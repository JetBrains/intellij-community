// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.io;

import com.intellij.openapi.diagnostic.LoggerRt;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.*;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

import static java.lang.System.getProperty;

/**
 * A stripped-down version of {@link com.intellij.openapi.util.io.FileUtil}.
 * Intended to use by external (out-of-IDE-process) runners and helpers, so it should not contain any library dependencies.
 */
public final class FileUtilRt {
  private static final int KILOBYTE = 1024;
  private static final int DEFAULT_INTELLISENSE_LIMIT = 2500 * KILOBYTE;

  public static final int MEGABYTE = KILOBYTE * KILOBYTE;

  /**
   * @deprecated Prefer using @link {@link com.intellij.openapi.vfs.limits.FileSizeLimit#getContentLoadLimit}
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  public static final int LARGE_FOR_CONTENT_LOADING = Math.max(20 * MEGABYTE, Math.max(getUserFileSizeLimit(), getUserContentLoadLimit()));

  /**
   * @deprecated Prefer using @link {@link com.intellij.openapi.vfs.limits.FileSizeLimit#getPreviewLimit}
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  public static final int LARGE_FILE_PREVIEW_SIZE = Math.min(getLargeFilePreviewSize(), LARGE_FOR_CONTENT_LOADING);

  private static final int MAX_FILE_IO_ATTEMPTS = 10;
  private static final boolean TRY_GC_IF_FILE_DELETE_FAILS = "true".equals(getProperty("idea.fs.try-gc-if-file-delete-fails", "true"));
  private static final boolean USE_FILE_CHANNELS = "true".equalsIgnoreCase(getProperty("idea.fs.useChannels"));


  private static String ourCanonicalTempPathCache;

  private FileUtilRt() { }

  public static boolean isJarOrZip(@NotNull File file) {
    return isJarOrZip(file, true);
  }

  public static boolean isJarOrZip(@NotNull File file, boolean isCheckIsDirectory) {
    if (isCheckIsDirectory && file.isDirectory()) {
      return false;
    }

    // do not use getName to avoid extra String creation (File.getName() calls substring)
    String path = file.getPath();
    return StringUtilRt.endsWithIgnoreCase(path, ".jar") || StringUtilRt.endsWithIgnoreCase(path, ".zip");
  }

  @NotNull
  public static List<String> splitPath(@NotNull String path, char separatorChar) {
    List<String> list = new ArrayList<>();
    int index = 0;
    int nextSeparator;
    while ((nextSeparator = path.indexOf(separatorChar, index)) != -1) {
      list.add(path.substring(index, nextSeparator));
      index = nextSeparator + 1;
    }
    list.add(path.substring(index));
    return list;
  }

  public static boolean isFilePathAcceptable(@NotNull File root, @Nullable FileFilter fileFilter) {
    if (fileFilter == null) {
      return true;
    }
    File file = root;
    do {
      if (!fileFilter.accept(file)) {
        return false;
      }
      file = file.getParentFile();
    }
    while (file != null);
    return true;
  }

  protected interface SymlinkResolver {
    @NotNull
    String resolveSymlinksAndCanonicalize(@NotNull String path, char separatorChar, boolean removeLastSlash);
    boolean isSymlink(@NotNull CharSequence path);
  }

  /**
   * Converts given path to canonical representation by eliminating '.'s, traversing '..'s, and omitting duplicate separators.
   * Please note that this method is symlink-unfriendly (i.e. result of "/path/to/link/../next" most probably will differ from
   * what {@link File#getCanonicalPath()} will return), so if the path may contain symlinks,
   * consider using {@link com.intellij.openapi.util.io.FileUtil#toCanonicalPath(String, boolean)} instead.
   */
  @Contract("null, _, _ -> null; !null,_,_->!null")
  public static String toCanonicalPath(@Nullable String path, char separatorChar, boolean removeLastSlash) {
    return toCanonicalPath(path, separatorChar, removeLastSlash, null);
  }

