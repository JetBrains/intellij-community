/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.Patches;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.win32.FileInfo;
import com.intellij.openapi.util.io.win32.IdeaWin32;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Locale;

import static com.intellij.util.BitUtil.isSet;

/**
 * @version 11.1
 */
public class FileSystemUtil {
  static final String FORCE_USE_NIO2_KEY = "idea.io.use.nio2";
  static final String FORCE_USE_FALLBACK_KEY = "idea.io.use.fallback";
  static final String COARSE_TIMESTAMP_KEY = "idea.io.coarse.ts";

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.io.FileSystemUtil");

  private abstract static class Mediator {
    @Nullable
    protected abstract FileAttributes getAttributes(@NotNull String path) throws Exception;

    @Nullable
    protected abstract String resolveSymLink(@NotNull String path) throws Exception;

    protected boolean clonePermissions(@NotNull String source, @NotNull String target, boolean onlyPermissionsToExecute) throws Exception { return false; }

    @NotNull
    private String getName() { return getClass().getSimpleName().replace("MediatorImpl", ""); }
  }

  @NotNull
  private static Mediator ourMediator = getMediator();

  private static Mediator getMediator() {
    boolean forceNio2 = SystemProperties.getBooleanProperty(FORCE_USE_NIO2_KEY, false);
    boolean forceFallback = SystemProperties.getBooleanProperty(FORCE_USE_FALLBACK_KEY, false);
    Throwable error = null;

    if (!forceNio2 && !forceFallback) {
      if (SystemInfo.isWindows && IdeaWin32.isAvailable()) {
        try {
          return check(new IdeaWin32MediatorImpl());
        }
        catch (Throwable t) {
          error = t;
        }
      }
      else if (SystemInfo.isLinux || SystemInfo.isMac || SystemInfo.isSolaris || SystemInfo.isFreeBSD) {
        try {
          return check(new JnaUnixMediatorImpl());
        }
        catch (Throwable t) {
          error = t;
        }
      }
    }

    if (!forceFallback && SystemInfo.isJavaVersionAtLeast("1.7") && !"1.7.0-ea".equals(SystemInfo.JAVA_VERSION)) {
      try {
        return check(new Nio2MediatorImpl());
      }
      catch (Throwable t) {
        error = t;
      }
    }

    if (!forceFallback) {
      LOG.warn("Failed to load filesystem access layer: " + SystemInfo.OS_NAME + ", " + SystemInfo.JAVA_VERSION + ", " + "nio2=" + forceNio2, error);
    }

    return new FallbackMediatorImpl();
  }

  private static Mediator check(final Mediator mediator) throws Exception {
    final String quickTestPath = SystemInfo.isWindows ? "C:\\" :  "/";
    mediator.getAttributes(quickTestPath);
    return mediator;
  }

  private FileSystemUtil() { }

  @Nullable
  public static FileAttributes getAttributes(@NotNull String path) {
    try {
      return ourMediator.getAttributes(path);
    }
    catch (Exception e) {
      LOG.warn(e);
    }
    return null;
  }

  @Nullable
  public static FileAttributes getAttributes(@NotNull File file) {
    return getAttributes(file.getPath());
  }

  public static long lastModified(@NotNull File file) {
    FileAttributes attributes = getAttributes(file);
    return attributes != null ? attributes.lastModified : 0;
  }

  /**
   * Checks if a last element in the path is a symlink.
   */
  public static boolean isSymLink(@NotNull String path) {
    if (SystemInfo.areSymLinksSupported) {
      final FileAttributes attributes = getAttributes(path);
      return attributes != null && attributes.isSymLink();
    }
    return false;
  }

  /**
   * Checks if a last element in the path is a symlink.
   */
  public static boolean isSymLink(@NotNull File file) {
    return isSymLink(file.getAbsolutePath());
  }

