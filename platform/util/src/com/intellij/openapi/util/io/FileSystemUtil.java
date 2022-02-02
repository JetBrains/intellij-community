// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.util.system.CpuArch;
import com.sun.jna.*;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributes;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class FileSystemUtil {
  static final String FORCE_USE_NIO2_KEY = "idea.io.use.nio2";

  private static final String COARSE_TIMESTAMP_KEY = "idea.io.coarse.ts";

  @ApiStatus.Internal
  public static final boolean DO_NOT_RESOLVE_SYMLINKS = Boolean.getBoolean("idea.symlinks.no.resolve");

  private static final Logger LOG = Logger.getInstance(FileSystemUtil.class);

  interface Mediator {
    @Nullable FileAttributes getAttributes(@NotNull String path) throws IOException;
    @Nullable String resolveSymLink(@NotNull String path) throws IOException;
    boolean clonePermissions(@NotNull String source, @NotNull String target, boolean execOnly) throws IOException;
  }

  private static final Mediator ourMediator = computeMediator();

  static @NotNull Mediator computeMediator() {
    if (!Boolean.getBoolean(FORCE_USE_NIO2_KEY)) {
      try {
        if (SystemInfo.isWindows && IdeaWin32.isAvailable()) {
          return check(new IdeaWin32MediatorImpl());
        }
        else if ((SystemInfo.isLinux || SystemInfo.isMac) && CpuArch.isIntel64() && JnaLoader.isLoaded() && JnaLoader.isSupportsDirectMapping()) {
          return check(new JnaUnixMediatorImpl());
        }
      }
      catch (Throwable t) {
        LOG.warn("Failed to load filesystem access layer: " + SystemInfo.OS_NAME + ", " + SystemInfo.JAVA_VERSION, t);
      }
    }

    return new Nio2MediatorImpl();
  }

  private static Mediator check(Mediator mediator) throws Exception {
    String quickTestPath = SystemInfo.isWindows ? "C:\\" : "/";
    mediator.getAttributes(quickTestPath);
    return mediator;
  }

  private FileSystemUtil() { }

  public static @Nullable FileAttributes getAttributes(@NotNull String path) {
    try {
      if (LOG.isTraceEnabled()) {
        long t = System.nanoTime();
        FileAttributes result = ourMediator.getAttributes(path);
        t = System.nanoTime() - t;
        LOG.trace("getAttributes(" + path + ") = " + result + " in " + TimeUnit.NANOSECONDS.toMicros(t) + " mks");
        return result;
      }
      else {
        return ourMediator.getAttributes(path);
      }
    }
    catch (Exception e) {
      LOG.warn("getAttributes(" + path + ")", e);
    }
    return null;
  }

  public static @Nullable FileAttributes getAttributes(@NotNull File file) {
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
    FileAttributes attributes = getAttributes(path);
    return attributes != null && attributes.isSymLink();
  }

  /**
   * Checks if a last element in the path is a symlink.
   */
  public static boolean isSymLink(@NotNull File file) {
    return isSymLink(file.getAbsolutePath());
  }

  public static @Nullable String resolveSymLink(@NotNull String path) {
    try {
      String realPath;
      if (LOG.isTraceEnabled()) {
        long t = System.nanoTime();
        realPath = ourMediator.resolveSymLink(path);
        t = System.nanoTime() - t;
        LOG.trace("resolveSymLink(" + path + ") = "+realPath+" in " + TimeUnit.NANOSECONDS.toMicros(t) + " mks");
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

  public static @Nullable String resolveSymLink(@NotNull File file) {
    return resolveSymLink(file.getAbsolutePath());
  }

  /**
   * Gives the second file permissions of the first one if possible; returns {@code true} on success; no-op on Windows.
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
   * Gives the second file permissions of the first one if possible; returns {@code true} on success; no-op on Windows.
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
    private static final class LibC {
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
    private static final class UnixLibC {
      static native int lstat(String path, Pointer stat);
      static native int stat(String path, Pointer stat);
    }

    @SuppressWarnings("SpellCheckingInspection")
    private static final class LinuxLibC {
      static native int __lxstat64(int ver, String path, Pointer stat);
      static native int __xstat64(int ver, String path, Pointer stat);
    }

    private static final int[] LINUX_64 =  {24, 48, 88, 28, 32};
    private static final int[] BSD_64 =    { 8, 72, 40, 12, 16};

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
      assert JnaLoader.isSupportsDirectMapping() : "Direct mapping not available on " + Platform.RESOURCE_PREFIX;

      if ("linux-x86-64".equals(Platform.RESOURCE_PREFIX)) myOffsets = LINUX_64;
      else if ("darwin-x86-64".equals(Platform.RESOURCE_PREFIX)) myOffsets = BSD_64;
      else throw new IllegalStateException("Unsupported OS/arch: " + Platform.RESOURCE_PREFIX);

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
        if (DO_NOT_RESOLVE_SYMLINKS) {
          isSymlink = false;
        }

        boolean isDirectory = (mode & LibC.S_IFMT) == LibC.S_IFDIR;
        boolean isSpecial = !isDirectory && (mode & LibC.S_IFMT) != LibC.S_IFREG;
        long size = buffer.getLong(myOffsets[OFF_SIZE]);
        long mTime1 = Native.LONG_SIZE == 4 ? buffer.getInt(myOffsets[OFF_TIME]) : buffer.getLong(myOffsets[OFF_TIME]);
        long mTime2 = myCoarseTs ? 0 : Native.LONG_SIZE == 4 ? buffer.getInt(myOffsets[OFF_TIME] + 4) : buffer.getLong(myOffsets[OFF_TIME] + 8);
        long mTime = mTime1 * 1000 + mTime2 / 1000000;

        boolean writable = ownFile(buffer) ? (mode & LibC.WRITE_MASK) != 0 : LibC.access(path, LibC.W_OK) == 0;

        return new FileAttributes(isDirectory, isSpecial, isSymlink, false, size, mTime, writable);
      }
      finally {
        myMemoryPool.recycle(buffer);
      }
    }

    @Override
    public String resolveSymLink(@NotNull String path) throws IOException {
      try {
        return DO_NOT_RESOLVE_SYMLINKS ? path : new File(path).getCanonicalPath();
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

    private static boolean loadFileStatus(@NotNull String path, @NotNull Memory buffer) {
      return (SystemInfo.isLinux ? LinuxLibC.__xstat64(STAT_VER, path, buffer) : UnixLibC.stat(path, buffer)) == 0;
    }

    private int getModeFlags(@NotNull Memory buffer) {
      return SystemInfo.isLinux ? buffer.getInt(myOffsets[OFF_MODE]) : buffer.getShort(myOffsets[OFF_MODE]);
    }

    private boolean ownFile(@NotNull Memory buffer) {
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
        boolean isSymbolicLink = attributes != null &&
                                 (attributes.isSymbolicLink() ||
                                  SystemInfo.isWindows && attributes.isOther() && attributes.isDirectory() && path.getParent() != null);
        if (isSymbolicLink) {
          try {
            attributes = Files.readAttributes(path, schema);
          }
          catch (NoSuchFileException e) {
            return FileAttributes.BROKEN_SYMLINK;
          }
        }
        if (attributes == null) {
          return null;
        }

        boolean isDirectory = attributes.isDirectory();
        boolean isOther = attributes.isOther();
        long size = attributes.size();
        long lastModified = attributes.lastModifiedTime().toMillis();
        boolean isHidden;
        boolean isWritable;
        if (SystemInfo.isWindows) {
          isHidden = path.getParent() != null && ((DosFileAttributes)attributes).isHidden();
          isWritable = isDirectory || !((DosFileAttributes)attributes).isReadOnly();
        }
        else {
          isHidden = false;
          try {
            isWritable = Files.isWritable(path);
          } catch (SecurityException e){
            isWritable = false;
          }
        }
        return new FileAttributes(isDirectory, isOther, isSymbolicLink, isHidden, size, lastModified, isWritable);
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

      Path sourcePath = Paths.get(source);
      Path targetPath = Paths.get(target);
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

  /**
   * Detects case-sensitivity of the directory containing {@code anyChild} (or {@code anyChild} itself, if it happens to be
   * a file system root) - first by calling platform-specific APIs if possible, then falling back to querying its attributes
   * via different names.
   */
  public static @NotNull FileAttributes.CaseSensitivity readParentCaseSensitivity(@NotNull File anyChild) {
    FileAttributes.CaseSensitivity detected = readCaseSensitivityByNativeAPI(anyChild);
    if (detected != FileAttributes.CaseSensitivity.UNKNOWN) return detected;

    // when native queries failed, fallback to the Java File IO:
    return readParentCaseSensitivityByJavaIO(anyChild);
  }

  static @NotNull FileAttributes.CaseSensitivity readParentCaseSensitivityByJavaIO(@NotNull File anyChild) {
    // try to query this path by different-case strings and deduce case sensitivity from the answers
    if (!anyChild.exists()) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("readParentCaseSensitivityByJavaIO(" + anyChild + "): does not exist");
      }
      return FileAttributes.CaseSensitivity.UNKNOWN;
    }

    File parent = anyChild.getParentFile();
    if (parent == null) {
      String probe = findCaseToggleableChild(anyChild);
      if (probe == null) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("readParentCaseSensitivityByJavaIO(" + anyChild + "): no toggleable child, parent=null isDirectory=" + anyChild.isDirectory());
        }
        return FileAttributes.CaseSensitivity.UNKNOWN;
      }
      parent = anyChild;
      anyChild = new File(parent, probe);
    }

    String name = anyChild.getName();
    String altName = toggleCase(name);
    if (altName.equals(name)) {
      // we have a bad case of "123" file
      name = findCaseToggleableChild(parent);
      if (name == null) {
        if (LOG.isDebugEnabled()) {
          String[] list = null;
          try {
            list = parent.list();
          }
          catch (Exception ignored) { }
          if (list == null) {
            LOG.debug("readParentCaseSensitivityByJavaIO(" + anyChild + "): parent.list() failed");
          }
          else {
            LOG.debug("readParentCaseSensitivityByJavaIO(" + anyChild + "): no toggleable child among " + list.length + " siblings");
            if (LOG.isTraceEnabled()) LOG.trace("readParentCaseSensitivityByJavaIO(" + anyChild + "): " + Arrays.toString(list));
          }
        }
        // we can't find any file with a case-toggleable name
        return FileAttributes.CaseSensitivity.UNKNOWN;
      }
      altName = toggleCase(name);
    }

    String altPath = parent.getPath() + '/' + altName;
    FileAttributes newAttributes = getAttributes(altPath);

    try {
      if (newAttributes == null) {
        // couldn't file this file by other-cased name, so deduce FS is sensitive
        return FileAttributes.CaseSensitivity.SENSITIVE;
      }
      // if changed-case file is found, there is a slim chance that the FS is still case-sensitive, but there are two files with different case
      File altCanonicalFile = new File(altPath).getCanonicalFile();
      String altCanonicalName = altCanonicalFile.getName();
      if (altCanonicalName.equals(name) || altCanonicalName.equals(anyChild.getCanonicalFile().getName())) {
        // nah, these two are really the same file
        return FileAttributes.CaseSensitivity.INSENSITIVE;
      }
    }
    catch (IOException e) {
      LOG.debug("readParentCaseSensitivityByJavaIO(" + anyChild + ")", e);
      return FileAttributes.CaseSensitivity.UNKNOWN;
    }

    // it's a different file indeed; tough luck
    return FileAttributes.CaseSensitivity.SENSITIVE;
  }

  static @NotNull FileAttributes.CaseSensitivity readCaseSensitivityByNativeAPI(@NotNull File anyChild) {
    FileAttributes.CaseSensitivity detected = FileAttributes.CaseSensitivity.UNKNOWN;
    if (JnaLoader.isLoaded()) {
      File parent = anyChild.getParentFile();
      String path = (parent != null ? parent : anyChild).getAbsolutePath();
      if (SystemInfo.isWin10OrNewer && OSAgnosticPathUtil.isAbsoluteDosPath(path)) {
        detected = getNtfsCaseSensitivity(path);
      }
      else if (SystemInfo.isMac) {
        detected = getMacOsCaseSensitivity(path);
      }
      else if (SystemInfo.isLinux) {
        detected = getLinuxCaseSensitivity(path);
      }
    }
    return detected;
  }

  private static String toggleCase(String name) {
    String altName = name.toUpperCase(Locale.getDefault());
    if (altName.equals(name)) altName = name.toLowerCase(Locale.getDefault());
    return altName;
  }

  /**
   * @return {@code true} when the {@code name} contains case-toggleable characters (for which toLowerCase() != toUpperCase()).
   * E.g. "Child.txt" is case-toggleable because "CHILD.TXT" != "child.txt", but "122.45" is not.
   */
  public static boolean isCaseToggleable(@NotNull String name) {
    return !toggleCase(name).equals(name);
  }

  // return child which name can be used for querying by different-case names (e.g "child.txt" vs "CHILD.TXT")
  // or null if there are none (e.g., there's only one child "123.456").
  private static @Nullable String findCaseToggleableChild(@NotNull File dir) {
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir.toPath())) {
      for (Path path : stream) {
        String name = path.getFileName().toString();
        if (!name.toLowerCase(Locale.getDefault()).equals(name.toUpperCase(Locale.getDefault()))) {
          return name;
        }
      }
    }
    catch (Exception ignored) { }
    return null;
  }

  //<editor-fold desc="Windows case sensitivity detection (NTFS-only)">
  private static FileAttributes.CaseSensitivity getNtfsCaseSensitivity(String path) {
    try {
      Kernel32 kernel32 = Kernel32.INSTANCE;
      NtOsKrnl ntOsKrnl = NtOsKrnl.INSTANCE;

      String name = "\\\\?\\" + path;
      WinNT.HANDLE handle = kernel32.CreateFile(name, 0, NtOsKrnl.FILE_SHARE_ALL, null, WinNT.OPEN_EXISTING, WinNT.FILE_FLAG_BACKUP_SEMANTICS, null);
      if (handle == WinBase.INVALID_HANDLE_VALUE) {
        if (LOG.isDebugEnabled()) LOG.debug("CreateFile(" + path + "): 0x" + Integer.toHexString(kernel32.GetLastError()));
        return FileAttributes.CaseSensitivity.UNKNOWN;
      }

      NtOsKrnl.FILE_CASE_SENSITIVE_INFORMATION_P fileInformation = new NtOsKrnl.FILE_CASE_SENSITIVE_INFORMATION_P();

      int result = ntOsKrnl.NtQueryInformationFile(
        handle,
        new NtOsKrnl.IO_STATUS_BLOCK_P(),
        fileInformation,
        fileInformation.size(),
        NtOsKrnl.FileCaseSensitiveInformation);

      kernel32.CloseHandle(handle);

      if (result != 0) {
        // https://docs.microsoft.com/en-us/openspecs/windows_protocols/ms-erref/596a1078-e883-4972-9bbc-49e60bebca55
        if (LOG.isDebugEnabled()) LOG.debug("NtQueryInformationFile(" + path + "): 0x" + Integer.toHexString(result));
      }
      else if (fileInformation.Flags == 0) {
        return FileAttributes.CaseSensitivity.INSENSITIVE;
      }
      else if (fileInformation.Flags == 1) {
        return FileAttributes.CaseSensitivity.SENSITIVE;
      }
      else {
        LOG.warn("NtQueryInformationFile(" + path + "): unexpected 'FileCaseSensitiveInformation' value " + fileInformation.Flags);
      }
    }
    catch (Throwable t) {
      LOG.warn("path: " + path, t);
    }

    return FileAttributes.CaseSensitivity.UNKNOWN;
  }

  private interface NtOsKrnl extends StdCallLibrary, WinNT {
    NtOsKrnl INSTANCE = Native.load("NtDll", NtOsKrnl.class, W32APIOptions.UNICODE_OPTIONS);

    int FILE_SHARE_ALL = FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE;

    @Structure.FieldOrder({"Pointer", "Information"})
    class IO_STATUS_BLOCK_P extends Structure implements Structure.ByReference {
      public Pointer Pointer;
      public Pointer Information;
    }

    @Structure.FieldOrder("Flags")
    class FILE_CASE_SENSITIVE_INFORMATION_P extends Structure implements Structure.ByReference {
      // initialize with something crazy to make sure the native call did write 0 or 1 to this field
      public long Flags = 0xFFFF_FFFFL;  // FILE_CS_FLAG_CASE_SENSITIVE_DIR = 1
    }

    int FileCaseSensitiveInformation = 71;

    int NtQueryInformationFile(
      HANDLE FileHandle,
      IO_STATUS_BLOCK_P ioStatusBlock,
      Structure fileInformation,
      long length,
      int fileInformationClass);
  }
  //</editor-fold>

  //<editor-fold desc="macOS case sensitivity detection">
  private static FileAttributes.CaseSensitivity getMacOsCaseSensitivity(String path) {
    try {
      CoreFoundation cf = CoreFoundation.INSTANCE;

      byte[] buffer = path.getBytes(StandardCharsets.UTF_8);
      CoreFoundation.CFTypeRef url = cf.CFURLCreateFromFileSystemRepresentation(null, buffer, buffer.length, true);
      try {
        PointerByReference resultPtr = new PointerByReference(), errorPtr = new PointerByReference();
        Pointer result;
        if (!cf.CFURLCopyResourcePropertyForKey(url, CoreFoundation.kCFURLVolumeSupportsCaseSensitiveNamesKey, resultPtr, errorPtr)) {
          Pointer error = errorPtr.getValue();
          String description = error != null ? cf.CFErrorGetDomain(error).stringValue() + '/' + cf.CFErrorGetCode(error) : "error";
          if (LOG.isDebugEnabled()) {
            LOG.debug("CFURLCopyResourcePropertyForKey(" + path + "): " + description);
          }
        }
        else if ((result = resultPtr.getValue()) == null) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("CFURLCopyResourcePropertyForKey(" + path + "): property not available");
          }
        }
        else {
          boolean value = new CoreFoundation.CFBooleanRef(result).booleanValue();
          return value ? FileAttributes.CaseSensitivity.SENSITIVE : FileAttributes.CaseSensitivity.INSENSITIVE;
        }
      }
      finally {
        url.release();
      }
    }
    catch (Throwable t) {
      LOG.warn("path: " + path, t);
    }

    return FileAttributes.CaseSensitivity.UNKNOWN;
  }

  private interface CoreFoundation extends com.sun.jna.platform.mac.CoreFoundation {
    CoreFoundation INSTANCE = Native.load("CoreFoundation", CoreFoundation.class);

    CFStringRef kCFURLVolumeSupportsCaseSensitiveNamesKey = CFStringRef.createCFString("NSURLVolumeSupportsCaseSensitiveNamesKey");

    CFTypeRef CFURLCreateFromFileSystemRepresentation(CFAllocatorRef allocator, byte[] buffer, long bufLen, boolean isDirectory);
    boolean CFURLCopyResourcePropertyForKey(CFTypeRef url, CFStringRef key, PointerByReference propertyValueTypeRefPtr, PointerByReference error);

    CFStringRef CFErrorGetDomain(Pointer errorRef);
    CFIndex CFErrorGetCode(Pointer errorRef);
  }
  //</editor-fold>

  //<editor-fold desc="Linux case sensitivity detection">
  private static FileAttributes.CaseSensitivity getLinuxCaseSensitivity(String path) {
    try {
      Memory buf = new Memory(256);
      if (LibC.INSTANCE.statfs(path, buf) != 0) {
        if (LOG.isDebugEnabled()) LOG.debug("statfs(" + path + "): error");
      }
      else {
        long fs = Native.LONG_SIZE == 4 ? buf.getInt(0) : buf.getLong(0);
        // Btrfs, XFS
        if (fs == 0x9123683e || fs == 0x58465342) {
          return FileAttributes.CaseSensitivity.SENSITIVE;
        }
        // VFAT
        if (fs == 0x4d44) {
          return FileAttributes.CaseSensitivity.INSENSITIVE;
        }
        // Ext*, F2FS
        if ((fs == 0xef53 || fs == 0xf2f52010) && ourLibExt2FsPresent) {
          LongByReference flags = new LongByReference();
          if (E2P.INSTANCE.fgetflags(path, flags) != 0) {
            if (LOG.isDebugEnabled()) LOG.debug("fgetflags(" + path + "): error");
          }
          else {
            // Ext4/F2FS inodes on file systems with "casefold" option enable may have EXT4_CASEFOLD_FL (F2FS_CASEFOLD_FL) attribute
            // see e.g. https://git.kernel.org/pub/scm/linux/kernel/git/torvalds/linux.git/commit/?id=b886ee3e778ec2ad43e276fd378ab492cf6819b7
            return (flags.getValue() & E2P.EXT4_CASEFOLD_FL) == 0 ? FileAttributes.CaseSensitivity.SENSITIVE : FileAttributes.CaseSensitivity.INSENSITIVE;
          }
        }
      }
    }
    catch (UnsatisfiedLinkError e) {
      ourLibExt2FsPresent = false;
      LOG.warn(e);
    }
    catch (Throwable t) {
      LOG.warn("path: " + path, t);
    }

    return FileAttributes.CaseSensitivity.UNKNOWN;
  }

  private interface LibC extends Library {
    LibC INSTANCE = Native.load(LibC.class);

    int statfs(String path, Memory buf);
  }

  private static volatile boolean ourLibExt2FsPresent = true;

  interface E2P extends Library {
    E2P INSTANCE = Native.load("e2p", E2P.class);

    long EXT4_CASEFOLD_FL = 0x4000_0000L;

    int fgetflags(String path, LongByReference flags);
  }
  //</editor-fold>
}