  @Contract("null, _, _, _ -> null; !null,_,_,_->!null")
  static String toCanonicalPath(@Nullable String path,
                                          char separatorChar,
                                          boolean removeLastSlash,
                                          @Nullable SymlinkResolver resolver) {
    if (path == null || path.isEmpty()) {
      return path;
    }
    if (path.charAt(0) == '.') {
      if (path.length() == 1) {
        return "";
      }
      char c = path.charAt(1);
      if (c == '/' || c == separatorChar) {
        path = path.substring(2);
      }
    }

    if (separatorChar != '/') {
      path = path.replace(separatorChar, '/');
    }
    // trying to speed up the common case when there are no "//" or "/."
    int index = -1;
    do {
      index = path.indexOf('/', index+1);
      char next = index == path.length() - 1 ? 0 : path.charAt(index + 1);
      if (next == '.' || next == '/') {
        break;
      }
    }
    while (index != -1);
    if (index == -1) {
      if (removeLastSlash) {
        int start = processRoot(path, NullAppendable.INSTANCE);
        int slashIndex = path.lastIndexOf('/');
        return slashIndex != -1 && slashIndex > start && slashIndex == path.length() - 1 ? path.substring(0, path.length() - 1) : path;
      }
      return path;
    }

    StringBuilder result = new StringBuilder(path.length());
    int start = processRoot(path, result);
    int dots = 0;
    boolean separator = true;

    for (int i = start; i < path.length(); ++i) {
      char c = path.charAt(i);
      if (c == '/') {
        if (!separator) {
          if (!processDots(result, dots, start, resolver)) {
            return resolver.resolveSymlinksAndCanonicalize(path, separatorChar, removeLastSlash);
          }
          dots = 0;
        }
        separator = true;
      }
      else if (c == '.') {
        if (separator || dots > 0) {
          ++dots;
        }
        else {
          result.append('.');
        }
        separator = false;
      }
      else {
        while (dots > 0) {
          result.append('.');
          dots--;
        }
        result.append(c);
        separator = false;
      }
    }

    if (dots > 0) {
      if (!processDots(result, dots, start, resolver)) {
        return resolver.resolveSymlinksAndCanonicalize(path, separatorChar, removeLastSlash);
      }
    }

    int lastChar = result.length() - 1;
    if (removeLastSlash && lastChar >= 0 && result.charAt(lastChar) == '/' && lastChar > start) {
      result.deleteCharAt(lastChar);
    }

    return result.toString();
  }