  @Nullable
  public static String resolveSymLink(@NotNull String path) {
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
  public static String resolveSymLink(@NotNull File file) {
    return resolveSymLink(file.getAbsolutePath());
  }

  /**
   * Gives the second file permissions of the first one if possible; returns true if succeed.
   * Will do nothing on Windows.
   */
  public static boolean clonePermissions(@NotNull String source, @NotNull String target) {
    try {
      return ourMediator.clonePermissions(source, target, false);
    }
    catch (Exception e) {
      LOG.warn(e);
      return false;
    }
  }

  /**
   * Gives the second file permissions to execute of the first one if possible; returns true if succeed.
   * Will do nothing on Windows.
   */
  public static boolean clonePermissionsToExecute(@NotNull String source, @NotNull String target) {
    try {
      return ourMediator.clonePermissions(source, target, true);
    }
    catch (Exception e) {
      LOG.warn(e);
      return false;
    }
  }


  private static class Nio2MediatorImpl extends Mediator {
    private final Method myGetPath;
    private final Object myLinkOptions;
    private final Object myNoFollowLinkOptions;
    private final Method myReadAttributes;
    private final Method mySetAttribute;
    private final Method myToRealPath;
    private final Method myToMillis;
    private final Class<?> mySchema;
    private final Method myIsSymbolicLink;
    private final Method myIsDirectory;
    private final Method myIsOther;
    private final Method mySize;
    private final Method myLastModifiedTime;
    private final Method myIsHidden;
    private final Method myIsReadOnly;
    private final Method myPermissions;

    private Nio2MediatorImpl() throws Exception {
      assert Patches.USE_REFLECTION_TO_ACCESS_JDK7;

      myGetPath = accessible(Class.forName("java.nio.file.Paths").getMethod("get", String.class, String[].class));
      Class<?> pathClass = Class.forName("java.nio.file.Path");
      Class<?> filesClass = Class.forName("java.nio.file.Files");
      Class<?> linkOptClass = Class.forName("java.nio.file.LinkOption");
      myLinkOptions = Array.newInstance(linkOptClass, 0);
      myNoFollowLinkOptions = Array.newInstance(linkOptClass, 1);
      Array.set(myNoFollowLinkOptions, 0, linkOptClass.getField("NOFOLLOW_LINKS").get(null));
      Class<?> linkOptArrayClass = myLinkOptions.getClass();
      myReadAttributes = accessible(filesClass.getMethod("readAttributes", pathClass, Class.class, linkOptArrayClass));
      mySetAttribute = accessible(filesClass.getMethod("setAttribute", pathClass, String.class, Object.class, linkOptArrayClass));
      myToRealPath = accessible(pathClass.getMethod("toRealPath", linkOptArrayClass));
      myToMillis = accessible(Class.forName("java.nio.file.attribute.FileTime").getMethod("toMillis"));

      mySchema = Class.forName("java.nio.file.attribute." + (SystemInfo.isWindows ? "DosFileAttributes" : "PosixFileAttributes"));
      myIsSymbolicLink = accessible(mySchema.getMethod("isSymbolicLink"));
      myIsDirectory = accessible(mySchema.getMethod("isDirectory"));
      myIsOther = accessible(mySchema.getMethod("isOther"));
      mySize = accessible(mySchema.getMethod("size"));
      myLastModifiedTime = accessible(mySchema.getMethod("lastModifiedTime"));
      if (SystemInfo.isWindows) {
        myIsHidden = accessible(mySchema.getMethod("isHidden"));
        myIsReadOnly = accessible(mySchema.getMethod("isReadOnly"));
        myPermissions = null;
      }
      else {
        myIsHidden = myIsReadOnly = null;
        myPermissions = accessible(mySchema.getMethod("permissions"));
      }
    }

    private static Method accessible(Method method) {
      method.setAccessible(true);
      return method;
    }

    @Override
    protected FileAttributes getAttributes(@NotNull String path) throws Exception {
      try {
        Object pathObj = myGetPath.invoke(null, path, ArrayUtil.EMPTY_STRING_ARRAY);

        Object attributes = myReadAttributes.invoke(null, pathObj, mySchema, myNoFollowLinkOptions);
        boolean isSymbolicLink = (Boolean)myIsSymbolicLink.invoke(attributes) ||
                                 SystemInfo.isWindows && (Boolean)myIsOther.invoke(attributes) && (Boolean)myIsDirectory.invoke(attributes);
        if (isSymbolicLink) {
          try {
            attributes = myReadAttributes.invoke(null, pathObj, mySchema, myLinkOptions);
          }
          catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause != null && "java.nio.file.NoSuchFileException".equals(cause.getClass().getName())) {
              return FileAttributes.BROKEN_SYMLINK;
            }
          }
        }

        boolean isDirectory = (Boolean)myIsDirectory.invoke(attributes);
        boolean isOther = (Boolean)myIsOther.invoke(attributes);
        long size = (Long)mySize.invoke(attributes);
        long lastModified = (Long)myToMillis.invoke(myLastModifiedTime.invoke(attributes));
        if (SystemInfo.isWindows) {
          boolean isHidden = new File(path).getParent() == null ? false : (Boolean)myIsHidden.invoke(attributes);
          boolean isWritable = isDirectory || !(Boolean)myIsReadOnly.invoke(attributes);
          return new FileAttributes(isDirectory, isOther, isSymbolicLink, isHidden, size, lastModified, isWritable);
        }
        else {
          boolean isWritable = new File(path).canWrite();
          return new FileAttributes(isDirectory, isOther, isSymbolicLink, false, size, lastModified, isWritable);
        }
      }
      catch (InvocationTargetException e) {
        Throwable cause = e.getCause();
        if (cause instanceof IOException || cause != null && "java.nio.file.InvalidPathException".equals(cause.getClass().getName())) {
          LOG.debug(cause);
          return null;
        }
        throw e;
      }
    }

