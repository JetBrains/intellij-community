// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util.io;

import com.intellij.jna.JnaLoader;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.win32.FileInfo;
import com.intellij.openapi.util.io.win32.IdeaWin32;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.LimitedPool;
import com.sun.jna.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributes;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * @version 11.1
 */
public class FileSystemUtil {
  static final String FORCE_USE_NIO2_KEY = "idea.io.use.nio2";
  private static final String COARSE_TIMESTAMP_KEY = "idea.io.coarse.ts";

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.io.FileSystemUtil");

  private interface Mediator {
    @Nullable FileAttributes getAttributes(@NotNull String path) throws IOException;
    @Nullable String resolveSymLink(@NotNull String path) throws IOException;
    boolean clonePermissions(@NotNull String source, @NotNull String target, boolean execOnly) throws IOException;
  }

  @NotNull
  private static Mediator ourMediator = getMediator();

  private static Mediator getMediator() {
    if (!Boolean.getBoolean(FORCE_USE_NIO2_KEY)) {
      try {
        if (SystemInfo.isWindows && IdeaWin32.isAvailable()) {
          return check(new IdeaWin32MediatorImpl());
        }
        else if ((SystemInfo.isLinux || SystemInfo.isMac || SystemInfo.isSolaris || SystemInfo.isFreeBSD) && JnaLoader.isLoaded()) {
          return check(new JnaUnixMediatorImpl());
        }
      }
      catch (Throwable t) {
        LOG.warn("Failed to load filesystem access layer: " + SystemInfo.OS_NAME + ", " + SystemInfo.JAVA_VERSION, t);
      }
    }

    return new Nio2MediatorImpl();
  }

  private static Mediator check(final Mediator mediator) throws Exception {
    String quickTestPath = SystemInfo.isWindows ? "C:\\" : "/";
    mediator.getAttributes(quickTestPath);
    return mediator;
  }

  private FileSystemUtil() { }

