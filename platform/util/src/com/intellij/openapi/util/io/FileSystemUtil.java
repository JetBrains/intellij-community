// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.io;

import com.intellij.jna.JnaLoader;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class FileSystemUtil {
  private static final Logger LOG = Logger.getInstance(FileSystemUtil.class);

  static final String FORCE_USE_NIO2_KEY = "idea.io.use.nio2";

  private static final String COARSE_TIMESTAMP_KEY = "idea.io.coarse.ts";

  @ApiStatus.Internal
  public static final boolean DO_NOT_RESOLVE_SYMLINKS = Boolean.getBoolean("idea.symlinks.no.resolve");

  interface Mediator {
    @Nullable FileAttributes getAttributes(@NotNull String path) throws IOException;
    @Nullable String resolveSymLink(@NotNull String path) throws IOException;
  }

  private static final Mediator ourMediator = computeMediator();

  static @NotNull Mediator computeMediator() {
    boolean forceNio = Boolean.getBoolean(FORCE_USE_NIO2_KEY) ||
                       "com.intellij.platform.core.nio.fs".equals(FileSystems.getDefault().provider().getClass().getPackage().getName());
    if (!forceNio) {
      try {
        if ((SystemInfo.isLinux || SystemInfo.isMac) && CpuArch.isIntel64() && JnaLoader.isLoaded()) {
          return ensureSane(new JnaUnixMediatorImpl());
        }
      }
      catch (Throwable t) {
        LOG.warn("Failed to load filesystem access layer: " + SystemInfo.OS_NAME + ", " + SystemInfo.JAVA_VERSION, t);
      }
    }

    return new Nio2MediatorImpl();
  }

  private static Mediator ensureSane(@NotNull Mediator mediator) throws Exception {
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
   * Checks if the last element in the path is a symlink.
   */
  public static boolean isSymLink(@NotNull String path) {
    FileAttributes attributes = getAttributes(path);
    return attributes != null && attributes.isSymLink();
  }

  /**
   * Checks if the last element in the path is a symlink.
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
        LOG.trace("resolveSymLink(" + path + ") = " + realPath + " in " + TimeUnit.NANOSECONDS.toMicros(t) + " mks");
      }
      else {
        realPath = ourMediator.resolveSymLink(path);
      }
      if (realPath != null && (SystemInfo.isWindows && realPath.startsWith("\\\\") || Files.exists(Paths.get(realPath)))) {
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

  // thanks to SVNKit for the idea of platform-specific offsets
  private static final class JnaUnixMediatorImpl implements Mediator {
    @SuppressWarnings({"OctalInteger", "SpellCheckingInspection"})
    private static final class LibC {
      static final int S_MASK = 0177777;
      static final int S_IFMT = 0170000;
      static final int S_IFLNK = 0120000;  // symbolic link
      static final int S_IFREG = 0100000;  // regular file
      static final int S_IFDIR = 0040000;  // directory
      static final int S_IWUSR = 0200;
      static final int IW_MASK = 0222;     // write mask (a file might be writable iff all bits are 0)
      static final int W_OK = 2;           // write permission flag for access(2)

      static native int getuid();
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

    private static final int[] LINUX_64 =  {24, 48, 88, 28};
    private static final int[] DARWIN_64 = { 8, 72, 40, 12};
    private static final int STAT_VER = 1;
    private static final int OFF_MODE = 0;
    private static final int OFF_SIZE = 1;
    private static final int OFF_TIME = 2;
    private static final int OFF_UID  = 3;

    private final int[] myOffsets;
    private final int myUid;
    private final boolean myCoarseTs = SystemProperties.getBooleanProperty(COARSE_TIMESTAMP_KEY, false);
    private final LimitedPool<Memory> myMemoryPool = new LimitedPool.Sync<>(10, () -> new Memory(256));

    JnaUnixMediatorImpl() {
      assert JnaLoader.isSupportsDirectMapping() : "Direct mapping not available on " + Platform.RESOURCE_PREFIX;

      if ("linux-x86-64".equals(Platform.RESOURCE_PREFIX)) myOffsets = LINUX_64;
      else if ("darwin-x86-64".equals(Platform.RESOURCE_PREFIX)) myOffsets = DARWIN_64;
      else throw new IllegalStateException("Unsupported OS/arch: " + Platform.RESOURCE_PREFIX);

      Map<String, String> options = Collections.singletonMap(Library.OPTION_STRING_ENCODING, CharsetToolkit.getPlatformCharset().name());
      NativeLibrary lib = NativeLibrary.getInstance("c", options);
      Native.register(LibC.class, lib);
      Native.register(SystemInfo.isLinux ? LinuxLibC.class : UnixLibC.class, lib);

      myUid = LibC.getuid();
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

        boolean writable;
        if (isDirectory) {
          writable = true;
        }
        else if (buffer.getInt(myOffsets[OFF_UID]) == myUid) {
          writable = (mode & LibC.S_IWUSR) != 0;
        }
        else if ((mode & LibC.IW_MASK) == 0) {
          writable = false;
        }
        else {
          writable = LibC.access(path, LibC.W_OK) == 0;
        }

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

    private static boolean loadFileStatus(String path, Memory buffer) {
      return (SystemInfo.isLinux ? LinuxLibC.__xstat64(STAT_VER, path, buffer) : UnixLibC.stat(path, buffer)) == 0;
    }

    private int getModeFlags(Memory buffer) {
      return SystemInfo.isLinux ? buffer.getInt(myOffsets[OFF_MODE]) : buffer.getShort(myOffsets[OFF_MODE]);
    }
  }

  private static final class Nio2MediatorImpl implements Mediator {
    @Override
    public FileAttributes getAttributes(@NotNull String pathStr) {
      if (SystemInfo.isWindows && pathStr.length() == 2 && pathStr.charAt(1) == ':') {
        LOG.error("Incomplete Windows path: " + pathStr);
        pathStr += '\\';
      }
      try {
        Path path = Paths.get(pathStr);
        BasicFileAttributes attributes = NioFiles.readAttributes(path);
        return attributes == NioFiles.BROKEN_SYMLINK ? FileAttributes.BROKEN_SYMLINK : FileAttributes.fromNio(path, attributes);
      }
      catch (NoSuchFileException e) {
        LOG.trace(e.getClass().getName() + ": " + pathStr);
        return null;
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
        LOG.trace(e.getClass().getName() + ": " + path);
        return null;
      }
      catch (FileSystemException e) {
        LOG.debug(path, e);
        return null;
      }
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
      if (SystemInfo.isWin10OrNewer && WINDOWS_CS_API_AVAILABLE) {
        detected = OSAgnosticPathUtil.isAbsoluteDosPath(path) ? getNtfsCaseSensitivity(path) : FileAttributes.CaseSensitivity.UNKNOWN;
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
  public static boolean isCaseToggleable(@NotNull String name) {
    return !toggleCase(name).equals(name);
  }

  // return child which name can be used for querying by different-case names (e.g "child.txt" vs "CHILD.TXT")
  // or null if there are none (e.g., there's only one child "123.456").
  private static @Nullable String findCaseToggleableChild(File dir) {
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
      return FileAttributes.CaseSensitivity.UNKNOWN;
    }
    try {
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
      return FileAttributes.CaseSensitivity.UNKNOWN;
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
  private static volatile boolean LINUX_CS_API_AVAILABLE = true;

  private static FileAttributes.CaseSensitivity getLinuxCaseSensitivity(String path) {
    LibC libC;
    try {
      libC = LibC.INSTANCE;
    }
    catch (Throwable t) {
      LOG.warn(t);
      LINUX_CS_API_AVAILABLE = false;
      return FileAttributes.CaseSensitivity.UNKNOWN;
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
          return FileAttributes.CaseSensitivity.SENSITIVE;
        }
        // VFAT
        if (fs == 0x4D44L) {
          return FileAttributes.CaseSensitivity.INSENSITIVE;
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
            return FileAttributes.CaseSensitivity.UNKNOWN;
          }
          LongByReference flags = new LongByReference();
          if (e2P.fgetflags(path, flags) != 0) {
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
