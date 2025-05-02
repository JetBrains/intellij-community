// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.io;

import com.intellij.UtilBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.util.*;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.containers.JBTreeTraverser;
import com.intellij.util.io.URLUtil;
import gnu.trove.TObjectHashingStrategy;
import org.intellij.lang.annotations.RegExp;
import org.jetbrains.annotations.*;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static java.nio.file.attribute.PosixFilePermission.*;

/**
 * Obsolete; please use NIO API instead ({@link Path}, {@link Files}, {@link NioFiles}, etc.)
 */
@ApiStatus.NonExtendable
@ApiStatus.Obsolete
public class FileUtil {
  public static final String ASYNC_DELETE_EXTENSION = ".__del__";

  public static final int REGEX_PATTERN_FLAGS = SystemInfoRt.isFileSystemCaseSensitive ? 0 : Pattern.CASE_INSENSITIVE;

  private static final Logger LOG = Logger.getInstance(FileUtil.class);

  @Contract(pure = true)
  public static @NotNull @NlsSafe String join(String @NotNull ... parts) {
    return String.join(File.separator, parts);
  }

  /**
   * Gets the relative path from the {@code base} to the {@code file} regardless existence or the type of the {@code base}.
   * <p>
   * NOTE: if the file(not directory) passed as the {@code base} the result can not be used as a relative path from the {@code base} parent directory to the {@code file}
   *
   * @param base the base
   * @param file the file
   * @return the relative path from the {@code base} to the {@code file} or {@code null}
   */
  @Contract(pure = true)
  public static @Nullable @NlsSafe String getRelativePath(File base, File file) {
    return FileUtilRt.getRelativePath(base, file);
  }

  @Contract(pure = true)
  public static @Nullable @NlsSafe String getRelativePath(@NotNull String basePath, @NotNull String filePath, char separator) {
    return FileUtilRt.getRelativePath(basePath, filePath, separator);
  }

  @Contract(pure = true)
  public static @Nullable @NlsSafe String getRelativePath(@NotNull String basePath,
                                                          @NotNull String filePath,
                                                          char separator,
                                                          boolean caseSensitive) {
    return FileUtilRt.getRelativePath(basePath, filePath, separator, caseSensitive);
  }

  public static boolean isAbsolute(@NotNull String path) {
    return !path.isEmpty() && new File(path).isAbsolute();
  }

  public static boolean exists(@Nullable String path) {
    return path != null && new File(path).exists();
  }

  /**
   * Check if the {@code ancestor} is an ancestor of {@code file}.
   *
   * @param ancestor supposed ancestor.
   * @param file     supposed descendant.
   * @param strict   if {@code false} then this method returns {@code true} if {@code ancestor} equals to {@code file}.
   * @return {@code true} if {@code ancestor} is parent of {@code file}; {@code false} otherwise.
   */
  public static boolean isAncestor(@NotNull File ancestor, @NotNull File file, boolean strict) {
    return isAncestor(ancestor.getPath(), file.getPath(), strict);
  }

  public static boolean isAncestor(@NotNull String ancestor, @NotNull String file, boolean strict) {
    return !ThreeState.NO.equals(isAncestorThreeState(ancestor, file, strict));
  }

  /**
   * Checks if the {@code ancestor} is an ancestor of the {@code file}, and if it is an immediate parent or not.
   *
   * @param ancestor supposed ancestor.
   * @param file     supposed descendant.
   * @param strict   if {@code false}, the file can be ancestor of itself,
   *                 i.e. the method returns {@code ThreeState.YES} if {@code ancestor} equals to {@code file}.
   * @return {@code ThreeState.YES} if ancestor is an immediate parent of the file,
   * {@code ThreeState.UNSURE} if ancestor is not immediate parent of the file,
   * {@code ThreeState.NO} if ancestor is not a parent of the file at all.
   */
  public static @NotNull ThreeState isAncestorThreeState(@NotNull String ancestor, @NotNull String file, boolean strict) {
    String ancestorPath = toCanonicalPath(ancestor);
    String filePath = toCanonicalPath(file);
    return startsWith(filePath, ancestorPath, strict, SystemInfoRt.isFileSystemCaseSensitive, true);
  }

  @Contract(pure = true)
  public static boolean startsWith(@NotNull String path, @NotNull String prefix) {
    return startsWith(path, prefix, SystemInfoRt.isFileSystemCaseSensitive);
  }

  @Contract(pure = true)
  public static boolean startsWith(@NotNull String path, @NotNull String prefix, boolean isCaseSensitive) {
    return startsWith(path, prefix, isCaseSensitive, false);
  }

  @Contract(pure = true)
  public static boolean startsWith(@NotNull String path, @NotNull String prefix, boolean isCaseSensitive, boolean strict) {
    return !ThreeState.NO.equals(startsWith(path, prefix, strict, isCaseSensitive, false));
  }

  @Contract(pure = true)
  private static @NotNull ThreeState startsWith(@NotNull String path, @NotNull String prefix, boolean strict, boolean isCaseSensitive, boolean checkImmediateParent) {
    int pathLength = path.length();
    int prefixLength = prefix.length();
    if (prefixLength == 0) return pathLength == 0 ? ThreeState.YES : ThreeState.UNSURE;
    if (prefixLength > pathLength) return ThreeState.NO;
    if (!path.regionMatches(!isCaseSensitive, 0, prefix, 0, prefixLength)) return ThreeState.NO;
    if (pathLength == prefixLength) {
      return strict ? ThreeState.NO : ThreeState.YES;
    }
    char lastPrefixChar = prefix.charAt(prefixLength - 1);
    int slashOrSeparatorIdx = prefixLength;
    if (lastPrefixChar == '/' || lastPrefixChar == File.separatorChar) {
      slashOrSeparatorIdx = prefixLength - 1;
    }
    char next = path.charAt(slashOrSeparatorIdx);
    if (next == '/' || next == File.separatorChar) {
      if (!checkImmediateParent) return ThreeState.YES;
      if (slashOrSeparatorIdx == pathLength - 1) return ThreeState.YES;
      int idxNext = path.indexOf(next, slashOrSeparatorIdx + 1);
      idxNext = idxNext == -1 ? path.indexOf(next == '/' ? '\\' : '/', slashOrSeparatorIdx + 1) : idxNext;
      return idxNext == -1 ? ThreeState.YES : ThreeState.UNSURE;
    }
    else {
      return ThreeState.NO;
    }
  }

  @Contract(pure = true)
  public static @Nullable File findAncestor(@NotNull File f1, @NotNull File f2) {
    File ancestor = f1;
    while (ancestor != null && !isAncestor(ancestor, f2, false)) {
      ancestor = ancestor.getParentFile();
    }
    return ancestor;
  }

  @Contract(pure = true)
  public static @Nullable Path findAncestor(@NotNull Path path1, @NotNull Path path2) {
    Path ancestor = path1;
    while (ancestor != null && !path2.startsWith(ancestor)) {
      ancestor = ancestor.getParent();
    }
    return ancestor;
  }