  @Nullable
  public static FileAttributes getAttributes(@NotNull String path) {
    try {
      if (LOG.isTraceEnabled()) {
        LOG.trace("getAttributes(" + path + ")");
        long t = System.nanoTime();
        FileAttributes result = ourMediator.getAttributes(path);
        t = (System.nanoTime() - t) / 1000;
        LOG.trace("  " + t + " mks");
        return result;
      }
      else {
        return ourMediator.getAttributes(path);
      }
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
      FileAttributes attributes = getAttributes(path);
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
      String realPath;
      if (LOG.isTraceEnabled()) {
        LOG.trace("resolveSymLink(" + path + ")");
        long t = System.nanoTime();
        realPath = ourMediator.resolveSymLink(path);
        t = (System.nanoTime() - t) / 1000;
        LOG.trace("  " + t + " mks");
      }
      else {
        realPath = ourMediator.resolveSymLink(path);
      }
      if (realPath != null && (SystemInfo.isWindows && realPath.startsWith("\\\\") || new File(realPath).exists())) {
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

  private static class IdeaWin32MediatorImpl implements Mediator {
    private final IdeaWin32 myInstance = IdeaWin32.getInstance();

    @Override
    public FileAttributes getAttributes(@NotNull String path) {
      FileInfo fileInfo = myInstance.getInfo(path);
      return fileInfo != null ? fileInfo.toFileAttributes() : null;
    }

    @Override
    public String resolveSymLink(@NotNull String path) {
      path = new File(path).getAbsolutePath();

      char drive = Character.toUpperCase(path.charAt(0));
      if (!(path.length() > 3 && drive >= 'A' && drive <= 'Z' && path.charAt(1) == ':' && path.charAt(2) == '\\')) {
        return path;  // unknown format
      }

      int remainder = 4;
      while (remainder < path.length()) {
        int next = path.indexOf('\\', remainder);
        String subPath = next > 0 ? path.substring(0, next) : path;
        FileAttributes attributes = getAttributes(subPath);
        if (attributes == null) {
          return null;
        }
        if (attributes.isSymLink()) {
          return myInstance.resolveSymLink(path);
        }

        remainder = next > 0 ? next + 1 : path.length();
      }

      return path;
    }

    @Override
    public boolean clonePermissions(@NotNull String source, @NotNull String target, boolean execOnly) {
      return false;
    }
  }

  // thanks to SVNKit for the idea of platform-specific offsets
  private static class JnaUnixMediatorImpl implements Mediator {
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
    private static final int[] BSD_32_12 = {24, 96, 64, 28, 32};
    private static final int[] BSD_64_12 = {24,112, 64, 28, 32};
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
    private final LimitedPool<Memory> myMemoryPool = new LimitedPool.Sync<>(10, () -> new Memory(256));

    JnaUnixMediatorImpl() {
      if ("linux-x86".equals(Platform.RESOURCE_PREFIX)) myOffsets = LINUX_32;
      else if ("linux-x86-64".equals(Platform.RESOURCE_PREFIX)) myOffsets = LINUX_64;
      else if ("linux-arm".equals(Platform.RESOURCE_PREFIX)) myOffsets = LNX_ARM32;
      else if ("linux-ppc".equals(Platform.RESOURCE_PREFIX)) myOffsets = LNX_PPC32;
      else if ("linux-ppc64le".equals(Platform.RESOURCE_PREFIX)) myOffsets = LNX_PPC64;
      else if ("darwin".equals(Platform.RESOURCE_PREFIX)) myOffsets = BSD_64;
      else if ("freebsd-x86".equals(Platform.RESOURCE_PREFIX)) myOffsets = SystemInfo.isOsVersionAtLeast("12") ? BSD_32_12 : BSD_32;
      else if ("freebsd-x86-64".equals(Platform.RESOURCE_PREFIX)) myOffsets = SystemInfo.isOsVersionAtLeast("12") ? BSD_64_12 : BSD_64;
      else if ("sunos-x86".equals(Platform.RESOURCE_PREFIX)) myOffsets = SUN_OS_32;
      else if ("sunos-x86-64".equals(Platform.RESOURCE_PREFIX)) myOffsets = SUN_OS_64;
      else throw new IllegalStateException("Unsupported OS/arch: " + SystemInfo.OS_NAME + "/" + SystemInfo.OS_ARCH);

      Map<String, String> options = Collections.singletonMap(Library.OPTION_STRING_ENCODING, CharsetToolkit.getPlatformCharset().name());
      NativeLibrary lib = NativeLibrary.getInstance("c", options);
      Native.register(LibC.class, lib);
      Native.register(SystemInfo.isLinux ? LinuxLibC.class : UnixLibC.class, lib);

      myUid = LibC.getuid();
      myGid = LibC.getgid();
    }

    @Override
    public FileAttributes getAttributes(@NotNull String path) {
      Memory buffer = myMemoryPool.alloc();
      try {
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
      finally {
        myMemoryPool.recycle(buffer);
      }
    }

    @Override
    public String resolveSymLink(@NotNull final String path) throws IOException {
      try {
        return new File(path).getCanonicalPath();
      }
      catch (IOException e) {
        String message = e.getMessage();
        if (message != null && StringUtil.toLowerCase(message).contains("too many levels of symbolic links")) {
          LOG.debug(e);
          return null;
        }
        throw new IOException("Cannot resolve '" + path + "'", e);
      }
    }

    @Override
    public boolean clonePermissions(@NotNull String source, @NotNull String target, boolean execOnly) {
      Memory buffer = new Memory(256);
      if (!loadFileStatus(source, buffer)) return false;

      int permissions;
      int sourcePermissions = getModeFlags(buffer) & LibC.PERM_MASK;
      if (execOnly) {
        if (!loadFileStatus(target, buffer)) return false;
        int targetPermissions = getModeFlags(buffer) & LibC.PERM_MASK;
        permissions = targetPermissions & ~LibC.EXECUTE_MASK | sourcePermissions & LibC.EXECUTE_MASK;
      }
      else {
        permissions = sourcePermissions;
      }
      return LibC.chmod(target, permissions) == 0;
    }

    private static boolean loadFileStatus(String path, Memory buffer) {
      return (SystemInfo.isLinux ? LinuxLibC.__xstat64(STAT_VER, path, buffer) : UnixLibC.stat(path, buffer)) == 0;
    }

    private int getModeFlags(Memory buffer) {
      return SystemInfo.isLinux ? buffer.getInt(myOffsets[OFF_MODE]) : buffer.getShort(myOffsets[OFF_MODE]);
    }

    private boolean ownFile(Memory buffer) {
      return buffer.getInt(myOffsets[OFF_UID]) == myUid && buffer.getInt(myOffsets[OFF_GID]) == myGid;
    }
  }

  private static class Nio2MediatorImpl implements Mediator {
    private final LinkOption[] myNoFollowLinkOptions = {LinkOption.NOFOLLOW_LINKS};
    private final PosixFilePermission[] myExecPermissions =
      {PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.OTHERS_EXECUTE};

    @Override
    public FileAttributes getAttributes(@NotNull String pathStr) {
      try {
        Path path = Paths.get(pathStr);

        Class<? extends BasicFileAttributes> schema = SystemInfo.isWindows ? DosFileAttributes.class : PosixFileAttributes.class;
        BasicFileAttributes attributes = Files.readAttributes(path, schema, myNoFollowLinkOptions);
        boolean isSymbolicLink =
          attributes.isSymbolicLink() || SystemInfo.isWindows && attributes.isOther() && attributes.isDirectory() && path.getParent() != null;
        if (isSymbolicLink) {
          try {
            attributes = Files.readAttributes(path, schema);
          }
          catch (NoSuchFileException e) {
            return FileAttributes.BROKEN_SYMLINK;
          }
        }

        boolean isDirectory = attributes.isDirectory();
        boolean isOther = attributes.isOther();
        long size = attributes.size();
        long lastModified = attributes.lastModifiedTime().toMillis();
        if (SystemInfo.isWindows) {
          boolean isHidden = path.getParent() != null && ((DosFileAttributes)attributes).isHidden();
          boolean isWritable = isDirectory || !((DosFileAttributes)attributes).isReadOnly();
          return new FileAttributes(isDirectory, isOther, isSymbolicLink, isHidden, size, lastModified, isWritable);
        }
        else {
          boolean isWritable = Files.isWritable(path);
          return new FileAttributes(isDirectory, isOther, isSymbolicLink, false, size, lastModified, isWritable);
        }
      }
      catch (IOException | InvalidPathException e) {
        LOG.debug(pathStr, e);
        return null;
      }
    }

    @Override
    public String resolveSymLink(@NotNull String path) throws IOException {
      try {
        return Paths.get(path).toRealPath().toString();
      }
      catch (NoSuchFileException e) {
        return null;
      }
    }

    @Override
    public boolean clonePermissions(@NotNull String source, @NotNull String target, boolean execOnly) throws IOException {
      if (!SystemInfo.isUnix) return false;

      Path sourcePath = Paths.get(source), targetPath = Paths.get(target);
      Set<PosixFilePermission> sourcePermissions = Files.readAttributes(sourcePath, PosixFileAttributes.class).permissions();
      Set<PosixFilePermission> targetPermissions = Files.readAttributes(targetPath, PosixFileAttributes.class).permissions();
      Set<PosixFilePermission> newPermissions;
      if (execOnly) {
        newPermissions = EnumSet.copyOf(targetPermissions);
        for (PosixFilePermission permission : myExecPermissions) {
          if (sourcePermissions.contains(permission)) {
            newPermissions.add(permission);
          }
          else {
            newPermissions.remove(permission);
          }
        }
      }
      else {
        newPermissions = sourcePermissions;
      }
      Files.setAttribute(targetPath, "posix:permissions", newPermissions);
      return true;
    }
  }

  @TestOnly
  static void resetMediator() {
    ourMediator = getMediator();
  }

  @TestOnly
  static String getMediatorName() {
    return ourMediator.getClass().getSimpleName().replace("MediatorImpl", "");
  }
}