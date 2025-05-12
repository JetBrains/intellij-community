// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.io;

import com.intellij.jna.JnaLoader;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
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
import org.jetbrains.annotations.VisibleForTesting;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Locale;

@SuppressWarnings({"IO_FILE_USAGE", "UnnecessaryFullyQualifiedName"})
public final class FileSystemUtil {
  private static final Logger LOG = Logger.getInstance(FileSystemUtil.class);

  private FileSystemUtil() { }

  /** Please use NIO API instead ({@link Files}, etc.) */
  @ApiStatus.Obsolete
  public static @Nullable FileAttributes getAttributes(@NotNull String path) {
    if (SystemInfo.isWindows && path.length() == 2 && path.charAt(1) == ':') {
      LOG.error("Incomplete Windows path: " + path);
      path += '\\';
    }
    try {
      return getAttributes(Paths.get(path));
    }
    catch (InvalidPathException e) {
      LOG.debug(e);
      return null;
    }
  }

  /** Please use NIO API instead ({@link Files}, etc.) */
  @ApiStatus.Obsolete
  public static @Nullable FileAttributes getAttributes(@NotNull java.io.File file) {
    return getAttributes(file.toPath());
  }

  /** Please use NIO API instead ({@link Files}, etc.) */
  @ApiStatus.Obsolete
  public static long lastModified(@NotNull java.io.File file) {
    FileAttributes attributes = getAttributes(file);
    return attributes != null ? attributes.lastModified : 0;
  }

  /** Please use NIO API instead ({@link Files}, etc.) */
  @ApiStatus.Obsolete
  public static boolean isSymLink(@NotNull String path) {
    FileAttributes attributes = getAttributes(path);
    return attributes != null && attributes.isSymLink();
  }

  /** Please use NIO API instead ({@link Files}, etc.) */
  @ApiStatus.Obsolete
  public static boolean isSymLink(@NotNull java.io.File file) {
    return isSymLink(file.getAbsolutePath());
  }

  /** Please use NIO API instead ({@link Files}, etc.) */
  @ApiStatus.Obsolete
  public static @Nullable String resolveSymLink(@NotNull String path) {
    try {
      return resolveSymLink(Paths.get(path));
    }
    catch (InvalidPathException e) {
      LOG.debug(e);
      return null;
    }
  }

  /** Please use NIO API instead ({@link Files}, etc.) */
  @ApiStatus.Obsolete
  public static @Nullable String resolveSymLink(@NotNull java.io.File file) {
    return resolveSymLink(file.toPath());
  }

  private static @Nullable FileAttributes getAttributes(Path path) {
    try {
      BasicFileAttributes attributes = NioFiles.readAttributes(path);
      return attributes == NioFiles.BROKEN_SYMLINK ? com.intellij.openapi.util.io.FileAttributes.BROKEN_SYMLINK : com.intellij.openapi.util.io.FileAttributes.fromNio(path, attributes);
    }
    catch (NoSuchFileException e) {
      LOG.trace(e.getClass().getName() + ": " + path);
      return null;
    }
    catch (IOException e) {
      LOG.debug(path.toString(), e);
      return null;
    }
  }

  private static @Nullable String resolveSymLink(Path path) {
    try {
      return path.toRealPath().toString();
    }
    catch (NoSuchFileException e) {
      LOG.trace(e.getClass().getName() + ": " + path);
    }
    catch (FileSystemException e) {
      LOG.debug(path.toString(), e);
    }
    catch (IOException e) {
      LOG.warn(e);
    }
    return null;
  }

  /**
   * Detects case-sensitivity of the directory containing {@code anyChild} (or {@code anyChild} itself, if it happens to be
   * a filesystem root) - first by calling platform-specific APIs if possible, then falling back to querying its attributes
   * via different names.
   */
  @ApiStatus.Internal
  public static @NotNull FileAttributes.CaseSensitivity readParentCaseSensitivity(@NotNull java.io.File anyChild) {
    FileAttributes.CaseSensitivity detected = readCaseSensitivityByNativeAPI(anyChild);
    if (detected != com.intellij.openapi.util.io.FileAttributes.CaseSensitivity.UNKNOWN) return detected;
    // native queries failed, fallback to the Java I/O:
    return readParentCaseSensitivityByJavaIO(anyChild);
  }