  @Contract(pure = true)
  public static @Nullable File getParentFile(@NotNull File file) {
    return FileUtilRt.getParentFile(file);
  }

  public static byte @NotNull [] loadFileBytes(@NotNull File file) throws IOException {
    byte[] bytes;
    try (InputStream stream = new FileInputStream(file)) {
      long len = file.length();
      if (len < 0) {
        throw new IOException("File length reported negative, probably doesn't exist");
      }

      if (FileUtilRt.isTooLarge(len)) {
        throw new FileTooBigException("Attempt to load '" + file + "' in memory buffer, file length is " + len + " bytes.");
      }

      bytes = loadBytes(stream, (int)len);
    }
    return bytes;
  }

  /**
   * use {@link com.intellij.openapi.vfs.VfsUtilCore#loadNBytes}
   * or {@link InputStream#readNBytes(int)}
   */
  @Deprecated
  public static byte @NotNull [] loadFirstAndClose(@NotNull InputStream stream, int maxLength) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try {
      copy(stream, maxLength, buffer);
    }
    finally {
      stream.close();
    }
    return buffer.toByteArray();
  }

  public static @NotNull String loadTextAndClose(@NotNull InputStream stream) throws IOException {
    return loadTextAndClose(new InputStreamReader(stream, StandardCharsets.UTF_8));
  }

  public static @NotNull String loadTextAndClose(@NotNull Reader reader) throws IOException {
    try {
      return new String(adaptiveLoadText(reader));
    }
    finally {
      reader.close();
    }
  }

  public static char @NotNull [] adaptiveLoadText(@NotNull Reader reader) throws IOException {
    char[] chars = new char[4096];
    List<char[]> buffers = null;
    int count = 0;
    int total = 0;
    while (true) {
      int n = reader.read(chars, count, chars.length - count);
      if (n <= 0) break;
      count += n;
      if (total > 1024 * 1024 * 10) throw new FileTooBigException("File too big " + reader);
      total += n;
      if (count == chars.length) {
        if (buffers == null) {
          buffers = new ArrayList<>();
        }
        buffers.add(chars);
        int newLength = Math.min(1024 * 1024, chars.length * 2);
        chars = new char[newLength];
        count = 0;
      }
    }
    char[] result = new char[total];
    if (buffers != null) {
      for (char[] buffer : buffers) {
        System.arraycopy(buffer, 0, result, result.length - total, buffer.length);
        total -= buffer.length;
      }
    }
    System.arraycopy(chars, 0, result, result.length - total, total);
    return result;
  }

  public static byte @NotNull [] adaptiveLoadBytes(@NotNull InputStream stream) throws IOException {
    byte[] bytes = new byte[StreamUtil.BUFFER_SIZE];
    List<byte[]> buffers = null;
    int count = 0;
    int total = 0;
    while (true) {
      int n = stream.read(bytes, count, bytes.length - count);
      if (n <= 0) break;
      count += n;
      if (total > 1024 * 1024 * 10) throw new FileTooBigException("File too big " + stream);
      total += n;
      if (count == bytes.length) {
        if (buffers == null) {
          buffers = new ArrayList<>();
        }
        buffers.add(bytes);
        int newLength = Math.min(1024 * 1024, bytes.length * 2);
        bytes = new byte[newLength];
        count = 0;
      }
    }
    byte[] result = ArrayUtil.newByteArray(total);
    if (buffers != null) {
      for (byte[] buffer : buffers) {
        System.arraycopy(buffer, 0, result, result.length - total, buffer.length);
        total -= buffer.length;
      }
    }
    System.arraycopy(bytes, 0, result, result.length - total, total);
    return result;
  }

  public static @NotNull Future<Void> asyncDelete(@NotNull File file) {
    return asyncDelete(Collections.singleton(file));
  }

  public static @NotNull Future<Void> asyncDelete(@NotNull Collection<? extends File> files) {
    List<File> tempFiles = new ArrayList<>();
    for (File file : files) {
      File tempFile = renameToTempFileOrDelete(file);
      if (tempFile != null) {
        tempFiles.add(tempFile);
      }
    }
    return tempFiles.isEmpty() ? CompletableFuture.completedFuture(null) : AppExecutorUtil.getAppExecutorService().submit(() -> {
      Thread currentThread = Thread.currentThread();
      int priority = currentThread.getPriority();
      currentThread.setPriority(Thread.MIN_PRIORITY);
      try {
        for (File tempFile : tempFiles) {
          delete(tempFile);
        }
      }
      finally {
        currentThread.setPriority(priority);
      }
      return null;
    });
  }

  private static @Nullable File renameToTempFileOrDelete(@NotNull File file) {
    String tempDir = getTempDirectory();
    boolean isSameDrive = true;
    if (SystemInfoRt.isWindows) {
      String tempDirDrive = tempDir.substring(0, 2);
      String fileDrive = file.getAbsolutePath().substring(0, 2);
      isSameDrive = tempDirDrive.equalsIgnoreCase(fileDrive);
    }

    if (isSameDrive) {
      // the optimization is reasonable only if destination dir is located on the same drive
      String originalFileName = file.getName();
      File tempFile = getTempFile(originalFileName, tempDir);
      if (file.renameTo(tempFile)) {
        return tempFile;
      }
    }

    delete(file);

    return null;
  }

  private static @NotNull File getTempFile(@NotNull String originalFileName, @NotNull String parent) {
    int randomSuffix = (int)(System.currentTimeMillis() % 1000);
    for (int i = randomSuffix; ; i++) {
      String name = "___" + originalFileName + i + ASYNC_DELETE_EXTENSION;
      File tempFile = new File(parent, name);
      if (!tempFile.exists()) return tempFile;
    }
  }

  public static boolean delete(@NotNull File file) {
    return FileUtilRt.delete(file);
  }

  /** @deprecated use {@link NioFiles#deleteRecursively} */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static void deleteRecursively(@NotNull Path file) throws IOException {
    FileUtilRt.deleteRecursively(file, null);
  }

  /** @deprecated use {@link NioFiles#deleteRecursively} */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static void delete(@NotNull Path path) throws IOException {
    FileUtilRt.deleteRecursively(path, null);
  }

  public static boolean createParentDirs(@NotNull File file) {
    return FileUtilRt.createParentDirs(file);
  }

  public static boolean createDirectory(@NotNull File path) {
    return FileUtilRt.createDirectory(path);
  }

  public static boolean createIfDoesntExist(@NotNull File file) {
    return FileUtilRt.createIfNotExists(file);
  }

  public static boolean ensureCanCreateFile(@NotNull File file) {
    return FileUtilRt.ensureCanCreateFile(file);
  }

  public static boolean createIfNotExists(@NotNull File file) {
    return FileUtilRt.createIfNotExists(file);
  }

  public static void copy(@NotNull File fromFile, @NotNull File toFile) throws IOException {
    performCopy(fromFile, toFile, true);
  }

  public static void copyContent(@NotNull File fromFile, @NotNull File toFile) throws IOException {
    performCopy(fromFile, toFile, false);
  }

  private static void performCopy(@NotNull File _fromFile, @NotNull File _toFile, boolean syncTimestamp) throws IOException {
    if (filesEqual(_fromFile, _toFile)) return;

    Path fromFile = _fromFile.toPath(), toFile = _toFile.toPath();
    try (InputStream in = Files.newInputStream(fromFile); OutputStream out = openOutputStream(toFile)) {
      copy(in, out);
    }
    catch (IOException e) {
      throw new IOException(String.format("Couldn't copy [%s] to [%s]", fromFile, toFile), e);
    }

    if (syncTimestamp) {
      try {
        FileTime timeStamp = Files.getLastModifiedTime(fromFile);
        Files.setLastModifiedTime(toFile, timeStamp);
      }
      catch (IOException e) {
        LOG.warn("Unable to set timestamp of '" + toFile + "'");
      }
    }

    if (SystemInfoRt.isUnix) {
      Set<PosixFilePermission> exec = EnumSet.of(OWNER_EXECUTE, GROUP_EXECUTE, OTHERS_EXECUTE);
      try {
        Set<PosixFilePermission> fromSet = Files.getPosixFilePermissions(fromFile);
        if (ContainerUtil.exists(exec, fromSet::contains)) {
          Set<PosixFilePermission> toSet = Files.getPosixFilePermissions(toFile);
          for (PosixFilePermission permission : exec) {
            if (fromSet.contains(permission)) toSet.add(permission); else toSet.remove(permission);
          }
          Files.setPosixFilePermissions(toFile, toSet);
        }
      }
      catch (IOException e) {
        LOG.warn("Unable to set permissions of '" + toFile + "'");
      }
    }
  }

  private static OutputStream openOutputStream(Path file) throws IOException {
    try {
      return Files.newOutputStream(file);
    }
    catch (NoSuchFileException e) {
      Path parentDir = file.getParent();
      if (parentDir == null) {
        throw new IOException("Parent file is null for " + file, e);
      }
      Files.createDirectories(parentDir);
      return Files.newOutputStream(file);
    }
  }

  public static void copy(@NotNull InputStream inputStream, @NotNull OutputStream outputStream) throws IOException {
    FileUtilRt.copy(inputStream, outputStream);
  }

  public static void copy(@NotNull InputStream inputStream, int maxSize, @NotNull OutputStream outputStream) throws IOException {
    copy(inputStream, (long)maxSize, outputStream);
  }

  public static void copy(@NotNull InputStream inputStream, long maxSize, @NotNull OutputStream outputStream) throws IOException {
    byte[] buffer = new byte[StreamUtil.BUFFER_SIZE];
    long toRead = maxSize;
    while (toRead > 0) {
      int read = inputStream.read(buffer, 0, (int)Math.min(buffer.length, toRead));
      if (read < 0) break;
      toRead -= read;
      outputStream.write(buffer, 0, read);
    }
  }

  public static void copyFileOrDir(@NotNull File from, @NotNull File to) throws IOException {
    copyFileOrDir(from, to, from.isDirectory());
  }

  public static void copyFileOrDir(@NotNull File from, @NotNull File to, boolean isDir) throws IOException {
    if (isDir) {
      copyDir(from, to);
    }
    else {
      copy(from, to);
    }
  }

  public static void copyDir(@NotNull File fromDir, @NotNull File toDir) throws IOException {
    copyDir(fromDir, toDir, true);
  }

  /**
   * Copies content of {@code fromDir} to {@code toDir}.
   * It's equivalent to "cp --dereference -r fromDir/* toDir" unix command.
   *
   * @param fromDir source directory
   * @param toDir   destination directory
   * @throws IOException in case of any IO troubles
   */
  public static void copyDirContent(@NotNull File fromDir, @NotNull File toDir) throws IOException {
    File[] children = ObjectUtils.notNull(fromDir.listFiles(), ArrayUtilRt.EMPTY_FILE_ARRAY);
    for (File child : children) {
      copyFileOrDir(child, new File(toDir, child.getName()));
    }
  }

  public static void copyDir(@NotNull File fromDir, @NotNull File toDir, boolean copySystemFiles) throws IOException {
    copyDir(fromDir, toDir, copySystemFiles ? null : file -> !StringUtil.startsWithChar(file.getName(), '.'));
  }

  public static void copyDir(@NotNull File fromDir, @NotNull File toDir, @Nullable FileFilter filter) throws IOException {
    ensureExists(toDir);
    if (isAncestor(fromDir, toDir, true)) {
      LOG.error(fromDir.getAbsolutePath() + " is ancestor of " + toDir + ". Can't copy to itself.");
      return;
    }
    File[] files = fromDir.listFiles();
    if (files == null) throw new IOException(UtilBundle.message("exception.directory.is.invalid", fromDir.getPath()));
    if (!fromDir.canRead()) throw new IOException(UtilBundle.message("exception.directory.is.not.readable", fromDir.getPath()));
    for (File file : files) {
      if (filter != null && !filter.accept(file)) {
        continue;
      }
      if (file.isDirectory()) {
        copyDir(file, new File(toDir, file.getName()), filter);
      }
      else {
        copy(file, new File(toDir, file.getName()));
      }
    }
  }

  public static void ensureExists(@NotNull File dir) throws IOException {
    if (!dir.exists() && !dir.mkdirs()) {
      throw new IOException(UtilBundle.message("exception.directory.can.not.create", dir.getPath()));
    }
  }

  public static @NotNull @NlsSafe String getNameWithoutExtension(@NotNull File file) {
    return FileUtilRt.getNameWithoutExtension(file.getName());
  }

  public static @NotNull @NlsSafe String getNameWithoutExtension(@NotNull String name) {
    return FileUtilRt.getNameWithoutExtension(name);
  }

  public static @NotNull @NlsSafe String createSequentFileName(@NotNull File aParentFolder,
                                                               @NotNull String aFilePrefix,
                                                               @NotNull String aExtension) {
    return findSequentNonexistentFile(aParentFolder, aFilePrefix, aExtension).getName();
  }

  public static @NotNull @NlsSafe String createSequentFileName(@NotNull File aParentFolder,
                                                               @NotNull String aFilePrefix,
                                                               @NotNull String aExtension,
                                                               @NotNull Predicate<? super File> condition) {
    return findSequentFile(aParentFolder, aFilePrefix, aExtension, condition).getName();
  }

  public static @NotNull File findSequentNonexistentFile(@NotNull File parentFolder, @NotNull @NonNls String filePrefix, @NotNull String extension) {
    return findSequentFile(parentFolder, filePrefix, extension, file -> !file.exists());
  }

  /**
   * Checks sequentially files with names filePrefix.extension, filePrefix1.extension, e.t.c
   * and returns the first file which conforms to the provided condition.
   *
   * @param parentFolder the parent folder of the file to be returned
   * @param filePrefix   the prefix of the file to be returned
   * @param extension    the extension of the file to be returned
   * @param condition    the check of the file to be returned
   */
  public static @NotNull File findSequentFile(@NotNull File parentFolder,
                                     @NotNull String filePrefix,
                                     @NotNull String extension,
                                     @NotNull Predicate<? super File> condition) {
    int postfix = 0;
    String ext = extension.isEmpty() ? "" : '.' + extension;
    File candidate = new File(parentFolder, filePrefix + ext);
    while (!condition.test(candidate)) {
      postfix++;
      candidate = new File(parentFolder, filePrefix + postfix + ext);
    }
    return candidate;
  }

  /**
   * Converts {@code filePath} to file system dependent form which uses forward slashes ('/') on Linux and Mac OS, and use back slashes ('\')
   * on Windows. Such paths are shown in UI. They must be converted to {@link #toSystemIndependentName file system independent form} to
   * store in configuration files.
   */
  public static @NotNull @NlsSafe String toSystemDependentName(@NotNull String filePath) {
    return FileUtilRt.toSystemDependentName(filePath);
  }

  public static @NotNull String toSystemDependentName(@NotNull String path, char separatorChar) {
    return FileUtilRt.toSystemDependentName(path, separatorChar);
  }

  /**
   * Converts {@code filePath} to file system independent form which uses forward slashes ('/'). Such paths can be stored in internal structures
   * and configuration files. They must be converted to {@link #toSystemDependentName file system dependenct form} to show in UI.
   */
  public static @NotNull @NonNls String toSystemIndependentName(@NotNull String filePath) {
    return FileUtilRt.toSystemIndependentName(filePath);
  }

  /**
   * Converts given path to canonical representation by eliminating '.'s, traversing '..'s, and omitting duplicate separators.
   * Please note that this method is symlink-unfriendly (i.e. result of "/path/to/link/../next" most probably will differ from
   * what {@link File#getCanonicalPath()} will return) - so use with care.<br>
   * <br>
   * If the path may contain symlinks, use {@link FileUtil#toCanonicalPath(String, boolean)} instead.
   */
  @Contract("null -> null; !null->!null")
  public static String toCanonicalPath(@Nullable String path) {
    return FileUtilRt.toCanonicalPath(path, File.separatorChar, true);
  }

  public static String toCanonicalPath(@Nullable String path, char separatorChar, boolean removeLastSlash) {
    return FileUtilRt.toCanonicalPath(path, separatorChar, removeLastSlash);
  }

  /**
   * When relative ../ parts do not escape outside of symlinks, the links are not expanded.<br>
   * That is, in the best-case scenario the original non-expanded path is preserved.<br>
   * <br>
   * Otherwise, returns a fully resolved path using {@link File#getCanonicalPath()}.<br>
   * <br>
   * Consider the following case:
   * <pre>
   * root/
   *   dir1/
   *     link_to_dir1
   *   dir2/
   * </pre>
   * 'root/dir1/link_to_dir1/../dir2' should be resolved to 'root/dir2'
   */
  @Contract("null, _ -> null; !null,_->!null")
  public static String toCanonicalPath(@Nullable String path, boolean resolveSymlinksIfNecessary) {
    return toCanonicalPath(path, File.separatorChar, true, resolveSymlinksIfNecessary);
  }

  @Contract("null, _ -> null; !null,_->!null")
  public static String toCanonicalPath(@Nullable String path, char separatorChar) {
    return FileUtilRt.toCanonicalPath(path, separatorChar, true);
  }

  @Contract("null -> null; !null->!null")
  public static String toCanonicalUriPath(@Nullable String path) {
    return FileUtilRt.toCanonicalPath(path, '/', false);
  }

  private static final FileUtilRt.SymlinkResolver SYMLINK_RESOLVER = new FileUtilRt.SymlinkResolver() {
    @Override
    public @NotNull String resolveSymlinksAndCanonicalize(@NotNull String path, char separatorChar, boolean removeLastSlash) {
      try {
        return new File(path).getCanonicalPath().replace(separatorChar, '/');
      }
      catch (IOException ignore) {
        // fall back to the default behavior
        return toCanonicalPath(path, separatorChar, removeLastSlash, false);
      }
    }

    @Override
    public boolean isSymlink(@NotNull CharSequence path) {
      return Files.isSymbolicLink(Paths.get(path.toString()));
    }
  };

  @Contract("null, _, _, _ -> null; !null,_,_,_->!null")
  private static String toCanonicalPath(@Nullable String path,
                                        char separatorChar,
                                        boolean removeLastSlash,
                                        boolean resolveSymlinks) {
    FileUtilRt.SymlinkResolver symlinkResolver = resolveSymlinks ? SYMLINK_RESOLVER : null;
    return FileUtilRt.toCanonicalPath(path, separatorChar, removeLastSlash, symlinkResolver);
  }

  /**
   * converts back slashes to forward slashes
   * removes double slashes inside the path, e.g. "x/y//z" => "x/y/z"
   */
  public static @NotNull String normalize(@NotNull String path) {
    int start = 0;
    boolean separator = false;
    if (SystemInfoRt.isWindows) {
      if (path.startsWith("//")) {
        start = 2;
        separator = true;
      }
      else if (path.startsWith("\\\\")) {
        return normalizeTail(0, path, false);
      }
    }

    for (int i = start; i < path.length(); ++i) {
      char c = path.charAt(i);
      if (c == '/') {
        if (separator) {
          return normalizeTail(i, path, true);
        }
        separator = true;
      }
      else if (c == '\\') {
        return normalizeTail(i, path, separator);
      }
      else {
        separator = false;
      }
    }

    return path;
  }

  private static @NotNull String normalizeTail(int prefixEnd, @NotNull String path, boolean separator) {
    StringBuilder result = new StringBuilder(path.length());
    result.append(path, 0, prefixEnd);
    int start = prefixEnd;
    if (start == 0 && SystemInfoRt.isWindows && (path.startsWith("//") || path.startsWith("\\\\"))) {
      start = 2;
      result.append("//");
      separator = true;
    }

    for (int i = start; i < path.length(); ++i) {
      char c = path.charAt(i);
      if (c == '/' || c == '\\') {
        if (!separator) result.append('/');
        separator = true;
      }
      else {
        result.append(c);
        separator = false;
      }
    }

    return result.toString();
  }

  public static @NotNull @NlsSafe String unquote(@NotNull String urlString) {
    urlString = urlString.replace('/', File.separatorChar);
    return URLUtil.unescapePercentSequences(urlString);
  }

  public static boolean rename(@NotNull File source, @NotNull String newName) throws IOException {
    File target = new File(source.getParent(), newName);
    if (!SystemInfoRt.isFileSystemCaseSensitive && newName.equalsIgnoreCase(source.getName())) {
      File intermediate = createTempFile(source.getParentFile(), source.getName(), ".tmp", false, false);
      return source.renameTo(intermediate) && intermediate.renameTo(target);
    }
    else {
      return source.renameTo(target);
    }
  }

  public static void rename(@NotNull File source, @NotNull File target) throws IOException {
    if (source.renameTo(target)) return;
    if (!source.exists()) return;

    copy(source, target);
    delete(source);
  }

  public static boolean filesEqual(@Nullable File file1, @Nullable File file2) {
    // on MacOS java.io.File.equals() is incorrectly case-sensitive
    return pathsEqual(file1 == null ? null : file1.getPath(),
                      file2 == null ? null : file2.getPath());
  }

  public static boolean pathsEqual(@Nullable String path1, @Nullable String path2) {
    if (Strings.areSameInstance(path1, path2)) return true;
    if (path1 == null || path2 == null) return false;

    path1 = toCanonicalPath(path1);
    path2 = toCanonicalPath(path2);
    return SystemInfoRt.isFileSystemCaseSensitive ? path1.equals(path2) : path1.equalsIgnoreCase(path2);
  }

  /**
   * optimized version of pathsEqual - it only compares pure names, without file separators
   */
  public static boolean namesEqual(@Nullable String name1, @Nullable String name2) {
    if (Strings.areSameInstance(name1, name2)) return true;
    if (name1 == null || name2 == null) return false;
    return SystemInfoRt.isFileSystemCaseSensitive ? name1.equals(name2) : name1.equalsIgnoreCase(name2);
  }

  public static int compareFiles(@Nullable File file1, @Nullable File file2) {
    return comparePaths(file1 == null ? null : file1.getPath(), file2 == null ? null : file2.getPath());
  }

  public static int comparePaths(@Nullable String path1, @Nullable String path2) {
    return OSAgnosticPathUtil.COMPARATOR.compare(path1, path2);
  }

  public static int fileHashCode(@Nullable File file) {
    return FileUtilRt.pathHashCode(file == null ? null : file.getPath());
  }

  public static int pathHashCode(@Nullable String path) {
    return FileUtilRt.pathHashCode(path);
  }

  /**
   * @deprecated this method returns extension converted to lower case, this may not be correct for case-sensitive FS.
   * Use {@link FileUtilRt#getExtension(String)} instead to get the unchanged extension.
   * If you need to check whether a file has a specified extension use {@link FileUtilRt#extensionEquals(String, String)}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static @NotNull String getExtension(@NotNull String fileName) {
    return Strings.toLowerCase(FileUtilRt.getExtension(fileName));
  }

  public static @NotNull CharSequence getExtension(@NotNull CharSequence fileName) {
    return FileUtilRt.getExtension(fileName);
  }

  public static CharSequence getExtension(@NotNull CharSequence fileName, @Nullable String defaultValue) {
    return FileUtilRt.getExtension(fileName, defaultValue);
  }

  public static @NotNull @NlsSafe String resolveShortWindowsName(@NotNull String path) throws IOException {
    try {
      return SystemInfoRt.isWindows && containsWindowsShortName(path) ? Paths.get(path).toRealPath(LinkOption.NOFOLLOW_LINKS).toString() : path;
    }
    catch (InvalidPathException e) {
      throw new IOException(e);
    }
  }

  public static boolean containsWindowsShortName(@NotNull String path) {
    if (path.indexOf('~') < 0) {
      return false;
    }

    path = toSystemIndependentName(path);

    int start = 0;
    while (start < path.length()) {
      int end = path.indexOf('/', start);
      if (end < 0) end = path.length();

      // "How Windows Generates 8.3 File Names from Long File Names", https://support.microsoft.com/en-us/kb/142982
      int dot = path.lastIndexOf('.', end);
      if (dot < start) dot = end;
      if (dot - start > 2 && dot - start <= 8 && end - dot - 1 <= 3 &&
          path.charAt(dot - 2) == '~' && Character.isDigit(path.charAt(dot - 1))) {
        return true;
      }

      start = end + 1;
    }
    return false;
  }

  /**
   * @return <ul>
   * <li>null for relative or incorrect paths.</li>
   * <li>'/' on Unix.</li>
   * <li>'C:' or '//host_name/share_name' on Windows.</li></ul>
   */
  public static @Nullable String extractRootPath(@NotNull String normalizedPath) {
    if (SystemInfoRt.isWindows) {
      if (OSAgnosticPathUtil.startsWithWindowsDrive(normalizedPath)) {
        // drive letter
        return StringUtil.toUpperCase(normalizedPath.substring(0, 2));
      }
      if (normalizedPath.startsWith("//")) {
        // UNC (https://docs.microsoft.com/en-us/openspecs/windows_protocols/ms-dtyp/62e862f4-2a51-452e-8eeb-dc4ff5ee33cc)
        int p1 = normalizedPath.indexOf('/', 2);
        if (p1 > 2) {
          if (PathUtilRt.isWindowsUNCRoot(normalizedPath, p1)) {
            int p2 = normalizedPath.indexOf('/', p1 + 1);
            if (p2 > p1 + 1) return normalizedPath.substring(0, p2);
            if (p2 < 0) return normalizedPath;
          }
          // else the path doesn't look like UNC, e.g. "//.."
        }
      }
    }
    else if (StringUtil.startsWithChar(normalizedPath, '/')) {
      return "/";
    }
    int sc = normalizedPath.indexOf(URLUtil.SCHEME_SEPARATOR);
    if (sc != -1) {
      return normalizedPath.substring(0, sc + URLUtil.SCHEME_SEPARATOR.length());
    }

    return null;
  }

  public static void collectMatchedFiles(@NotNull File root, @NotNull Pattern pattern, @NotNull List<? super File> outFiles) {
    collectMatchedFiles(root, root, pattern, outFiles);
  }

  private static void collectMatchedFiles(@NotNull File absoluteRoot,
                                          @NotNull File root,
                                          @NotNull Pattern pattern,
                                          @NotNull List<? super File> files) {
    File[] dirs = root.listFiles();
    if (dirs == null) return;
    for (File dir : dirs) {
      if (dir.isFile()) {
        String relativePath = getRelativePath(absoluteRoot, dir);
        if (relativePath != null) {
          String path = toSystemIndependentName(relativePath);
          if (pattern.matcher(path).matches()) {
            files.add(dir);
          }
        }
      }
      else {
        collectMatchedFiles(absoluteRoot, dir, pattern, files);
      }
    }
  }

  @RegExp
  public static @NotNull String convertAntToRegexp(@NotNull String antPattern) {
    return convertAntToRegexp(antPattern, true);
  }

  /**
   * @param antPattern ant-style path pattern
   * @return java regexp pattern.
   * Note that no matter whether forward or backward slashes were used in the antPattern
   * the returned regexp pattern will use forward slashes ('/') as file separators.
   * Paths containing windows-style backslashes must be converted before matching against the resulting regexp
   * @see FileUtil#toSystemIndependentName
   */
  @RegExp
  public static @NotNull String convertAntToRegexp(@NotNull String antPattern, boolean ignoreStartingSlash) {
    StringBuilder builder = new StringBuilder();
    int asteriskCount = 0;
    boolean recursive = true;
    int start =
      ignoreStartingSlash && (StringUtil.startsWithChar(antPattern, '/') || StringUtil.startsWithChar(antPattern, '\\')) ? 1 : 0;
    for (int idx = start; idx < antPattern.length(); idx++) {
      char ch = antPattern.charAt(idx);

      if (ch == '*') {
        asteriskCount++;
        continue;
      }

      boolean foundRecursivePattern = recursive && asteriskCount == 2 && (ch == '/' || ch == '\\');
      boolean asterisksFound = asteriskCount > 0;

      asteriskCount = 0;
      recursive = ch == '/' || ch == '\\';

      if (foundRecursivePattern) {
        builder.append("(?:[^/]+/)*?");
        continue;
      }

      if (asterisksFound) {
        builder.append("[^/]*?");
      }

      if (ch == '(' ||
          ch == ')' ||
          ch == '[' ||
          ch == ']' ||
          ch == '^' ||
          ch == '$' ||
          ch == '.' ||
          ch == '{' ||
          ch == '}' ||
          ch == '+' ||
          ch == '|') {
        // quote regexp-specific symbols
        builder.append('\\').append(ch);
        continue;
      }
      if (ch == '?') {
        builder.append("[^/]{1}");
        continue;
      }
      if (ch == '\\') {
        builder.append('/');
        continue;
      }
      builder.append(ch);
    }

    // handle ant shorthand: my_package/test/ is interpreted as if it were my_package/test/**
    boolean isTrailingSlash = builder.length() > 0 && builder.charAt(builder.length() - 1) == '/';
    if (asteriskCount == 0 && isTrailingSlash || recursive && asteriskCount == 2) {
      if (isTrailingSlash) {
        builder.setLength(builder.length() - 1);
      }
      if (builder.length() == 0) {
        builder.append(".*");
      }
      else {
        builder.append("(?:$|/.+)");
      }
    }
    else if (asteriskCount > 0) {
      builder.append("[^/]*?");
    }
    return builder.toString();
  }

  public static boolean moveDirWithContent(@NotNull File fromDir, @NotNull File toDir) {
    if (!toDir.exists()) return fromDir.renameTo(toDir);

    File[] files = fromDir.listFiles();
    if (files == null) return false;

    boolean success = true;

    for (File fromFile : files) {
      File toFile = new File(toDir, fromFile.getName());
      success = success && fromFile.renameTo(toFile);
    }
    //noinspection ResultOfMethodCallIgnored
    fromDir.delete();

    return success;
  }

  public static @NotNull String sanitizeFileName(@NotNull String name) {
    return sanitizeFileName(name, true);
  }

  public static @NotNull String sanitizeFileName(@NotNull String name, boolean strict) {
    return sanitizeFileName(name, strict, "_");
  }

  public static @NotNull String sanitizeFileName(@NotNull String name, boolean strict, @NotNull String replacement) {
    StringBuilder result = null;

    int last = 0;
    int length = name.length();
    for (int i = 0; i < length; i++) {
      char c = name.charAt(i);
      boolean appendReplacement = true;
      if (c > 0 && c < 255) {
        if (strict
            ? Character.isLetterOrDigit(c) || c == '_'
            : Character.isJavaIdentifierPart(c) || c == ' ' || c == '@' || c == '-') {
          continue;
        }
      }
      else {
        appendReplacement = false;
      }

      if (result == null) {
        result = new StringBuilder();
      }
      if (last < i) {
        result.append(name, last, i);
      }
      if (appendReplacement) {
        result.append(replacement);
      }
      last = i + 1;
    }

    if (result == null) {
      return name;
    }

    if (last < length) {
      result.append(name, last, length);
    }

    return result.toString();
  }

  public static boolean canExecute(@NotNull File file) {
    return file.canExecute();
  }

  /** @deprecated use {@link NioFiles#isWritable} */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static boolean canWrite(@NotNull String path) {
    return NioFiles.isWritable(Paths.get(path));
  }

  public static void appendToFile(@NotNull File file, @NotNull String text) throws IOException {
    writeToFile(file, text.getBytes(StandardCharsets.UTF_8), true);
  }

  public static void writeToFile(@NotNull File file, byte @NotNull [] content) throws IOException {
    writeToFile(file, content, false);
  }

  public static void writeToFile(@NotNull File file, @NotNull String text) throws IOException {
    writeToFile(file, text, false);
  }

  public static void writeToFile(@NotNull File file, @NotNull String text, @NotNull Charset charset) throws IOException {
    writeToFile(file, text.getBytes(charset));
  }

  public static void writeToFile(@NotNull File file, @NotNull String text, boolean append) throws IOException {
    writeToFile(file, text.getBytes(StandardCharsets.UTF_8), append);
  }

  public static void writeToFile(@NotNull File file, byte @NotNull [] content, int off, int len) throws IOException {
    writeToFile(file, content, off, len, false);
  }

  public static void writeToFile(@NotNull File file, byte @NotNull [] content, boolean append) throws IOException {
    writeToFile(file, content, 0, content.length, append);
  }

  private static void writeToFile(@NotNull File file, byte @NotNull [] content, int off, int len, boolean append) throws IOException {
    createParentDirs(file);

    try (OutputStream stream = new FileOutputStream(file, append)) {
      stream.write(content, off, len);
    }
  }

  private static final class Lazy {
    private static final JBTreeTraverser<File> FILE_TRAVERSER = JBTreeTraverser.from(
      (Function<File, Iterable<File>>)file -> file == null ? Collections.emptySet() : JBIterable.of(file.listFiles()));
  }

  public static @NotNull JBTreeTraverser<File> fileTraverser(@Nullable File root) {
    return Lazy.FILE_TRAVERSER.withRoot(root);
  }

  public static boolean processFilesRecursively(@NotNull File root, @NotNull Processor<? super File> processor) {
    return fileTraverser(root).bfsTraversal().processEach(processor);
  }

  /**
   * @deprecated use  {@link #fileTraverser(File)}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static boolean processFilesRecursively(@NotNull File root,
                                                @NotNull Processor<? super File> processor,
                                                @Nullable Processor<? super File> directoryFilter) {
    LinkedList<File> queue = new LinkedList<>();
    queue.add(root);
    while (!queue.isEmpty()) {
      File file = queue.removeFirst();
      if (!processor.process(file)) return false;
      if (directoryFilter != null && (!file.isDirectory() || !directoryFilter.process(file))) continue;

      File[] children = file.listFiles();
      if (children != null) {
        ContainerUtil.addAll(queue, children);
      }
    }
    return true;
  }

  public static @Nullable File findFirstThatExist(String @NotNull ... paths) {
    for (String path : paths) {
      if (!Strings.isEmptyOrSpaces(path)) {
        File file = new File(toSystemDependentName(path));
        if (file.exists()) return file;
      }
    }

    return null;
  }

  public static @NotNull List<File> findFilesByMask(@NotNull Pattern pattern, @NotNull File dir) {
    List<File> found = new ArrayList<>();
    File[] files = dir.listFiles();
    if (files != null) {
      for (File file : files) {
        if (file.isDirectory()) {
          found.addAll(findFilesByMask(pattern, file));
        }
        else if (pattern.matcher(file.getName()).matches()) {
          found.add(file);
        }
      }
    }
    return found;
  }

  public static @NotNull List<File> findFilesOrDirsByMask(@NotNull Pattern pattern, @NotNull File dir) {
    List<File> found = new ArrayList<>();
    File[] files = dir.listFiles();
    if (files != null) {
      for (File file : files) {
        if (pattern.matcher(file.getName()).matches()) {
          found.add(file);
        }
        if (file.isDirectory()) {
          found.addAll(findFilesOrDirsByMask(pattern, file));
        }
      }
    }
    return found;
  }

  /**
   * Returns empty string for empty path.
   * First checks whether provided path is a path of a file with sought-for name.
   * Unless found, checks if provided file was a directory. In this case checks existence
   * of child files with given names in order "as provided". Finally checks filename among
   * brother-files of provided. Returns null if nothing found.
   *
   * @return path of the first of found files or empty string or null.
   */
  public static @Nullable @NlsSafe String findFileInProvidedPath(@NotNull String providedPath, @NotNull String @NotNull ... fileNames) {
    if (Strings.isEmpty(providedPath)) {
      return "";
    }

    File providedFile = new File(providedPath);
    if (providedFile.exists() && ArrayUtil.indexOf(fileNames, providedFile.getName()) >= 0) {
      return toSystemDependentName(providedFile.getPath());
    }

    if (providedFile.isDirectory()) {  //user chose folder with file
      for (String fileName : fileNames) {
        File file = new File(providedFile, fileName);
        if (fileName.equals(file.getName()) && file.exists()) {
          return toSystemDependentName(file.getPath());
        }
      }
    }

    providedFile = providedFile.getParentFile();  //users chose wrong file in same directory
    if (providedFile != null && providedFile.exists()) {
      for (String fileName : fileNames) {
        File file = new File(providedFile, fileName);
        if (fileName.equals(file.getName()) && file.exists()) {
          return toSystemDependentName(file.getPath());
        }
      }
    }

    return null;
  }

  /** @deprecated does not support UNC paths; consider using {@link OSAgnosticPathUtil} or {@link java.nio.file NIO2} instead */
  @Deprecated
  public static boolean isAbsolutePlatformIndependent(@NotNull String path) {
    return isUnixAbsolutePath(path) || isWindowsAbsolutePath(path);
  }

  /** @deprecated ambiguous w.r.t. to normalized UNC paths; consider using {@link OSAgnosticPathUtil} or {@link java.nio.file NIO2} instead */
  @ApiStatus.ScheduledForRemoval
  @Deprecated
  public static boolean isUnixAbsolutePath(@NotNull String path) {
    return path.startsWith("/");
  }

  /** @deprecated does not support UNC paths; consider using {@link OSAgnosticPathUtil} or {@link java.nio.file NIO2} instead */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static boolean isWindowsAbsolutePath(@NotNull String path) {
    return path.length() <= 2 && OSAgnosticPathUtil.startsWithWindowsDrive(path)
           || OSAgnosticPathUtil.isAbsoluteDosPath(path);
  }

  @Contract("null -> null; !null -> !null")
  public static @NlsSafe String getLocationRelativeToUserHome(@Nullable String path) {
    return getLocationRelativeToUserHome(path, true);
  }

  @Contract("null,_ -> null; !null,_ -> !null")
  public static @NlsSafe String getLocationRelativeToUserHome(@Nullable String path, boolean unixOnly) {
    if (path == null) return null;

    if (SystemInfoRt.isUnix || !unixOnly) {
      File projectDir = new File(path);
      File userHomeDir = new File(SystemProperties.getUserHome());
      if (isAncestor(userHomeDir, projectDir, true)) {
        return '~' + File.separator + getRelativePath(userHomeDir, projectDir);
      }
    }

    return path;
  }

  @Contract(pure = true)
  public static @NotNull String expandUserHome(@NotNull String path) {
    if (path.equals("~")) {
      return SystemProperties.getUserHome();
    }
    else if (path.startsWith("~/") || path.startsWith("~\\")) {
      return SystemProperties.getUserHome() + path.substring(1);
    }
    else {
      return path;
    }
  }

  public static File @NotNull [] notNullize(File @Nullable [] files) {
    return notNullize(files, ArrayUtilRt.EMPTY_FILE_ARRAY);
  }

  public static File @NotNull [] notNullize(File @Nullable [] files, File @NotNull [] defaultFiles) {
    return files == null ? defaultFiles : files;
  }

  public static boolean isHashBangLine(@Nullable CharSequence firstCharsIfText, @NotNull String marker) {
    if (firstCharsIfText == null) {
      return false;
    }
    if (!StringUtil.startsWith(firstCharsIfText, "#!")) {
      return false;
    }

    int lineBreak = Strings.indexOf(firstCharsIfText, '\n', 2);
    return lineBreak >= 0 && Strings.indexOf(firstCharsIfText, marker, 2, lineBreak) != -1;
  }

  public static @NotNull File createTempDirectory(@NotNull @NonNls String prefix, @Nullable @NonNls String suffix) throws IOException {
    return FileUtilRt.createTempDirectory(prefix, suffix);
  }

  public static @NotNull File createTempDirectory(@NotNull @NonNls String prefix, @Nullable @NonNls String suffix, boolean deleteOnExit)
    throws IOException {
    return FileUtilRt.createTempDirectory(prefix, suffix, deleteOnExit);
  }

  public static @NotNull File createTempDirectory(@NotNull File dir, @NotNull @NonNls String prefix, @Nullable @NonNls String suffix)
    throws IOException {
    return FileUtilRt.createTempDirectory(dir, prefix, suffix);
  }

  public static @NotNull File createTempDirectory(@NotNull File dir,
                                                  @NotNull @NonNls String prefix,
                                                  @Nullable @NonNls String suffix,
                                                  boolean deleteOnExit) throws IOException {
    return FileUtilRt.createTempDirectory(dir, prefix, suffix, deleteOnExit);
  }

  public static @NotNull File createTempFile(@NotNull @NonNls String prefix, @Nullable @NonNls String suffix) throws IOException {
    return FileUtilRt.createTempFile(prefix, suffix);
  }

  public static @NotNull File createTempFile(@NotNull @NonNls String prefix, @Nullable @NonNls String suffix, boolean deleteOnExit)
    throws IOException {
    return FileUtilRt.createTempFile(prefix, suffix, deleteOnExit);
  }

  public static @NotNull File createTempFile(@NotNull File dir, @NotNull @NonNls String prefix, @Nullable @NonNls String suffix) throws IOException {
    return FileUtilRt.createTempFile(dir, prefix, suffix);
  }

  public static @NotNull File createTempFile(@NotNull File dir,
                                             @NotNull @NonNls String prefix,
                                             @Nullable @NonNls String suffix,
                                             boolean create) throws IOException {
    return FileUtilRt.createTempFile(dir, prefix, suffix, create);
  }

  public static @NotNull File createTempFile(@NotNull File dir,
                                             @NotNull @NonNls String prefix,
                                             @Nullable @NonNls String suffix,
                                             boolean create,
                                             boolean deleteOnExit) throws IOException {
    return FileUtilRt.createTempFile(dir, prefix, suffix, create, deleteOnExit);
  }

  public static @NotNull @NlsSafe String getTempDirectory() {
    return FileUtilRt.getTempDirectory();
  }

  @TestOnly
  public static void resetCanonicalTempPathCache(@NotNull String tempPath) {
    FileUtilRt.resetCanonicalTempPathCache(tempPath);
  }

  public static @NotNull File generateRandomTemporaryPath() throws IOException {
    return FileUtilRt.generateRandomTemporaryPath();
  }

  public static @NotNull File generateRandomTemporaryPath(@NotNull String prefix, @NotNull String suffix) throws IOException {
    return FileUtilRt.generateRandomTemporaryPath(prefix, suffix);
  }

  public static void setExecutable(@NotNull File file) throws IOException {
    NioFiles.setExecutable(file.toPath());
  }

  public static @Nullable String loadFileOrNull(@NotNull String path) {
    return loadFileOrNull(new File(path));
  }

  public static @Nullable String loadFileOrNull(@NotNull File file) {
    try {
      return loadFile(file);
    }
    catch (IOException e) {
      return null;
    }
  }

  public static @NotNull String loadFile(@NotNull File file) throws IOException {
    return FileUtilRt.loadFile(file);
  }

  public static @NotNull String loadFile(@NotNull File file, boolean convertLineSeparators) throws IOException {
    return FileUtilRt.loadFile(file, convertLineSeparators);
  }

  public static @NotNull String loadFile(@NotNull File file, @Nullable String encoding) throws IOException {
    return FileUtilRt.loadFile(file, encoding);
  }

  public static @NotNull String loadFile(@NotNull File file, @NotNull Charset encoding) throws IOException {
    return String.valueOf(FileUtilRt.loadFileText(file, encoding));
  }

  public static @NotNull String loadFile(@NotNull File file, @Nullable String encoding, boolean convertLineSeparators) throws IOException {
    return FileUtilRt.loadFile(file, encoding, convertLineSeparators);
  }

  public static char @NotNull [] loadFileText(@NotNull File file) throws IOException {
    return FileUtilRt.loadFileText(file);
  }

  public static char @NotNull [] loadFileText(@NotNull File file, @NotNull Charset encoding) throws IOException {
    return FileUtilRt.loadFileText(file, encoding);
  }

  public static char[] loadFileText(@NotNull File file, @Nullable String encoding) throws IOException {
    return FileUtilRt.loadFileText(file, encoding);
  }

  public static char @NotNull [] loadText(@NotNull Reader reader, int length) throws IOException {
    return FileUtilRt.loadText(reader, length);
  }

  public static @NotNull List<String> loadLines(@NotNull File file) throws IOException {
    return FileUtilRt.loadLines(file);
  }

  public static @NotNull List<String> loadLines(@NotNull File file, @Nullable String encoding) throws IOException {
    return FileUtilRt.loadLines(file, encoding);
  }

  public static @NotNull List<String> loadLines(@NotNull String path) throws IOException {
    return FileUtilRt.loadLines(path);
  }

  public static @NotNull List<String> loadLines(@NotNull String path, @Nullable String encoding) throws IOException {
    return FileUtilRt.loadLines(path, encoding);
  }

  public static @NotNull List<String> loadLines(@NotNull BufferedReader reader) throws IOException {
    return FileUtilRt.loadLines(reader);
  }

  public static byte @NotNull [] loadBytes(@NotNull InputStream stream) throws IOException {
    return FileUtilRt.loadBytes(stream);
  }

  public static byte @NotNull [] loadBytes(@NotNull InputStream stream, int length) throws IOException {
    return FileUtilRt.loadBytes(stream, length);
  }

  public static @NotNull List<String> splitPath(@NotNull String path) {
    return FileUtilRt.splitPath(path, File.separatorChar);
  }

  public static @NotNull List<String> splitPath(@NotNull String path, char separatorChar) {
    return FileUtilRt.splitPath(path, separatorChar);
  }

  public static boolean visitFiles(@NotNull File root, @NotNull Processor<? super File> processor) {
    if (!processor.process(root)) {
      return false;
    }

    File[] children = root.listFiles();
    if (children != null) {
      for (File child : children) {
        if (!visitFiles(child, processor)) {
          return false;
        }
      }
    }

    return true;
  }

  public static boolean deleteWithRenamingIfExists(@NotNull Path file) {
    return Files.exists(file) && deleteWithRenaming(file);
  }

  public static boolean deleteWithRenaming(@NotNull Path file) {
    return deleteWithRenaming(file.toFile());
  }

  public static boolean deleteWithRenaming(@NotNull File file) {
    File tempFileNameForDeletion = findSequentNonexistentFile(file.getParentFile(), file.getName(), "");
    boolean success = file.renameTo(tempFileNameForDeletion);
    return delete(success ? tempFileNameForDeletion : file);
  }

  public static @NotNull String getUrl(@NotNull File file) {
    try {
      return file.toURI().toURL().toExternalForm();
    }
    catch (MalformedURLException e) {
      return "file://" + file.getAbsolutePath();
    }
  }

  /**
   * Energy-efficient variant of {@link File#toURI()}. Unlike the latter, doesn't check whether a given file is a directory,
   * so URIs never have a trailing slash (but are nevertheless compatible with {@link File#File(URI)}).
   */
  public static @NotNull URI fileToUri(@NotNull File file) {
    return FileUtilRt.fileToUri(file);
  }

  public static boolean extensionEquals(@NotNull @NonNls String filePath, @NotNull @NonNls String extension) {
    return FileUtilRt.extensionEquals(filePath, extension);
  }

  @SuppressWarnings("unused")
  public static boolean isJarOrZip(@NotNull File file) {
    return FileUtilRt.isJarOrZip(file, true);
  }

  /**
   * @deprecated use {@link com.intellij.util.containers.CollectionFactory#createFilePathSet()}, or other createFilePath*() methods from there
   * Please don't remove -- KTIJ-29067 JPS: "NoSuchFieldError: FILE_HASHING_STRATEGY" caused by Maven project with 1.6
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  @ApiStatus.Internal
  @SuppressWarnings("all")
  public final static TObjectHashingStrategy<File> FILE_HASHING_STRATEGY;

  static {
      Object FILE_HASHING_STRATEGY_temp = null;
      try {
        // If there is no trove4j library in the classpath, we will fail with ClassNotFoundException
        // and FILE_HASHING_STRATEGY will not be initialized
        Class<?> clazz = Class.forName("gnu.trove.TObjectHashingStrategy");

        FILE_HASHING_STRATEGY_temp = new TObjectHashingStrategy<File>() {
          @Override
          public int computeHashCode(File object) {
            return fileHashCode(object);
          }

          @Override
          public boolean equals(File o1, File o2) {
            return filesEqual(o1, o2);
          }
        };
      }
      catch (ClassNotFoundException e) {
        // Do nothing
      } finally {
        //We cannot use TObjectHashingStrategy explicitly to declare the variable, as NoClassDefFoundError could be thrown 
        // if trove4j is not available
        //noinspection CastCanBeRemovedNarrowingVariableType,unchecked
        FILE_HASHING_STRATEGY = (TObjectHashingStrategy<File>)FILE_HASHING_STRATEGY_temp;
      }
  }
}
