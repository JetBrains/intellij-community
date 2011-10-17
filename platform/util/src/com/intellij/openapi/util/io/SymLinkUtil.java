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
      }
    }
    ourMediator = mediator;
  }

  private SymLinkUtil() { }

  public static boolean isSymLink(@NotNull final File file) {
    try {
      return file.exists() && ourMediator != null && ourMediator.isSymLink(file.getAbsolutePath());
    }
    catch (Exception e) {
      LOG.warn(e);
      return false;
    }
  }

  public static boolean isSymLink(@NotNull final String path) {
    return isSymLink(new File(path));
  }

  @Nullable
  public static String resolveSymLink(@NotNull final File file) {
    if (file.exists() && ourMediator != null) {
      try {
        final String realPath = ourMediator.resolveSymLink(file.getAbsolutePath());
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

  @Nullable
  public static String resolveSymLink(@NotNull final String path) {
    return resolveSymLink(new File(path));
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
}