  @VisibleForTesting
  @ApiStatus.Internal
  public static @NotNull FileAttributes.CaseSensitivity readParentCaseSensitivityByJavaIO(@NotNull java.io.File anyChild) {
    // try to query this path by different-case strings and deduce case sensitivity from the answers
    if (!anyChild.exists()) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("readParentCaseSensitivityByJavaIO(" + anyChild + "): does not exist");
      }
      return FileAttributes.CaseSensitivity.UNKNOWN;
    }

    java.io.File parent = anyChild.getParentFile();
    if (parent == null) {
      String probe = findCaseToggleableChild(anyChild);
      if (probe == null) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("readParentCaseSensitivityByJavaIO(" + anyChild + "): no toggleable child, parent=null isDirectory=" + anyChild.isDirectory());
        }
        return FileAttributes.CaseSensitivity.UNKNOWN;
      }
      parent = anyChild;
      anyChild = new java.io.File(parent, probe);
    }

    String name = anyChild.getName();
    String altName = toggleCase(name);
    if (altName.equals(name)) {
      // we have a bad case of non-alphabetic file name
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
        // couldn't find this file by other-cased name, so deduce FS is sensitive
        return FileAttributes.CaseSensitivity.SENSITIVE;
      }
      // if a changed-case file is found, there is a slim chance that the FS is still case-sensitive,
      // but there are two files with a different case
      java.io.File altCanonicalFile = new java.io.File(altPath).getCanonicalFile();
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

  @VisibleForTesting
  @ApiStatus.Internal
  public static @NotNull FileAttributes.CaseSensitivity readCaseSensitivityByNativeAPI(@NotNull java.io.File anyChild) {
    FileAttributes.CaseSensitivity detected = com.intellij.openapi.util.io.FileAttributes.CaseSensitivity.UNKNOWN;
    if (JnaLoader.isLoaded()) {
      java.io.File parent = anyChild.getParentFile();
      String path = (parent != null ? parent : anyChild).getAbsolutePath();
      if (SystemInfo.isWin10OrNewer && WINDOWS_CS_API_AVAILABLE) {
        detected = OSAgnosticPathUtil.isAbsoluteDosPath(path) ? getNtfsCaseSensitivity(path) : com.intellij.openapi.util.io.FileAttributes.CaseSensitivity.UNKNOWN;
      }
      else if (SystemInfo.isMac && MAC_CS_API_AVAILABLE) {
        detected = getMacOsCaseSensitivity(path);
      }
      else if (SystemInfo.isLinux && LINUX_CS_API_AVAILABLE) {
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
  @ApiStatus.Internal
  public static boolean isCaseToggleable(@NotNull String name) {
    return !toggleCase(name).equals(name);
  }

  // returns a child whose name can be used for querying by different-case names (e.g. "child.txt" vs. "CHILD.TXT")
  // or `null` if there are none (e.g., there's only one child "123.456")
  private static @Nullable String findCaseToggleableChild(java.io.File dir) {
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
  private static volatile boolean WINDOWS_CS_API_AVAILABLE = true;

  private static FileAttributes.CaseSensitivity getNtfsCaseSensitivity(String path) {
    Kernel32 kernel32;
    NtOsKrnl ntOsKrnl;
    try {
      kernel32 = Kernel32.INSTANCE;
      ntOsKrnl = NtOsKrnl.INSTANCE;
    }
    catch (Throwable t) {
      LOG.warn(t);
      WINDOWS_CS_API_AVAILABLE = false;
      return com.intellij.openapi.util.io.FileAttributes.CaseSensitivity.UNKNOWN;
    }
    try {
      String name = "\\\\?\\" + path;
      WinNT.HANDLE handle = kernel32.CreateFile(name, 0, NtOsKrnl.FILE_SHARE_ALL, null, WinNT.OPEN_EXISTING, WinNT.FILE_FLAG_BACKUP_SEMANTICS, null);
      if (handle == WinBase.INVALID_HANDLE_VALUE) {
        if (LOG.isDebugEnabled()) LOG.debug("CreateFile(" + path + "): 0x" + Integer.toHexString(kernel32.GetLastError()));
        return com.intellij.openapi.util.io.FileAttributes.CaseSensitivity.UNKNOWN;
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
        return com.intellij.openapi.util.io.FileAttributes.CaseSensitivity.INSENSITIVE;
      }
      else if (fileInformation.Flags == 1) {
        return com.intellij.openapi.util.io.FileAttributes.CaseSensitivity.SENSITIVE;
      }
      else {
        LOG.warn("NtQueryInformationFile(" + path + "): unexpected 'FileCaseSensitiveInformation' value " + fileInformation.Flags);
      }
    }
    catch (Throwable t) {
      LOG.warn("path: " + path, t);
    }

    return com.intellij.openapi.util.io.FileAttributes.CaseSensitivity.UNKNOWN;
  }

  @SuppressWarnings("SpellCheckingInspection")
  private interface NtOsKrnl extends StdCallLibrary, WinNT {
    NtOsKrnl INSTANCE = Native.load("NtDll", NtOsKrnl.class, W32APIOptions.UNICODE_OPTIONS);

    int FILE_SHARE_ALL = FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE;

    @Structure.FieldOrder({"Pointer", "Information"})
    final
    class IO_STATUS_BLOCK_P extends Structure implements Structure.ByReference {
      public Pointer Pointer;
      public Pointer Information;
    }

    @Structure.FieldOrder("Flags")
    final
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
  private static volatile boolean MAC_CS_API_AVAILABLE = true;

  private static FileAttributes.CaseSensitivity getMacOsCaseSensitivity(String path) {
    CoreFoundation cf;
    try {
      cf = CoreFoundation.INSTANCE;
    }
    catch (Throwable t) {
      LOG.warn(t);
      MAC_CS_API_AVAILABLE = false;
      return com.intellij.openapi.util.io.FileAttributes.CaseSensitivity.UNKNOWN;
    }
    try {
      byte[] buffer = path.getBytes(StandardCharsets.UTF_8);
      CoreFoundation.CFTypeRef url = cf.CFURLCreateFromFileSystemRepresentation(null, buffer, buffer.length, true);
      try {
        PointerByReference resultPtr = new PointerByReference(), errorPtr = new PointerByReference();
        Pointer result;
        if (!cf.CFURLCopyResourcePropertyForKey(url, CoreFoundation.kCFURLVolumeSupportsCaseSensitiveNamesKey, resultPtr, errorPtr)) {
          if (LOG.isDebugEnabled()) {
            Pointer error = errorPtr.getValue();
            String description = error != null ? cf.CFErrorGetDomain(error).stringValue() + '/' + cf.CFErrorGetCode(error) : "error";
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
          return value ? com.intellij.openapi.util.io.FileAttributes.CaseSensitivity.SENSITIVE : com.intellij.openapi.util.io.FileAttributes.CaseSensitivity.INSENSITIVE;
        }
      }
      finally {
        url.release();
      }
    }
    catch (Throwable t) {
      LOG.warn("path: " + path, t);
    }

    return com.intellij.openapi.util.io.FileAttributes.CaseSensitivity.UNKNOWN;
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
  private static volatile boolean LINUX_CS_API_AVAILABLE = true;

  private static FileAttributes.CaseSensitivity getLinuxCaseSensitivity(String path) {
    LibC libC;
    try {
      libC = LibC.INSTANCE;
    }
    catch (Throwable t) {
      LOG.warn(t);
      LINUX_CS_API_AVAILABLE = false;
      return com.intellij.openapi.util.io.FileAttributes.CaseSensitivity.UNKNOWN;
    }
    try {
      Memory buf = new Memory(256);
      if (libC.statfs(path, buf) != 0) {
        if (LOG.isDebugEnabled()) LOG.debug("statfs(" + path + "): error");
      }
      else {
        long fs = Native.LONG_SIZE == 4 ? ((long)buf.getInt(0)) & 0xFFFFFFFFL : buf.getLong(0);
        // Btrfs, XFS
        if (fs == 0x9123683EL || fs == 0x58465342L) {
          return com.intellij.openapi.util.io.FileAttributes.CaseSensitivity.SENSITIVE;
        }
        // VFAT
        if (fs == 0x4D44L) {
          return com.intellij.openapi.util.io.FileAttributes.CaseSensitivity.INSENSITIVE;
        }
        // Ext*, F2FS
        if ((fs == 0xEF53L || fs == 0xF2F52010L) && ourLibExt2FsPresent) {
          E2P e2P;
          try {
            e2P = E2P.INSTANCE;
          }
          catch (Throwable t) {
            LOG.warn(t);
            ourLibExt2FsPresent = false;
            return com.intellij.openapi.util.io.FileAttributes.CaseSensitivity.UNKNOWN;
          }
          LongByReference flags = new LongByReference();
          if (e2P.fgetflags(path, flags) != 0) {
            if (LOG.isDebugEnabled()) LOG.debug("fgetflags(" + path + "): error");
          }
          else {
            // Ext4/F2FS inodes on file systems with the "casefold" option enable may have EXT4_CASEFOLD_FL (F2FS_CASEFOLD_FL) attribute
            // (like https://git.kernel.org/pub/scm/linux/kernel/git/torvalds/linux.git/commit/?id=b886ee3e778ec2ad43e276fd378ab492cf6819b7)
            return (flags.getValue() & E2P.EXT4_CASEFOLD_FL) == 0 ? com.intellij.openapi.util.io.FileAttributes.CaseSensitivity.SENSITIVE : com.intellij.openapi.util.io.FileAttributes.CaseSensitivity.INSENSITIVE;
          }
        }
      }
    }
    catch (Throwable t) {
      LOG.warn("path: " + path, t);
    }

    return com.intellij.openapi.util.io.FileAttributes.CaseSensitivity.UNKNOWN;
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