    @Override
    protected String resolveSymLink(@NotNull String path) throws Exception {
      Object pathObj = myGetPath.invoke(null, path, ArrayUtil.EMPTY_STRING_ARRAY);
      try {
        return myToRealPath.invoke(pathObj, myLinkOptions).toString();
      }
      catch (InvocationTargetException e) {
        Throwable cause = e.getCause();
        if (cause != null && "java.nio.file.NoSuchFileException".equals(cause.getClass().getName())) return null;
        throw e;
      }
    }

    @Override
    protected boolean clonePermissions(@NotNull String source, @NotNull String target, boolean onlyPermissionsToExecute) throws Exception {
      if (SystemInfo.isUnix) {
        Object sourcePath = myGetPath.invoke(null, source, ArrayUtil.EMPTY_STRING_ARRAY);
        Object targetPath = myGetPath.invoke(null, target, ArrayUtil.EMPTY_STRING_ARRAY);
        Collection sourcePermissions = getPermissions(sourcePath);
        Collection targetPermissions = getPermissions(targetPath);
        if (sourcePermissions != null && targetPermissions != null) {
          if (onlyPermissionsToExecute) {
            Collection<Object> permissionsToSet = ContainerUtil.newHashSet();
            for (Object permission : targetPermissions) {
              if (!permission.toString().endsWith("_EXECUTE")) {
                permissionsToSet.add(permission);
              }
            }
            for (Object permission : sourcePermissions) {
              if (permission.toString().endsWith("_EXECUTE")) {
                permissionsToSet.add(permission);
              }
            }
            mySetAttribute.invoke(null, targetPath, "posix:permissions", permissionsToSet, myLinkOptions);
          }
          else {
            mySetAttribute.invoke(null, targetPath, "posix:permissions", sourcePermissions, myLinkOptions);
          }
          return true;
        }
      }

      return false;
    }

