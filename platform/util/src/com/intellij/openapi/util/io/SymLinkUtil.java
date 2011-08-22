/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ArrayUtil;
import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Method;

// todo[r.sh] use NIO2 API after migration to JDK 7
public class SymLinkUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.io.SymLinkUtil");

  @Nullable
  private static final Mediator ourMediator;

  static {
    Mediator mediator = null;
    if (SystemInfo.areSymLinksSupported) {
      if (SystemInfo.isJavaVersionAtLeast("1.7")) {
        try {
          mediator = new Jdk7MediatorImpl();
          mediator.isSymLink("/");  // quick test
        }
        catch (Throwable t) {
          LOG.error(t);
          mediator = null;
        }
      }
      if (mediator == null) {
        if (SystemInfo.isLinux || SystemInfo.isMac || SystemInfo.isSolaris) {
          try {
            mediator = new JnaUnixMediatorImpl();
            mediator.isSymLink("/");  // quick test
          }
          catch (Throwable t) {
            LOG.error(t);
            mediator = null;
          }
        }
        /*else if (SystemInfo.isWindows) {
          try {
            mediator = new JnaWindowsMediatorImpl();
            mediator.isSymLink("/");  // quick test
          }
          catch (Throwable t) {
            LOG.error(t);
            mediator = null;
          }
        }*/
      }
    }
    ourMediator = mediator;
  }

  private SymLinkUtil() { }

  public static boolean isSymLink(@NotNull final File file) {
    return isSymLink(file.getAbsolutePath());
  }

  public static boolean isSymLink(@NotNull final String path) {
    try {
      return ourMediator != null && ourMediator.isSymLink(path);
    }
    catch (Exception e) {
      LOG.warn(e);
      return false;
    }
  }

  @Nullable
  public static String resolveSymLink(@NotNull final File file) {
    return resolveSymLink(file.getAbsolutePath());
  }

  @Nullable
  public static String resolveSymLink(@NotNull final String path) {
    if (ourMediator != null) {
      try {
        final String realPath = ourMediator.resolveSymLink(path);
        if (realPath != null && new File(realPath).exists()) {
          return realPath;
        }
      }
      catch (Exception e) {
        LOG.warn(e);
      }
    }
    return null;
  }

  private interface Mediator {
    boolean isSymLink(@NotNull final String path) throws Exception;

    @Nullable
    String resolveSymLink(@NotNull final String path) throws Exception;
  }

  private static class Jdk7MediatorImpl implements Mediator {
    private final Object myDefaultFileSystem;
    private final Method myGetPath;
    private final Method myIsSymbolicLink;
    private final Object myLinkOptions;

    private Jdk7MediatorImpl() throws Exception {
      myDefaultFileSystem = Class.forName("java.nio.file.FileSystems").getMethod("getDefault").invoke(null);
      myGetPath = Class.forName("java.nio.file.FileSystem").getMethod("getPath", String.class, String[].class);
      myGetPath.setAccessible(true);
      myIsSymbolicLink = Class.forName("java.nio.file.Files").getMethod("isSymbolicLink", Class.forName("java.nio.file.Path"));
      myIsSymbolicLink.setAccessible(true);
      myLinkOptions = Array.newInstance(Class.forName("java.nio.file.LinkOption"), 0);
    }

    @Override
    public boolean isSymLink(@NotNull final String path) throws Exception {
      final Object pathObj = myGetPath.invoke(myDefaultFileSystem, path, ArrayUtil.EMPTY_STRING_ARRAY);
      return (Boolean)myIsSymbolicLink.invoke(null, pathObj);
    }

    @Override
    public String resolveSymLink(@NotNull final String path) throws Exception {
      final Object pathObj = myGetPath.invoke(myDefaultFileSystem, path, ArrayUtil.EMPTY_STRING_ARRAY);
      final Method toRealPath = pathObj.getClass().getMethod("toRealPath", myLinkOptions.getClass());
      toRealPath.setAccessible(true);
      return toRealPath.invoke(pathObj, myLinkOptions).toString();
    }
  }

  // thanks to SVNKit for the idea
  @SuppressWarnings("OctalInteger")
  private static class JnaUnixMediatorImpl implements Mediator {
    private interface LibC extends Library {
      int S_MASK = 0177777;
      int S_IFLNK = 0120000;

      int lstat(String path, Pointer stat);
      int __lxstat64(int ver, String path, Pointer stat);
    }

    private final LibC myLibC;
    private final Memory mySharedMem;
    private final int myOffset;

    private JnaUnixMediatorImpl() throws Exception {
      myLibC = (LibC)Native.loadLibrary("c", LibC.class);
      mySharedMem = new Memory(512);
      myOffset = SystemInfo.isLinux ? (SystemInfo.is32Bit ? 16 : 24) :
                 SystemInfo.isMac ? 8 :
                 SystemInfo.isSolaris ? (SystemInfo.is32Bit ? 20 : 16) :
                 -1;
      if (myOffset < 0) throw new IllegalStateException("Unsupported OS: " + SystemInfo.OS_NAME);
    }

    @Override
    public synchronized boolean isSymLink(@NotNull final String path) throws Exception {
      mySharedMem.clear();
      final int res = SystemInfo.isLinux ? myLibC.__lxstat64(0, path, mySharedMem) : myLibC.lstat(path, mySharedMem);
      if (res == 0) {
        final int mode = (SystemInfo.isLinux ? mySharedMem.getInt(myOffset) : mySharedMem.getShort(myOffset)) & LibC.S_MASK;
        return (mode & LibC.S_IFLNK) == LibC.S_IFLNK;
      }
      else {
        LOG.debug("lstat(" + path + "): " + res);
        return false;
      }
    }

    @Override
    public String resolveSymLink(@NotNull final String path) throws Exception {
      return new File(path).getCanonicalPath();
    }
  }

  /*private static class JnaWindowsMediatorImpl implements Mediator {
    private interface Kernel32 extends StdCallLibrary {
      int IO_REPARSE_TAG_SYMLINK = 0xA000000C;
      int FILE_ACCESS_FLAGS = 0x0080;
      int FILE_SHARE_FLAGS = 0x00000001 | 0x00000002 | 0x00000004;
      int OPEN_EXISTING = 3;
      int FILE_OPEN_FLAGS = 0x02000000 | 0x00200000;
      int FSCTL_GET_REPARSE_POINT = 0x000900A8;
      int SYMLINK_FLAG_RELATIVE = 0x00000001;

      @SuppressWarnings({"UnusedDeclaration", "MultipleVariablesInDeclaration"})
      class Win32FindData extends Structure implements Structure.ByReference {
        public int dwFileAttributes;
        public int ftCreationTimeL, ftCreationTimeH;
        public int ftLastAccessTimeL, ftLastAccessTimeH;
        public int ftLastWriteTimeL, ftLastWriteTimeH;
        public int lFileSizeH, lFileSizeL;
        public int dwReserved0;
        public int dwReserved1;
        public char[] cFileName = new char[260];
        public char[] cAlternateFileName = new char[14];
      }

      int MAX_SUPPORTED_TARGET_LENGTH = 4 * 1024;
      @SuppressWarnings({"UnusedDeclaration", "MultipleVariablesInDeclaration"})
      class ReparseDataBuffer extends Structure implements Structure.ByReference {
        public NativeLong ReparseTag;
        public short ReparseDataLength;
        public short Reserved;
        public short SubstituteNameOffset, SubstituteNameLength;
        public short PrintNameOffset, PrintNameLength;
        public NativeLong Flags;
        public char[] PathBuffer = new char[MAX_SUPPORTED_TARGET_LENGTH];
      }

      Pointer INVALID_HANDLE = Pointer.createConstant(-1);

      Pointer FindFirstFile(String lpFileName, Win32FindData lpFindFileData);

      boolean FindClose(Pointer hFindFile);

      Pointer CreateFile(String lpFileName,
                         int dwDesiredAccess,
                         int dwShareMode,
                         @Nullable Pointer lpSecurityAttributes,
                         int dwCreationDisposition,
                         int dwFlagsAndAttributes,
                         @Nullable Pointer hTemplateFile);

      boolean CloseHandle(Pointer hObject);

      boolean DeviceIoControl(Pointer hDevice,
                              int dwIoControlCode,
                              @Nullable Structure.ByReference lpInBuffer,
                              int nInBufferSize,
                              @Nullable Structure.ByReference lpOutBuffer,
                              int nOutBufferSize,
                              IntByReference lpBytesReturned,
                              @Nullable Pointer lpOverlapped);
    }

    private final Kernel32 myKernel32;
    private final Kernel32.Win32FindData myFindData;
    private final Kernel32.ReparseDataBuffer myReparseData;

    private JnaWindowsMediatorImpl() throws Exception {
      myKernel32 = (Kernel32) Native.loadLibrary("kernel32", Kernel32.class, W32APIOptions.UNICODE_OPTIONS);
      myFindData = new Kernel32.Win32FindData();
      myReparseData = new Kernel32.ReparseDataBuffer();
    }

    @SuppressWarnings("NonPrivateFieldAccessedInSynchronizedContext")
    @Override
    public synchronized boolean isSymLink(@NotNull final String path) throws Exception {
      synchronized (myFindData) {
        myFindData.dwReserved0 = 0;
        final Pointer handle = myKernel32.FindFirstFile(path, myFindData);
        if (Kernel32.INVALID_HANDLE.equals(handle)) {
          LOG.debug("FindFirstFile(" + path + "): " + handle);
          return false;
        }
        myKernel32.FindClose(handle);
        return (myFindData.dwReserved0 & Kernel32.IO_REPARSE_TAG_SYMLINK) == Kernel32.IO_REPARSE_TAG_SYMLINK;
      }
    }

    @SuppressWarnings("NonPrivateFieldAccessedInSynchronizedContext")
    @Override
    public String resolveSymLink(@NotNull final String path) throws Exception {
      final Pointer handle = myKernel32.CreateFile(path, Kernel32.FILE_ACCESS_FLAGS, Kernel32.FILE_SHARE_FLAGS, null,
                                                   Kernel32.OPEN_EXISTING, Kernel32.FILE_OPEN_FLAGS, null);
      if (Kernel32.INVALID_HANDLE.equals(handle)) {
        LOG.debug("CreateFile(" + path + "): " + handle);
        return null;
      }
      synchronized (myReparseData) {
        try {
          myReparseData.ReparseTag.setValue(0);
          myReparseData.SubstituteNameOffset = myReparseData.SubstituteNameLength = 0;
          myReparseData.Flags.setValue(0);
          final boolean result = myKernel32.DeviceIoControl(handle, Kernel32.FSCTL_GET_REPARSE_POINT, null, 0,
                                                            myReparseData, myReparseData.size(), new IntByReference(), null);
          if (!result || myReparseData.ReparseTag.intValue() != Kernel32.IO_REPARSE_TAG_SYMLINK) {
            LOG.debug("DeviceIoControl(" + path + "): " + result + "," + myReparseData.ReparseTag);
            return null;
          }
          String target = new String(myReparseData.PathBuffer, myReparseData.SubstituteNameOffset / 2, myReparseData.SubstituteNameLength / 2);
          if ((myReparseData.Flags.intValue() & Kernel32.SYMLINK_FLAG_RELATIVE) == Kernel32.SYMLINK_FLAG_RELATIVE) {
            return new File(new File(path).getParent(), target).getCanonicalPath();
          }
          else {
            if (target.startsWith("\\??\\") || target.startsWith("\\\\?\\")) {
              target = target.substring(4);
            }
            return new File(target).getCanonicalPath();
          }
        }
        finally {
          myKernel32.CloseHandle(handle);
        }
      }
    }
  }*/
}