  @SuppressWarnings("DuplicatedCode")
  private static int processRoot(@NotNull String path, @NotNull Appendable result) {
    try {
      if (SystemInfoRt.isWindows && path.length() > 1 && path.charAt(0) == '/' && path.charAt(1) == '/') {
        result.append("//");

        int hostStart = 2;
        while (hostStart < path.length() && path.charAt(hostStart) == '/') hostStart++;
        if (hostStart == path.length()) return hostStart;
        int hostEnd = path.indexOf('/', hostStart);
        if (hostEnd < 0) hostEnd = path.length();
        result.append(path, hostStart, hostEnd);
        result.append('/');

        int shareStart = hostEnd;
        while (shareStart < path.length() && path.charAt(shareStart) == '/') shareStart++;
        if (shareStart == path.length()) return shareStart;
        int shareEnd = path.indexOf('/', shareStart);
        if (shareEnd < 0) shareEnd = path.length();
        result.append(path, shareStart, shareEnd);
        result.append('/');

        return shareEnd;
      }

      if (!path.isEmpty() && path.charAt(0) == '/') {
        result.append('/');
        return 1;
      }

      if (path.length() > 2 && path.charAt(1) == ':' && path.charAt(2) == '/') {
        result.append(path, 0, 3);
        return 3;
      }

      return 0;
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Contract("_, _, _, null -> true")
  private static boolean processDots(@NotNull StringBuilder result, int dots, int start, @Nullable SymlinkResolver symlinkResolver) {
    if (dots == 2) {
      int pos = -1;
      if (!StringUtilRt.endsWith(result, "/../") && !"../".contentEquals(result)) {
        pos = StringUtilRt.lastIndexOf(result, '/', start, result.length() - 1);
        if (pos >= 0) {
          ++pos;  // separator found, trim to next char
        }
        else if (start > 0) {
          pos = start;  // path is absolute, trim to root ('/..' -> '/')
        }
        else if (result.length() > 0) {
          pos = 0;  // path is relative, trim to default ('a/..' -> '')
        }
      }
      if (pos >= 0) {
        if (symlinkResolver != null && symlinkResolver.isSymlink(result)) {
          return false;
        }
        result.delete(pos, result.length());
      }
      else {
        result.append("../");  // impossible to traverse, keep as-is
      }
    }
    else if (dots != 1) {
      for (int i = 0; i < dots; i++) {
        result.append('.');
      }
      result.append('/');
    }
    return true;
  }

  @NotNull
  public static String getExtension(@NotNull String fileName) {
    int index = fileName.lastIndexOf('.');
    if (index < 0) return "";
    return fileName.substring(index + 1);
  }

  @NotNull
  public static CharSequence getExtension(@NotNull CharSequence fileName) {
    return getExtension(fileName, "");
  }

  @Contract("_,!null -> !null")
  public static CharSequence getExtension(@NotNull CharSequence fileName, @Nullable String defaultValue) {
    int index = StringUtilRt.lastIndexOf(fileName, '.', 0, fileName.length());
    if (index < 0) {
      return defaultValue;
    }
    return fileName.subSequence(index + 1, fileName.length());
  }

  public static boolean extensionEquals(@NotNull @NonNls String filePath, @NotNull @NonNls String extension) {
    int extLen = extension.length();
    if (extLen == 0) {
      int lastSlash = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
      return filePath.indexOf('.', lastSlash+1) == -1;
    }
    int extStart = filePath.length() - extLen;
    return extStart >= 1 && filePath.charAt(extStart-1) == '.'
           && filePath.regionMatches(!SystemInfoRt.isFileSystemCaseSensitive, extStart, extension, 0, extLen);
  }

  public static boolean fileNameEquals(@NotNull File file, @NonNls @NotNull String name) {
    return fileNameEquals(file.getName(), name);
  }

  public static boolean fileNameEquals(@NotNull @NonNls CharSequence fileName,
                                       @NotNull @NonNls CharSequence expectedName) {
    return StringUtilRt.equal(expectedName, fileName, SystemInfoRt.isFileSystemCaseSensitive);
  }

  @NotNull
  public static String toSystemDependentName(@NotNull String path) {
    return toSystemDependentName(path, File.separatorChar);
  }

  @NotNull
  public static String toSystemDependentName(@NotNull String path, char separatorChar) {
    return path.replace('/', separatorChar).replace('\\', separatorChar);
  }

  @NotNull
  public static String toSystemIndependentName(@NotNull String path) {
    return path.replace('\\', '/');
  }

  /**
   * <p>Gets the relative path from the {@code base} to the {@code file} regardless existence or the type of the {@code base}.</p>
   *
   * <p>NOTE: if a file (not a directory) is passed as the {@code base}, the result cannot be used as a relative path
   * from the {@code base} parent directory to the {@code file}.</p>
   *
   * @param base the base
   * @param file the file
   * @return the relative path from the {@code base} to the {@code file}, or {@code null}
   */
  @Nullable
  @Contract(pure = true)
  public static String getRelativePath(File base, File file) {
    if (base == null || file == null) return null;

    if (base.equals(file)) return ".";

    String filePath = file.getAbsolutePath();
    String basePath = base.getAbsolutePath();
    return getRelativePath(basePath, filePath, File.separatorChar);
  }

  @Nullable
  @Contract(pure = true)
  public static String getRelativePath(@NotNull String basePath, @NotNull String filePath, char separator) {
    return getRelativePath(basePath, filePath, separator, SystemInfoRt.isFileSystemCaseSensitive);
  }

  @Nullable
  @Contract(pure = true)
  public static String getRelativePath(@NotNull String basePath, @NotNull String filePath, char separator, boolean caseSensitive) {
    basePath = ensureEnds(basePath, separator);

    if (caseSensitive ? basePath.equals(ensureEnds(filePath, separator)) : basePath.equalsIgnoreCase(ensureEnds(filePath, separator))) {
      return ".";
    }

    int len = 0;
    int lastSeparatorIndex = 0; // need this for cases like this: base="/temp/abc/base" and file="/temp/ab"
    CharComparingStrategy strategy = caseSensitive ? CharComparingStrategy.IDENTITY : CharComparingStrategy.CASE_INSENSITIVE;
    while (len < filePath.length() && len < basePath.length() && strategy.charsEqual(filePath.charAt(len), basePath.charAt(len))) {
      if (basePath.charAt(len) == separator) {
        lastSeparatorIndex = len;
      }
      len++;
    }

    if (len == 0) return null;

    StringBuilder relativePath = new StringBuilder();
    for (int i = len; i < basePath.length(); i++) {
      if (basePath.charAt(i) == separator) {
        relativePath.append("..");
        relativePath.append(separator);
      }
    }
    relativePath.append(filePath.substring(lastSeparatorIndex + 1));

    return relativePath.toString();
  }

  @NotNull
  @Contract(pure = true)
  private static String ensureEnds(@NotNull String s, char endsWith) {
    return StringUtilRt.endsWithChar(s, endsWith) ? s : s + endsWith;
  }

  @NotNull
  public static CharSequence getNameWithoutExtension(@NotNull CharSequence name) {
    int i = StringUtilRt.lastIndexOf(name, '.', 0, name.length());
    return i < 0 ? name : name.subSequence(0, i);
  }

  @NotNull
  public static String getNameWithoutExtension(@NotNull String name) {
    return getNameWithoutExtension((CharSequence)name).toString();
  }

  @NotNull
  public static File createTempDirectory(@NotNull String prefix, @Nullable String suffix) throws IOException {
    return createTempDirectory(prefix, suffix, true);
  }

  @NotNull
  public static File createTempDirectory(@NotNull String prefix, @Nullable String suffix, boolean deleteOnExit) throws IOException {
    File dir = new File(getTempDirectory());
    return createTempDirectory(dir, prefix, suffix, deleteOnExit);
  }

  @NotNull
  public static File createTempDirectory(@NotNull File dir,
                                         @NotNull String prefix, @Nullable String suffix) throws IOException {
    return createTempDirectory(dir, prefix, suffix, true);
  }

  @NotNull
  public static File createTempDirectory(@NotNull File dir,
                                         @NotNull String prefix, @Nullable String suffix,
                                         boolean deleteOnExit) throws IOException {
    File file = doCreateTempFile(dir, prefix, suffix, true);
    if (deleteOnExit) {
      // default deleteOnExit does not remove dirs if they are not empty
      FilesToDeleteHolder.ourFilesToDelete.add(file.getPath());
    }
    if (!file.isDirectory()) {
      throw new IOException("Cannot create a directory: " + file);
    }
    return file;
  }

  private static class FilesToDeleteHolder {
    private static final Queue<String> ourFilesToDelete = createFilesToDelete();

    @NotNull
    private static Queue<String> createFilesToDelete() {
      final ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();
      Runtime.getRuntime().addShutdownHook(new Thread("FileUtil deleteOnExit") {
        @Override
        public void run() {
          String name;
          while ((name = queue.poll()) != null) {
            delete(new File(name));
          }
        }
      });
      return queue;
    }
  }

  @NotNull
  public static File createTempFile(@NotNull String prefix, @Nullable String suffix) throws IOException {
    return createTempFile(prefix, suffix, false); //false until TeamCity fixes its plugin
  }

  @NotNull
  public static File createTempFile(@NonNls @NotNull String prefix, @NonNls @Nullable String suffix, boolean deleteOnExit) throws IOException {
    File dir = new File(getTempDirectory());
    return createTempFile(dir, prefix, suffix, true, deleteOnExit);
  }

  @NotNull
  public static File createTempFile(@NotNull File dir, @NotNull String prefix, @Nullable String suffix) throws IOException {
    return createTempFile(dir, prefix, suffix, true, true);
  }

  @NotNull
  public static File createTempFile(@NotNull File dir, @NotNull String prefix, @Nullable String suffix, boolean create) throws IOException {
    return createTempFile(dir, prefix, suffix, create, true);
  }

  @NotNull
  public static File createTempFile(@NotNull File dir, @NotNull String prefix, @Nullable String suffix, boolean create, boolean deleteOnExit) throws IOException {
    File file = doCreateTempFile(dir, prefix, suffix, false);
    if (deleteOnExit) {
      //noinspection SSBasedInspection
      file.deleteOnExit();
    }
    if (!create) {
      if (!file.delete() && file.exists()) {
        throw new IOException("Cannot delete a file: " + file);
      }
    }
    return file;
  }

  private static final Random RANDOM = new Random();

  @NotNull
  private static File doCreateTempFile(@NotNull File dir, @NotNull String prefix, @Nullable String suffix, boolean isDirectory) throws IOException {
    //noinspection ResultOfMethodCallIgnored
    dir.mkdirs();

    if (prefix.length() < 3) {
      prefix = (prefix + "___").substring(0, 3);
    }
    if (suffix == null) {
      suffix = "";
    }
    // normalize and use only the file name from the prefix
    prefix = new File(prefix).getName();

    int attempts = 0;
    int i = 0;
    int maxFileNumber = 10;
    IOException exception = null;
    while (true) {
      File f = null;
      try {
        f = calcName(dir, prefix, suffix, i);

        boolean success = isDirectory ? f.mkdir() : f.createNewFile();
        if (success) {
          return normalizeFile(f);
        }
      }
      catch (IOException e) { // Win32 createFileExclusively access denied
        exception = e;
      }
      attempts++;
      int MAX_ATTEMPTS = 100;
      if (attempts > maxFileNumber / 2 || attempts > MAX_ATTEMPTS) {
        String[] children = dir.list();
        int size = children == null ? 0 : children.length;
        maxFileNumber = Math.max(10, size * 10); // if too many files are in tmp dir, we need a bigger random range than a meager 10
        if (attempts > MAX_ATTEMPTS) {
          throw exception != null ? exception: new IOException("Unable to create a temporary file " + f + "\nDirectory '" + dir +
                                "' list ("+size+" children): " + Arrays.toString(children));
        }
      }

      // For some reason, the file1 can't be created (previous file1 was deleted but got locked by antivirus?). Try file2.
      i++;
      if (i > 2) {
        // generate random suffix if too many failures
        i = 2 + RANDOM.nextInt(maxFileNumber);
      }
    }
  }

  @NotNull
  private static File calcName(@NotNull File dir, @NotNull String prefix, @NotNull String suffix, int i) throws IOException {
    prefix = i == 0 ? prefix : prefix + i;
    if (prefix.endsWith(".") && suffix.startsWith(".")) {
      prefix = prefix.substring(0, prefix.length() - 1);
    }
    String name = prefix + suffix;
    File f = new File(dir, name);
    if (!name.equals(f.getName())) {
      throw new IOException("A generated name is malformed: '" + name + "' (" + f + ")");
    }
    return f;
  }

  @NotNull
  private static File normalizeFile(@NotNull File temp) throws IOException {
    File canonical = temp.getCanonicalFile();
    return SystemInfoRt.isWindows && canonical.getAbsolutePath().contains(" ") ? temp.getAbsoluteFile() : canonical;
  }

  @NotNull
  public static String getTempDirectory() {
    if (ourCanonicalTempPathCache == null) {
      ourCanonicalTempPathCache = calcCanonicalTempPath();
    }
    return ourCanonicalTempPathCache;
  }

  @NotNull
  private static String calcCanonicalTempPath() {
    File file = new File(getProperty("java.io.tmpdir"));
    try {
      String canonical = file.getCanonicalPath();
      if (!SystemInfoRt.isWindows || !canonical.contains(" ")) {
        return canonical;
      }
    }
    catch (IOException ignore) { }
    return file.getAbsolutePath();
  }

  @TestOnly
  static void resetCanonicalTempPathCache(@NotNull String tempPath) {
    ourCanonicalTempPathCache = tempPath;
  }

  @NotNull
  public static File generateRandomTemporaryPath() throws IOException {
    return generateRandomTemporaryPath("", "");
  }

  @NotNull
  public static File generateRandomTemporaryPath(@NotNull String prefix, @NotNull String suffix) throws IOException {
    File file = new File(getTempDirectory(), prefix + UUID.randomUUID() + suffix);
    int i = 0;
    while (file.exists() && i < 5) {
      file = new File(getTempDirectory(), prefix + UUID.randomUUID() + suffix);
      ++i;
    }
    if (file.exists()) {
      throw new IOException("Couldn't generate unique random path.");
    }
    return normalizeFile(file);
  }

  @NotNull
  public static String loadFile(@NotNull File file) throws IOException {
    return loadFile(file, null, false);
  }

  @NotNull
  public static String loadFile(@NotNull File file, boolean convertLineSeparators) throws IOException {
    return loadFile(file, null, convertLineSeparators);
  }

  @NotNull
  public static String loadFile(@NotNull File file, @Nullable String encoding) throws IOException {
    return loadFile(file, encoding, false);
  }

  @NotNull
  public static String loadFile(@NotNull File file, @Nullable String encoding, boolean convertLineSeparators) throws IOException {
    String s = new String(loadFileText(file, encoding));
    return convertLineSeparators ? StringUtilRt.convertLineSeparators(s) : s;
  }

  @NotNull
  public static char[] loadFileText(@NotNull File file) throws IOException {
    return loadFileText(file, (String)null);
  }

  @NotNull
  public static char[] loadFileText(@NotNull File file, @Nullable String encoding) throws IOException {
    InputStream stream = new FileInputStream(file);
    try (Reader reader = encoding == null
                         ? new InputStreamReader(stream, Charset.defaultCharset())
                         : new InputStreamReader(stream, encoding)) {
      return loadText(reader, (int)file.length());
    }
  }

  @NotNull
  public static char[] loadFileText(@NotNull File file, @NotNull Charset encoding) throws IOException {
    try (Reader reader = new InputStreamReader(new FileInputStream(file), encoding)) {
      return loadText(reader, (int)file.length());
    }
  }

  @NotNull
  public static char[] loadText(@NotNull Reader reader, int length) throws IOException {
    char[] chars = new char[length];
    int count = 0;
    while (count < chars.length) {
      int n = reader.read(chars, count, chars.length - count);
      if (n <= 0) break;
      count += n;
    }
    if (count == chars.length) {
      return chars;
    }
    else {
      return Arrays.copyOf(chars, count);
    }
  }

  @NotNull
  public static List<String> loadLines(@NotNull File file) throws IOException {
    return loadLines(file.getPath());
  }

  @NotNull
  public static List<String> loadLines(@NotNull File file, @Nullable String encoding) throws IOException {
    return loadLines(file.getPath(), encoding);
  }

  @NotNull
  public static List<String> loadLines(@NotNull String path) throws IOException {
    return loadLines(path, null);
  }

  @NotNull
  public static List<String> loadLines(@NotNull String path, @Nullable String encoding) throws IOException {
    InputStream stream = new FileInputStream(path);
    try (BufferedReader reader = new BufferedReader(
      encoding == null ? new InputStreamReader(stream, Charset.defaultCharset()) : new InputStreamReader(stream, encoding))) {
      return loadLines(reader);
    }
  }

  @NotNull
  public static List<String> loadLines(@NotNull BufferedReader reader) throws IOException {
    List<String> lines = new ArrayList<>();
    String line;
    while ((line = reader.readLine()) != null) {
      lines.add(line);
    }
    return lines;
  }

  @NotNull
  public static byte[] loadBytes(@NotNull InputStream stream) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    copy(stream, buffer);
    return buffer.toByteArray();
  }

  /**
   * @deprecated Prefer using @link {@link com.intellij.openapi.vfs.limits.FileSizeLimit#isTooLarge}
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  public static boolean isTooLarge(long len) {
    return len > LARGE_FOR_CONTENT_LOADING;
  }

  @NotNull
  public static byte[] loadBytes(@NotNull InputStream stream, int length) throws IOException {
    if (length == 0) {
      return ArrayUtilRt.EMPTY_BYTE_ARRAY;
    }
    byte[] bytes = new byte[length];
    int count = 0;
    while (count < length) {
      int n = stream.read(bytes, count, length - count);
      if (n <= 0) break;
      count += n;
    }
    return bytes;
  }

  /**
   * Get parent for the file.
   * The method correctly processes `.` and `..` in file names.
   * The name remains relative if it was relative before.
   *
   * @param file a file to analyze
   * @return file's parent, or {@code null} if the file has no parent.
   */
  @Nullable
  public static File getParentFile(@NotNull File file) {
    int skipCount = 0;
    File parentFile = file;
    while (true) {
      parentFile = parentFile.getParentFile();
      if (parentFile == null) {
        return null;
      }
      if (".".equals(parentFile.getName())) {
        continue;
      }
      if ("..".equals(parentFile.getName())) {
        skipCount++;
        continue;
      }
      if (skipCount > 0) {
        skipCount--;
        continue;
      }
      return parentFile;
    }
  }

  /**
   * @param file file or directory to delete
   * @return {@code true} if the file did not exist or was successfully deleted
   */
  public static boolean delete(@NotNull File file) {
    try {
      deleteRecursively(file.toPath());
      return true;
    }
    catch (IOException e) {
      return false;
    }
    catch (Exception e) {
      logger().info(e);
      return false;
    }
  }

  public static void deleteRecursively(@NotNull Path path) throws IOException {
    deleteRecursively(path, null);
  }

  interface DeleteRecursivelyCallback {
    void beforeDeleting(Path path);
  }

  static void deleteRecursively(@NotNull Path path, @Nullable final DeleteRecursivelyCallback callback) throws IOException {
    if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
      return;
    }

    try {
      Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
          if (SystemInfoRt.isWindows && attrs.isOther()) {
            // probably an NTFS reparse point
            doDelete(dir);
            return FileVisitResult.SKIP_SUBTREE;
          }
          else {
            return FileVisitResult.CONTINUE;
          }
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          if (callback != null) callback.beforeDeleting(file);
          doDelete(file);
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
          try {
            if (callback != null) callback.beforeDeleting(dir);
            doDelete(dir);
            return FileVisitResult.CONTINUE;
          }
          catch (IOException e) {
            if (exc != null) {
              exc.addSuppressed(e);
              throw exc;
            }
            else {
              throw e;
            }
          }
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
          if (SystemInfoRt.isWindows && exc instanceof NoSuchFileException) {
            // could be an aimless junction
            doDelete(file);
            return FileVisitResult.CONTINUE;
          }
          else {
            throw exc;
          }
        }
      });
    }
    catch (NoSuchFileException ignored) {
    }
  }

  private static void doDelete(Path path) throws IOException {
    for (int attemptsLeft = MAX_FILE_IO_ATTEMPTS; attemptsLeft > 0; attemptsLeft--) {
      try {
        Files.deleteIfExists(path);
        return;
      }
      catch (IOException e) {
        if (!SystemInfoRt.isWindows || attemptsLeft == 1) {
          //noinspection InstanceofCatchParameter
          if (e instanceof DirectoryNotEmptyException) {
            //add the directory content to the exception:
            DirectoryNotEmptyException replacingEx = directoryNotEmptyExceptionWithMoreDiagnostic(path);
            replacingEx.addSuppressed(e);
            throw replacingEx;
          }
          throw e;
        }

        //noinspection InstanceofCatchParameter
        if (e instanceof AccessDeniedException) {
          // a file could be read-only, then fallback to legacy java.io API helps
          try {
            File file = path.toFile();
            if (file.delete() || !file.exists()) {
              break;
            }
          }
          catch (Throwable ignored) { }

          if (attemptsLeft == MAX_FILE_IO_ATTEMPTS / 2 && TRY_GC_IF_FILE_DELETE_FAILS) {
            //Non-closed stream/channel, or not-yet-unmapped memory-mapped buffer could be a reason for
            // AccessDeniedException on an attempt to delete file on Windows.
            // => kick in GC/finalizers to collect that is not yet collected.
            //
            // Those are quite heavy, system-wide operations, which is why we fall back to them only after
            // several attempts to delete the file already failed. But we don't do it at the last attempt
            // either, because GC/finalizers tasks could run async/background and may need some time to
            // finish.

            //noinspection CallToSystemGC
            System.gc();
            System.runFinalization();
          }
        }
      }

      try { Thread.sleep(10); }
      catch (InterruptedException ignored) { }
    }
  }

  private static DirectoryNotEmptyException directoryNotEmptyExceptionWithMoreDiagnostic(@NotNull Path path) throws IOException {
    DirectoryStream.Filter<Path> alwaysTrue = new DirectoryStream.Filter<Path>() {
      @Override
      public boolean accept(Path entry) {
        return true;
      }
    };
    try (DirectoryStream<Path> children = Files.newDirectoryStream(path, alwaysTrue)) {
      StringBuilder sb = new StringBuilder();
      for (Path child : children) {
        sb.append(child.getFileName()).append(", ");
      }
      return new DirectoryNotEmptyException(path.toAbsolutePath() + " (children: " + sb + ")");
    }
  }


  public interface RepeatableIOOperation<T, E extends Throwable> {
    @Nullable T execute(boolean lastAttempt) throws E;
  }

  @Nullable
  public static <T, E extends Throwable> T doIOOperation(@NotNull RepeatableIOOperation<T, E> ioTask) throws E {
    for (int i = MAX_FILE_IO_ATTEMPTS; i > 0; i--) {
      T result = ioTask.execute(i == 1);
      if (result != null) return result;

      try {
        Thread.sleep(10);
      }
      catch (InterruptedException ignored) { }
    }
    return null;
  }

  static boolean deleteFile(@NotNull final File file) {
    Boolean result = doIOOperation(new RepeatableIOOperation<Boolean, RuntimeException>() {
      @Override
      public Boolean execute(boolean lastAttempt) {
        if (file.delete() || !file.exists()) return Boolean.TRUE;
        else if (lastAttempt) return Boolean.FALSE;
        else return null;
      }
    });
    return Boolean.TRUE.equals(result);
  }

  public static boolean ensureCanCreateFile(@NotNull File file) {
    if (file.exists()) {
      return file.canWrite();
    }
    if (!createIfNotExists(file)) {
      return false;
    }
    return delete(file);
  }

  public static boolean createIfNotExists(@NotNull File file) {
    if (file.exists()) return true;
    try {
      if (!createParentDirs(file)) return false;

      OutputStream s = new FileOutputStream(file);
      s.close();
      return true;
    }
    catch (IOException e) {
      logger().info(e);
      return false;
    }
  }

  public static boolean createParentDirs(@NotNull File file) {
    File parentPath = file.getParentFile();
    return parentPath == null || createDirectory(parentPath);
  }

  public static boolean createDirectory(@NotNull File path) {
    return path.isDirectory() || path.mkdirs();
  }

  public static void copy(@NotNull File fromFile, @NotNull File toFile) throws IOException {
    if (!ensureCanCreateFile(toFile)) {
      return;
    }

    try (FileOutputStream fos = new FileOutputStream(toFile)) {
      try (FileInputStream fis = new FileInputStream(fromFile)) {
        copy(fis, fos);
      }
    }

    long timeStamp = fromFile.lastModified();
    if (timeStamp < 0) {
      logger().warn("Invalid timestamp " + timeStamp + " of '" + fromFile + "'");
    }
    else if (!toFile.setLastModified(timeStamp)) {
      logger().warn("Unable to set timestamp " + timeStamp + " to '" + toFile + "'");
    }
  }

  public static void copy(@NotNull InputStream inputStream, @NotNull OutputStream outputStream) throws IOException {
    if (USE_FILE_CHANNELS && inputStream instanceof FileInputStream && outputStream instanceof FileOutputStream) {
      try (FileChannel fromChannel = ((FileInputStream)inputStream).getChannel()) {
        try (FileChannel toChannel = ((FileOutputStream)outputStream).getChannel()) {
          fromChannel.transferTo(0, Long.MAX_VALUE, toChannel);
        }
      }
    }
    else {
      byte[] buffer = new byte[8192];
      while (true) {
        int read = inputStream.read(buffer);
        if (read < 0) break;
        outputStream.write(buffer, 0, read);
      }
    }
  }

  public static int getUserFileSizeLimit() {
    return parseKilobyteProperty("idea.max.intellisense.filesize", DEFAULT_INTELLISENSE_LIMIT);
  }

  public static int getUserContentLoadLimit() {
    return parseKilobyteProperty("idea.max.content.load.filesize", 20 * MEGABYTE);
  }

  private static int getLargeFilePreviewSize() {
    return parseKilobyteProperty("idea.max.content.load.large.preview.size", DEFAULT_INTELLISENSE_LIMIT);
  }

  private static int parseKilobyteProperty(String key, int defaultValue) {
    try {
      long i = Integer.parseInt(getProperty(key, String.valueOf(defaultValue / KILOBYTE)));
      if (i < 0) return Integer.MAX_VALUE;
      return (int) Math.min(i * KILOBYTE, Integer.MAX_VALUE);
    }
    catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  private interface CharComparingStrategy {
    CharComparingStrategy IDENTITY = new CharComparingStrategy() {
      @Override
      public boolean charsEqual(char ch1, char ch2) {
        return ch1 == ch2;
      }
    };
    CharComparingStrategy CASE_INSENSITIVE = new CharComparingStrategy() {
      @Override
      public boolean charsEqual(char ch1, char ch2) {
        return StringUtilRt.charsEqualIgnoreCase(ch1, ch2);
      }
    };

    boolean charsEqual(char ch1, char ch2);
  }

  private static LoggerRt logger() {
    return LoggerRt.getInstance("#com.intellij.openapi.util.io.FileUtilRt");
  }

  /**
   * Energy-efficient variant of {@link File#toURI()}. Unlike the latter, doesn't check whether a given file is a directory,
   * so URIs never have a trailing slash (but are nevertheless compatible with {@link File#File(URI)}).
   */
  public static @NotNull URI fileToUri(@NotNull File file) {
    String path = file.getAbsolutePath();
    if (File.separatorChar != '/') path = path.replace(File.separatorChar, '/');
    if (!path.startsWith("/")) path = '/' + path;
    if (path.startsWith("//")) path = "//" + path;
    try {
      return new URI("file", null, path, null);
    }
    catch (URISyntaxException e) {
      throw new IllegalArgumentException(path, e);  // unlikely, as `File#toURI()` doesn't declare any exceptions
    }
  }

  public static int pathHashCode(@Nullable String path) {
    if (path == null || path.isEmpty()) {
      return 0;
    }
    path = toCanonicalPath(path, File.separatorChar, true);
    return SystemInfoRt.isFileSystemCaseSensitive ? path.hashCode() : StringUtilRt.stringHashCodeInsensitive(path);
  }

  public static boolean filesEqual(@Nullable File file1, @Nullable File file2) {
    // on macOS java.io.File.equals() is incorrectly case-sensitive
    return pathsEqual(file1 == null ? null : file1.getPath(),
                      file2 == null ? null : file2.getPath());
  }

  @SuppressWarnings("RedundantSuppression")
  public static boolean pathsEqual(@Nullable String path1, @Nullable String path2) {
    //noinspection StringEquality,SSBasedInspection
    if (path1 == path2) {
      return true;
    }
    if (path1 == null || path2 == null) {
      return false;
    }

    path1 = toCanonicalPath(path1, File.separatorChar, true);
    path2 = toCanonicalPath(path2, File.separatorChar, true);
    if (SystemInfoRt.isFileSystemCaseSensitive) {
      return path1.equals(path2);
    }
    else {
      return path1.equalsIgnoreCase(path2);
    }
  }
}