    private Collection getPermissions(Object sourcePath) throws IllegalAccessException, InvocationTargetException {
      Object attributes = myReadAttributes.invoke(null, sourcePath, mySchema, myLinkOptions);
      return attributes != null ? (Collection)myPermissions.invoke(attributes) : null;
    }
  }


  private static class IdeaWin32MediatorImpl extends Mediator {
    private IdeaWin32 myInstance = IdeaWin32.getInstance();

    @Override
    protected FileAttributes getAttributes(@NotNull final String path) {
      final FileInfo fileInfo = myInstance.getInfo(path);
      return fileInfo != null ? fileInfo.toFileAttributes() : null;
    }

    @Override
    protected String resolveSymLink(@NotNull final String path) {
      return myInstance.resolveSymLink(path);
    }
  }


  // thanks to SVNKit for the idea of platform-specific offsets
  private static class JnaUnixMediatorImpl extends Mediator {
    @SuppressWarnings({"OctalInteger", "SpellCheckingInspection"})
    private static class LibC {
      static final int S_MASK = 0177777;
      static final int S_IFMT = 0170000;
      static final int S_IFLNK = 0120000;  // symbolic link
      static final int S_IFREG = 0100000;  // regular file
      static final int S_IFDIR = 0040000;  // directory
      static final int PERM_MASK = 0777;
      static final int EXECUTE_MASK = 0111;
      static final int WRITE_MASK = 0222;
      static final int W_OK = 2;           // write permission flag for access(2)

      static native int getuid();
      static native int getgid();
      static native int chmod(String path, int mode);
      static native int access(String path, int mode);
    }

    @SuppressWarnings("SpellCheckingInspection")
    private static class UnixLibC {
      static native int lstat(String path, Pointer stat);
      static native int stat(String path, Pointer stat);
    }

    @SuppressWarnings("SpellCheckingInspection")
    private static class LinuxLibC {
      static native int __lxstat64(int ver, String path, Pointer stat);
      static native int __xstat64(int ver, String path, Pointer stat);
    }

    private static final int[] LINUX_32 =  {16, 44, 72, 24, 28};
    private static final int[] LINUX_64 =  {24, 48, 88, 28, 32};
    private static final int[] LNX_PPC32 = {16, 48, 80, 24, 28};
    private static final int[] LNX_PPC64 = LINUX_64;
    private static final int[] LNX_ARM32 = LNX_PPC32;
    private static final int[] BSD_32 =    { 8, 48, 32, 12, 16};
    private static final int[] BSD_64 =    { 8, 72, 40, 12, 16};
    private static final int[] SUN_OS_32 = {20, 48, 64, 28, 32};
    private static final int[] SUN_OS_64 = {16, 40, 64, 24, 28};

    private static final int STAT_VER = 1;
    private static final int OFF_MODE = 0;
    private static final int OFF_SIZE = 1;
    private static final int OFF_TIME = 2;
    private static final int OFF_UID  = 3;
    private static final int OFF_GID  = 4;

    private final int[] myOffsets;
    private final int myUid;
    private final int myGid;
    private final boolean myCoarseTs = SystemProperties.getBooleanProperty(COARSE_TIMESTAMP_KEY, false);

    private JnaUnixMediatorImpl() {
           if ("linux-x86".equals(Platform.RESOURCE_PREFIX)) myOffsets = LINUX_32;
      else if ("linux-x86-64".equals(Platform.RESOURCE_PREFIX)) myOffsets = LINUX_64;
      else if ("linux-arm".equals(Platform.RESOURCE_PREFIX)) myOffsets = LNX_ARM32;
      else if ("linux-ppc".equals(Platform.RESOURCE_PREFIX)) myOffsets = LNX_PPC32;
      else if ("linux-ppc64le".equals(Platform.RESOURCE_PREFIX)) myOffsets = LNX_PPC64;
      else if ("freebsd-x86".equals(Platform.RESOURCE_PREFIX)) myOffsets = BSD_32;
      else if ("darwin".equals(Platform.RESOURCE_PREFIX) ||
               "freebsd-x86-64".equals(Platform.RESOURCE_PREFIX)) myOffsets = BSD_64;
      else if ("sunos-x86".equals(Platform.RESOURCE_PREFIX)) myOffsets = SUN_OS_32;
      else if ("sunos-x86-64".equals(Platform.RESOURCE_PREFIX)) myOffsets = SUN_OS_64;
      else throw new IllegalStateException("Unsupported OS/arch: " + SystemInfo.OS_NAME + "/" + SystemInfo.OS_ARCH);

      Native.register(LibC.class, "c");
      Native.register(SystemInfo.isLinux ? LinuxLibC.class : UnixLibC.class, "c");

      myUid = LibC.getuid();
      myGid = LibC.getgid();
    }

    @Override
    protected FileAttributes getAttributes(@NotNull String path) {
      Memory buffer = new Memory(256);
      int res = SystemInfo.isLinux ? LinuxLibC.__lxstat64(STAT_VER, path, buffer) : UnixLibC.lstat(path, buffer);
      if (res != 0) return null;

      int mode = getModeFlags(buffer) & LibC.S_MASK;
      boolean isSymlink = (mode & LibC.S_IFMT) == LibC.S_IFLNK;
      if (isSymlink) {
        if (!loadFileStatus(path, buffer)) {
          return FileAttributes.BROKEN_SYMLINK;
        }
        mode = getModeFlags(buffer) & LibC.S_MASK;
      }

      boolean isDirectory = (mode & LibC.S_IFMT) == LibC.S_IFDIR;
      boolean isSpecial = !isDirectory && (mode & LibC.S_IFMT) != LibC.S_IFREG;
      long size = buffer.getLong(myOffsets[OFF_SIZE]);
      long mTime1 = SystemInfo.is32Bit ? buffer.getInt(myOffsets[OFF_TIME]) : buffer.getLong(myOffsets[OFF_TIME]);
      long mTime2 = myCoarseTs ? 0 : SystemInfo.is32Bit ? buffer.getInt(myOffsets[OFF_TIME] + 4) : buffer.getLong(myOffsets[OFF_TIME] + 8);
      long mTime = mTime1 * 1000 + mTime2 / 1000000;

      boolean writable = ownFile(buffer) ? (mode & LibC.WRITE_MASK) != 0 : LibC.access(path, LibC.W_OK) == 0;

      return new FileAttributes(isDirectory, isSpecial, isSymlink, false, size, mTime, writable);
    }

    private static boolean loadFileStatus(String path, Memory buffer) {
      return (SystemInfo.isLinux ? LinuxLibC.__xstat64(STAT_VER, path, buffer) : UnixLibC.stat(path, buffer)) == 0;
    }

    @Override
    protected String resolveSymLink(@NotNull final String path) throws Exception {
      try {
        return new File(path).getCanonicalPath();
      }
      catch (IOException e) {
        String message = e.getMessage();
        if (message != null && message.toLowerCase(Locale.US).contains("too many levels of symbolic links")) {
          LOG.debug(e);
          return null;
        }
        throw new IOException("Cannot resolve '" + path + "'", e);
      }
    }

    @Override
    protected boolean clonePermissions(@NotNull String source, @NotNull String target, boolean onlyPermissionsToExecute) {
      Memory buffer = new Memory(256);
      if (!loadFileStatus(source, buffer)) return false;

      int permissions;
      int sourcePermissions = getModeFlags(buffer) & LibC.PERM_MASK;
      if (onlyPermissionsToExecute) {
        if (!loadFileStatus(target, buffer)) return false;
        int targetPermissions = getModeFlags(buffer) & LibC.PERM_MASK;
        permissions = targetPermissions & ~LibC.EXECUTE_MASK | sourcePermissions & LibC.EXECUTE_MASK;
      }
      else {
        permissions = sourcePermissions;
      }
      return LibC.chmod(target, permissions) == 0;
    }

    private int getModeFlags(Memory buffer) {
      return SystemInfo.isLinux ? buffer.getInt(myOffsets[OFF_MODE]) : buffer.getShort(myOffsets[OFF_MODE]);
    }

    private boolean ownFile(Memory buffer) {
      return buffer.getInt(myOffsets[OFF_UID]) == myUid && buffer.getInt(myOffsets[OFF_GID]) == myGid;
    }
  }


  private static class FallbackMediatorImpl extends Mediator {
    // from java.io.FileSystem
    private static final int BA_REGULAR   = 0x02;
    private static final int BA_DIRECTORY = 0x04;
    private static final int BA_HIDDEN    = 0x08;

    private final Object myFileSystem;
    private final Method myGetBooleanAttributes;

    private FallbackMediatorImpl() {
      Object fileSystem;
      Method getBooleanAttributes;
      try {
        Field fs = File.class.getDeclaredField("fs");
        fs.setAccessible(true);
        fileSystem = fs.get(null);
        getBooleanAttributes = fileSystem.getClass().getMethod("getBooleanAttributes", File.class);
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
    protected FileAttributes getAttributes(@NotNull final String path) throws Exception {
      final File file = new File(path);
      if (myFileSystem != null) {
        final int flags = (Integer)myGetBooleanAttributes.invoke(myFileSystem, file);
        if (flags != 0) {
          boolean isDirectory = isSet(flags, BA_DIRECTORY);
          boolean isSpecial = !isSet(flags, BA_REGULAR) && !isSet(flags, BA_DIRECTORY);
          boolean isHidden = isSet(flags, BA_HIDDEN) && !isWindowsRoot(path);
          boolean isWritable = SystemInfo.isWindows && isDirectory || file.canWrite();
          return new FileAttributes(isDirectory, isSpecial, false, isHidden, file.length(), file.lastModified(), isWritable);
        }
      }
      else if (file.exists()) {
        boolean isDirectory = file.isDirectory();
        boolean isSpecial = !isDirectory && !file.isFile();
        boolean isHidden = file.isHidden() && !isWindowsRoot(path);
        boolean isWritable = SystemInfo.isWindows && isDirectory || file.canWrite();
        return new FileAttributes(isDirectory, isSpecial, false, isHidden, file.length(), file.lastModified(), isWritable);
      }

      return null;
    }

    private static boolean isWindowsRoot(String p) {
      return SystemInfo.isWindows && p.length() >= 2 && p.length() <= 3 && Character.isLetter(p.charAt(0)) && p.charAt(1) == ':';
    }

    @Override
    protected String resolveSymLink(@NotNull final String path) throws Exception {
      return new File(path).getCanonicalPath();
    }

    @Override
    protected boolean clonePermissions(@NotNull String source, @NotNull String target, boolean onlyPermissionsToExecute) {
      if (SystemInfo.isUnix) {
        File srcFile = new File(source);
        File dstFile = new File(target);
        if (!onlyPermissionsToExecute) {
          if (!dstFile.setWritable(srcFile.canWrite(), true)) return false;
        }
        return dstFile.setExecutable(srcFile.canExecute(), true);
      }

      return false;
    }
  }

  @TestOnly
  static void resetMediator() {
    ourMediator = getMediator();
  }

  @TestOnly
  static String getMediatorName() {
    return ourMediator.getName();
  }
}