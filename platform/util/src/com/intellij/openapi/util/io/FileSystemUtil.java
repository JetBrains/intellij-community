/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * @version 11.1
 */
public class FileSystemUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.io.FileSystemUtil");

  @NotNull
  private static final Mediator ourMediator;
  static {
    Mediator mediator = null;

    // todo: move IdeaWin32 interface to this package, add mediator

    if (SystemInfo.isLinux || SystemInfo.isMac || SystemInfo.isSolaris || SystemInfo.isFreeBSD) {
      try {
        mediator = new JnaUnixMediatorImpl();
        mediator.getAttributes("/");  // quick test
      }
      catch (Throwable t) {
        LOG.error(t);
        mediator = null;
      }
    }

    if (mediator == null && SystemInfo.isJavaVersionAtLeast("1.7") && !"1.7.0-ea".equals(SystemInfo.JAVA_VERSION)) {
      try {
        mediator = new Jdk7MediatorImpl();
        mediator.getAttributes("/");  // quick test
      }
      catch (Throwable t) {
        LOG.error(t);
        mediator = null;
      }
    }

    if (mediator == null) {
      // todo: after introducing IdeaWin32 mediator, fail tests at this point, or issue a warning in production
      mediator = new StandardMediatorImpl();
    }

    ourMediator = mediator;
  }

  private FileSystemUtil() { }

  @Nullable
  public static FileAttributes getAttributes(@NotNull final String path) {
    try {
      return ourMediator.getAttributes(path);
    }
    catch (Exception e) {
      LOG.warn(e);
    }
    return null;
  }

  public static boolean isSymLink(@NotNull final String path) {
    final FileAttributes attributes = getAttributes(path);
    return attributes != null && attributes.isSymlink;
  }

  public static boolean isSymLink(@NotNull final File file) {
    return isSymLink(file.getAbsolutePath());
  }

  @Nullable
  public static String resolveSymLink(@NotNull final String path) {
    try {
      final String realPath = ourMediator.resolveSymLink(path);
      if (realPath != null && new File(realPath).exists()) {
        return realPath;
      }
    }
    catch (Exception e) {
      LOG.warn(e);
    }
    return null;
  }

  @Nullable
  public static String resolveSymLink(@NotNull final File file) {
    return resolveSymLink(file.getAbsolutePath());
  }

  public static int getPermissions(@NotNull final String path) {
    final FileAttributes attributes = getAttributes(path);
    return attributes != null ? attributes.permissions : -1;
  }

  public static int getPermissions(@NotNull final File file) {
    return getPermissions(file.getAbsolutePath());
  }

  public static void setPermissions(@NotNull final String path, final int permissions) {
    if (SystemInfo.isUnix) {
      try {
        ourMediator.setPermissions(path, permissions);
      }
      catch (Exception e) {
        LOG.warn(e);
      }
    }
  }

  public static void setPermissions(@NotNull final File file, final int permissions) {
    setPermissions(file.getAbsolutePath(), permissions);
  }

  private interface Mediator {
    @Nullable
    FileAttributes getAttributes(@NotNull String path) throws Exception;

    @Nullable
    String resolveSymLink(@NotNull String path) throws Exception;

    void setPermissions(@NotNull String path, int permissions) throws Exception;
  }

  // todo[r.sh] remove reflection after migration to JDK 7
  @SuppressWarnings("OctalInteger")
  private static class Jdk7MediatorImpl implements Mediator {
    private final Object myDefaultFileSystem;
    private final Method myGetPath;
    private final Method myIsSymbolicLink;
    private final Object myLinkOptions;
    private final Object myNoFollowLinkOptions;
    private final Method myReadAttributes;
    private final Method mySetAttribute;
    private final Method myToMillis;

    private Jdk7MediatorImpl() throws Exception {
      myDefaultFileSystem = Class.forName("java.nio.file.FileSystems").getMethod("getDefault").invoke(null);

      myGetPath = Class.forName("java.nio.file.FileSystem").getMethod("getPath", String.class, String[].class);
      myGetPath.setAccessible(true);

      final Class<?> pathClass = Class.forName("java.nio.file.Path");
      final Class<?> filesClass = Class.forName("java.nio.file.Files");
      myIsSymbolicLink = filesClass.getMethod("isSymbolicLink", pathClass);
      myIsSymbolicLink.setAccessible(true);

      final Class<?> linkOptClass = Class.forName("java.nio.file.LinkOption");
      myLinkOptions = Array.newInstance(linkOptClass, 0);
      myNoFollowLinkOptions = Array.newInstance(linkOptClass, 1);
      Array.set(myNoFollowLinkOptions, 0, linkOptClass.getField("NOFOLLOW_LINKS").get(null));

      final Class<?> linkOptArrClass = myLinkOptions.getClass();
      myReadAttributes = filesClass.getMethod("readAttributes", pathClass, String.class, linkOptArrClass);
      myReadAttributes.setAccessible(true);
      mySetAttribute = filesClass.getMethod("setAttribute", pathClass, String.class, Object.class, linkOptArrClass);
      mySetAttribute.setAccessible(true);

      final Class<?> fileTimeClass = Class.forName("java.nio.file.attribute.FileTime");
      myToMillis = fileTimeClass.getMethod("toMillis");
      myToMillis.setAccessible(true);
    }

    @Override
    public FileAttributes getAttributes(@NotNull final String path) throws Exception {
      final Object pathObj = myGetPath.invoke(myDefaultFileSystem, path, ArrayUtil.EMPTY_STRING_ARRAY);
      try {
        if (SystemInfo.isWindows) {
          final Map attributes = (Map)myReadAttributes.invoke(null, pathObj, "dos:*", myNoFollowLinkOptions);
          return new FileAttributes((Boolean)attributes.get("isDirectory"),
                                    (Boolean)attributes.get("isSymbolicLink"),
                                    (Boolean)attributes.get("isOther"),
                                    (Long)attributes.get("size"),
                                    (Long)myToMillis.invoke(attributes.get("lastModifiedTime")),
                                    !(Boolean)attributes.get("readonly"));
        }
        else {
          final Map attributes = (Map)myReadAttributes.invoke(null, pathObj, "posix:*", myNoFollowLinkOptions);
          return new FileAttributes((Boolean)attributes.get("isDirectory"),
                                    (Boolean)attributes.get("isSymbolicLink"),
                                    (Boolean)attributes.get("isOther"),
                                    (Long)attributes.get("size"),
                                    (Long)myToMillis.invoke(attributes.get("lastModifiedTime")),
                                    decodePermissions(attributes.get("permissions")));
        }
      }
      catch (InvocationTargetException e) {
        final Throwable cause = e.getCause();
        if (cause != null && "java.nio.file.NoSuchFileException".equals(cause.getClass().getName())) {
          return null;
        }
        throw e;
      }
    }

    @Override
    public String resolveSymLink(@NotNull final String path) throws Exception {
      if (!new File(path).exists()) return null;
      final Object pathObj = myGetPath.invoke(myDefaultFileSystem, path, ArrayUtil.EMPTY_STRING_ARRAY);
      final Method toRealPath = pathObj.getClass().getMethod("toRealPath", myLinkOptions.getClass());
      toRealPath.setAccessible(true);
      return toRealPath.invoke(pathObj, myLinkOptions).toString();
    }

    @Override
    public void setPermissions(@NotNull final String path, final int permissions) throws Exception {
      final Object pathObj = myGetPath.invoke(myDefaultFileSystem, path, ArrayUtil.EMPTY_STRING_ARRAY);
      final Object attribute = encodePermissions(permissions);
      if (attribute != null) {
        mySetAttribute.invoke(null, pathObj, "posix:permissions", attribute, myLinkOptions);
      }
    }

    private static final Map<String, Integer> ATTRIBUTES_MAP;
    static {
      ATTRIBUTES_MAP = new HashMap<String, Integer>();
      ATTRIBUTES_MAP.put("OWNER_READ", FileAttributes.OWNER_READ);
      ATTRIBUTES_MAP.put("OWNER_WRITE", FileAttributes.OWNER_WRITE);
      ATTRIBUTES_MAP.put("OWNER_EXECUTE", FileAttributes.OWNER_EXECUTE);
      ATTRIBUTES_MAP.put("GROUP_READ", FileAttributes.GROUP_READ);
      ATTRIBUTES_MAP.put("GROUP_WRITE", FileAttributes.GROUP_WRITE);
      ATTRIBUTES_MAP.put("GROUP_EXECUTE", FileAttributes.GROUP_EXECUTE);
      ATTRIBUTES_MAP.put("OTHERS_READ", FileAttributes.OTHERS_READ);
      ATTRIBUTES_MAP.put("OTHERS_WRITE", FileAttributes.OTHERS_WRITE);
      ATTRIBUTES_MAP.put("OTHERS_EXECUTE", FileAttributes.OTHERS_EXECUTE);
    }

    @FileAttributes.Permissions
    private static int decodePermissions(final Object o) {
      if (!(o instanceof Collection)) return -1;

      int value = 0;
      for (Object attr : (Collection)o) {
        final Integer bit = ATTRIBUTES_MAP.get(attr.toString());
        if (bit != null) value |= bit;
      }
      //noinspection MagicConstant
      return value;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static Object encodePermissions(final int value) {
      try {
        final Class aClass = Class.forName("java.nio.file.attribute.PosixFilePermission");
        final Set values = new HashSet();
        for (Map.Entry<String, Integer> entry : ATTRIBUTES_MAP.entrySet()) {
          if ((value & entry.getValue()) > 0) {
            final String name = entry.getKey();
            values.add(Enum.valueOf(aClass, name));
          }
        }
        return values;
      }
      catch (Exception ignore) { }

      return null;
    }
  }

  // thanks to SVNKit for the idea
  private static class JnaUnixMediatorImpl implements Mediator {
    @SuppressWarnings("OctalInteger")
    private interface LibC extends Library {
      // from stat(2)
      int S_MASK = 0177777;
      int S_IFLNK = 0120000;  // symbolic link
      int S_IFREG = 0100000;  // regular file
      int S_IFDIR = 0040000;  // directory
      int PERM_MASK = 0777;

      int lstat(String path, Pointer stat);
      int __lxstat64(int ver, String path, Pointer stat);
      int chmod(String path, int mode);
    }

    private final LibC myLibC;
    private final Memory mySharedMem;
    private final int myModeOffset;
    private final int mySizeOffset;
    private final int myTimeOffset;

    private JnaUnixMediatorImpl() throws Exception {
      myLibC = (LibC)Native.loadLibrary("c", LibC.class);
      mySharedMem = new Memory(512);
      myModeOffset = SystemInfo.isLinux ? (SystemInfo.is32Bit ? 16 : 24) :
                     SystemInfo.isMac | SystemInfo.isFreeBSD ? 8 :
                     SystemInfo.isSolaris ? (SystemInfo.is32Bit ? 20 : 16) :
                     -1;
      mySizeOffset = SystemInfo.isLinux ? (SystemInfo.is32Bit ? 44 : 48) :
                     SystemInfo.isMac | SystemInfo.isFreeBSD ? (SystemInfo.is32Bit ? 48 : 72) :
                     SystemInfo.isSolaris ? (SystemInfo.is32Bit ? 48 : 40) :
                     -1;
      myTimeOffset = SystemInfo.isLinux ? (SystemInfo.is32Bit ? 72 : 88) :
                     SystemInfo.isMac | SystemInfo.isFreeBSD ? 40 :
                     SystemInfo.isSolaris ? 64 :
                     -1;
      if (myModeOffset < 0) throw new IllegalStateException("Unsupported OS: " + SystemInfo.OS_NAME);
    }

    @Override
    public synchronized FileAttributes getAttributes(@NotNull final String path) throws Exception {
      mySharedMem.clear();
      final int res = SystemInfo.isLinux ? myLibC.__lxstat64(0, path, mySharedMem) : myLibC.lstat(path, mySharedMem);
      if (res == 0) {
        final int mode = (SystemInfo.isLinux ? mySharedMem.getInt(myModeOffset) : mySharedMem.getShort(myModeOffset)) & LibC.S_MASK;
        final boolean isDirectory = (mode & LibC.S_IFDIR) == LibC.S_IFDIR;
        final boolean isSymlink = (mode & LibC.S_IFLNK) == LibC.S_IFLNK;
        final boolean isSpecial = !isDirectory && !isSymlink && (mode & LibC.S_IFREG) == 0;
        final long size = mySharedMem.getLong(mySizeOffset);
        final long mTime1 = SystemInfo.is32Bit ? mySharedMem.getInt(myTimeOffset) : mySharedMem.getLong(myTimeOffset);
        final long mTime2 = SystemInfo.is32Bit ? mySharedMem.getInt(myTimeOffset + 4) : mySharedMem.getLong(myTimeOffset + 8);
        final long mTime = mTime1 * 1000 + mTime2 / 1000000;
        @FileAttributes.Permissions final int permissions = mode & LibC.PERM_MASK;
        return new FileAttributes(isDirectory, isSymlink, isSpecial, size, mTime, permissions);
      }
      return null;
    }

    @Override
    public String resolveSymLink(@NotNull final String path) throws Exception {
      return new File(path).getCanonicalPath();
    }

    @Override
    public void setPermissions(@NotNull final String path, final int permissions) throws Exception {
      myLibC.chmod(path, permissions & LibC.PERM_MASK);
    }
  }

  private static class StandardMediatorImpl implements Mediator {
    // from java.io.FileSystem
    private static final int BA_REGULAR   = 0x02;
    private static final int BA_DIRECTORY = 0x04;

    private final Object myFileSystem;
    private final Method myGetBooleanAttributes;

    private StandardMediatorImpl() {
      Object fileSystem;
      Method getBooleanAttributes;
      try {
        final Class<?> fsClass = Class.forName("java.io.FileSystem");
        final Method getFileSystem = fsClass.getMethod("getFileSystem");
        getFileSystem.setAccessible(true);
        fileSystem = getFileSystem.invoke(null);
        getBooleanAttributes = fsClass.getDeclaredMethod("getBooleanAttributes", File.class);
        getBooleanAttributes.setAccessible(true);
      }
      catch (Throwable t) {
        fileSystem = null;
        getBooleanAttributes = null;
      }
      myFileSystem = fileSystem;
      myGetBooleanAttributes = getBooleanAttributes;
    }

    @Override
    public FileAttributes getAttributes(@NotNull final String path) throws Exception {
      final File file = new File(path);
      if (myFileSystem != null) {
        final int flags = (Integer)myGetBooleanAttributes.invoke(myFileSystem, file);
        if (flags != 0) {
          final boolean isDirectory = (flags & BA_DIRECTORY) != 0;
          final boolean isSpecial = (flags & (BA_REGULAR | BA_DIRECTORY)) == 0;
          return new FileAttributes(isDirectory, false, isSpecial, file.length(), file.lastModified(), file.canWrite());
        }
      }
      else {
        if (file.exists()) {
          final boolean isDirectory = file.isDirectory();
          final boolean isSpecial = !isDirectory && !file.isFile();
          return new FileAttributes(isDirectory, false, isSpecial, file.length(), file.lastModified(), file.canWrite());
        }
      }

      return null;
    }

    @Override
    public String resolveSymLink(@NotNull final String path) throws Exception {
      return new File(path).getCanonicalPath();
    }

    @Override
    public void setPermissions(@NotNull final String path, final int permissions) throws Exception {
    }
  }
}
