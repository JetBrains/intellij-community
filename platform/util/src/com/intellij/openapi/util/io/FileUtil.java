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

import com.intellij.CommonBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.util.io.URLUtil;
import com.intellij.util.text.CaseInsensitiveStringHashingStrategy;
import gnu.trove.TObjectHashingStrategy;
import org.intellij.lang.annotations.RegExp;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.*;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

@SuppressWarnings({"UtilityClassWithoutPrivateConstructor", "MethodOverridesStaticMethodOfSuperclass"})
public class FileUtil extends FileUtilRt {

  @NonNls public static final String ASYNC_DELETE_EXTENSION = ".__del__";

  public static final int REGEX_PATTERN_FLAGS = SystemInfo.isFileSystemCaseSensitive ? 0 : Pattern.CASE_INSENSITIVE;

  @SuppressWarnings({"unchecked"})
  public static final TObjectHashingStrategy<String> PATH_HASHING_STRATEGY =
    SystemInfo.isFileSystemCaseSensitive ? TObjectHashingStrategy.CANONICAL : CaseInsensitiveStringHashingStrategy.INSTANCE;

  @SuppressWarnings({"unchecked"})
  public static final TObjectHashingStrategy<File> FILE_HASHING_STRATEGY =
    SystemInfo.isFileSystemCaseSensitive ? TObjectHashingStrategy.CANONICAL : new TObjectHashingStrategy<File>() {
      @Override
      public int computeHashCode(File object) {
        return fileHashCode(object);
      }

      @Override
      public boolean equals(File o1, File o2) {
        return filesEqual(o1, o2);
      }
    };

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.io.FileUtil");

  @NotNull
  public static String join(@NotNull final String... parts) {
    return StringUtil.join(parts, File.separator);
  }

  @Nullable
  public static String getRelativePath(File base, File file) {
    return FileUtilRt.getRelativePath(base, file);
  }

  @Nullable
  public static String getRelativePath(@NotNull String basePath, @NotNull String filePath, final char separator) {
    return FileUtilRt.getRelativePath(basePath, filePath, separator);
  }

  @Nullable
  public static String getRelativePath(@NotNull String basePath,
                                       @NotNull String filePath,
                                       final char separator,
                                       final boolean caseSensitive) {
    return FileUtilRt.getRelativePath(basePath, filePath, separator, caseSensitive);
  }

  public static boolean isAbsolute(@NotNull String path) {
    return new File(path).isAbsolute();
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
    return ! ThreeState.NO.equals(isAncestorThreeState(ancestor, file, strict));
  }

  public static ThreeState isAncestorThreeState(@NotNull String ancestor, @NotNull String file, boolean strict) {
    String ancestorPath = toCanonicalPath(ancestor);
    String filePath = toCanonicalPath(file);

    if (ancestorPath == null || filePath == null) {
      return ThreeState.NO;
    }

    return startsWith(filePath, ancestorPath, strict, SystemInfo.isFileSystemCaseSensitive, true);
  }

  public static boolean startsWith(@NotNull String path, @NotNull String start) {
    return ! ThreeState.NO.equals(startsWith(path, start, false, SystemInfo.isFileSystemCaseSensitive, false));
  }

  public static boolean startsWith(@NotNull String path, @NotNull String start, boolean caseSensitive) {
    return ! ThreeState.NO.equals(startsWith(path, start, false, caseSensitive, false));
  }

  /**
   * @return ThreeState.YES if same path or immediate parent
   */
  private static ThreeState startsWith(@NotNull String path, @NotNull String start, boolean strict, boolean caseSensitive,
                                       boolean checkImmediateParent) {
    final int length1 = path.length();
    final int length2 = start.length();
    if (length2 == 0) return length1 == 0 ? ThreeState.YES : ThreeState.UNSURE;
    if (length2 > length1) return ThreeState.NO;
    if (!path.regionMatches(!caseSensitive, 0, start, 0, length2)) return ThreeState.NO;
    if (length1 == length2) {
      return strict ? ThreeState.NO : ThreeState.YES;
    }
    char last2 = start.charAt(length2 - 1);
    char next1;
    int slashOrSeparatorIdx = length2;
    if (last2 == '/' || last2 == File.separatorChar) {
      slashOrSeparatorIdx = length2 - 1;
    }
    next1 = path.charAt(slashOrSeparatorIdx);
    if (next1 == '/' || next1 == File.separatorChar) {
      if (! checkImmediateParent) return ThreeState.YES;

      if (slashOrSeparatorIdx == length1 - 1) return ThreeState.YES;
      int idxNext = path.indexOf(next1, slashOrSeparatorIdx + 1);
      idxNext = idxNext == -1 ? path.indexOf(next1 == '/' ? '\\' : '/', slashOrSeparatorIdx + 1) : idxNext;
      return idxNext == -1 ? ThreeState.YES : ThreeState.UNSURE;
    }
    else {
      return ThreeState.NO;
    }
  }

  /**
   * @param removeProcessor parent, child
   */
  public static <T> Collection<T> removeAncestors(final Collection<T> files,
                                                  final Convertor<T, String> convertor,
                                                  final PairProcessor<T, T> removeProcessor) {
    if (files.isEmpty()) return files;
    final TreeMap<String, T> paths = new TreeMap<String, T>();
    for (T file : files) {
      final String path = convertor.convert(file);
      assert path != null;
      final String canonicalPath = toCanonicalPath(path);
      paths.put(canonicalPath, file);
    }
    final List<Map.Entry<String, T>> ordered = new ArrayList<Map.Entry<String, T>>(paths.entrySet());
    final List<T> result = new ArrayList<T>(ordered.size());
    result.add(ordered.get(0).getValue());
    for (int i = 1; i < ordered.size(); i++) {
      final Map.Entry<String, T> entry = ordered.get(i);
      final String child = entry.getKey();
      boolean parentNotFound = true;
      for (int j = i - 1; j >= 0; j--) {
        // possible parents
        final String parent = ordered.get(j).getKey();
        if (parent == null) continue;
        if (startsWith(child, parent) && removeProcessor.process(ordered.get(j).getValue(), entry.getValue())) {
          parentNotFound = false;
          break;
        }
      }
      if (parentNotFound) {
        result.add(entry.getValue());
      }
    }
    return result;
  }

  @Nullable
  public static File getParentFile(@NotNull File file) {
    return FileUtilRt.getParentFile(file);
  }

  @NotNull
  public static byte[] loadFileBytes(@NotNull File file) throws IOException {
    byte[] bytes;
    final InputStream stream = new FileInputStream(file);
    try {
      final long len = file.length();
      if (len < 0) {
        throw new IOException("File length reported negative, probably doesn't exist");
      }

      if (isTooLarge(len)) {
        throw new FileTooBigException("Attempt to load '" + file + "' in memory buffer, file length is " + len + " bytes.");
      }

      bytes = loadBytes(stream, (int)len);
    }
    finally {
      stream.close();
    }
    return bytes;
  }

  public static boolean processFirstBytes(@NotNull InputStream stream, int length, @NotNull Processor<ByteSequence> processor) throws IOException {
    final byte[] bytes = BUFFER.get();
    assert bytes.length >= length : "Cannot process more than " + bytes.length + " in one call, requested:" + length;

    int n = stream.read(bytes, 0, length);
    if (n <= 0) return false;

    return processor.process(new ByteSequence(bytes, 0, n));
  }

  @NotNull
  public static byte[] loadFirst(@NotNull InputStream stream, int maxLength) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    final byte[] bytes = BUFFER.get();
    while (maxLength > 0) {
      int n = stream.read(bytes, 0, Math.min(maxLength, bytes.length));
      if (n <= 0) break;
      buffer.write(bytes, 0, n);
      maxLength -= n;
    }
    buffer.close();
    return buffer.toByteArray();
  }

  @NotNull
  public static String loadTextAndClose(@NotNull InputStream stream) throws IOException {
    //noinspection IOResourceOpenedButNotSafelyClosed
    return loadTextAndClose(new InputStreamReader(stream));
  }

  @NotNull
  public static String loadTextAndClose(@NotNull Reader reader) throws IOException {
    try {
      return new String(adaptiveLoadText(reader));
    }
    finally {
      reader.close();
    }
  }

  @NotNull
  public static char[] adaptiveLoadText(@NotNull Reader reader) throws IOException {
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
          buffers = new ArrayList<char[]>();
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

  @NotNull
  public static byte[] adaptiveLoadBytes(@NotNull InputStream stream) throws IOException {
    byte[] bytes = new byte[4096];
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
          buffers = new ArrayList<byte[]>();
        }
        buffers.add(bytes);
        int newLength = Math.min(1024 * 1024, bytes.length * 2);
        bytes = new byte[newLength];
        count = 0;
      }
    }
    byte[] result = new byte[total];
    if (buffers != null) {
      for (byte[] buffer : buffers) {
        System.arraycopy(buffer, 0, result, result.length - total, buffer.length);
        total -= buffer.length;
      }
    }
    System.arraycopy(bytes, 0, result, result.length - total, total);
    return result;
  }

  @NotNull
  public static Future<Void> asyncDelete(@NotNull File file) {
    return asyncDelete(Collections.singleton(file));
  }

  @NotNull
  public static Future<Void> asyncDelete(@NotNull Collection<File> files) {
    List<File> tempFiles = new ArrayList<File>();
    for (File file : files) {
      final File tempFile = renameToTempFileOrDelete(file);
      if (tempFile != null) {
        tempFiles.add(tempFile);
      }
    }
    if (!tempFiles.isEmpty()) {
      return startDeletionThread(tempFiles.toArray(new File[tempFiles.size()]));
    }
    return new CompletedFuture<Void>();
  }

  private static Future<Void> startDeletionThread(@NotNull final File... tempFiles) {
    final RunnableFuture<Void> deleteFilesTask = new FutureTask<Void>(new Runnable() {
      public void run() {
        final Thread currentThread = Thread.currentThread();
        final int priority = currentThread.getPriority();
        currentThread.setPriority(Thread.MIN_PRIORITY);
        try {
          for (File tempFile : tempFiles) {
            delete(tempFile);
          }
        }
        finally {
          currentThread.setPriority(priority);
        }
      }
    }, null);

    try {
      // attempt to execute on pooled thread
      final Class<?> aClass = Class.forName("com.intellij.openapi.application.ApplicationManager");
      final Method getApplicationMethod = aClass.getMethod("getApplication");
      final Object application = getApplicationMethod.invoke(null);
      final Method executeOnPooledThreadMethod = application.getClass().getMethod("executeOnPooledThread", Runnable.class);
      executeOnPooledThreadMethod.invoke(application, deleteFilesTask);
    }
    catch (Exception e) {
      new Thread(deleteFilesTask, "File deletion thread").start();
    }
    return deleteFilesTask;
  }

  @Nullable
  private static File renameToTempFileOrDelete(@NotNull File file) {
    final File tempDir = new File(getTempDirectory());
    boolean isSameDrive = true;
    if (SystemInfo.isWindows) {
      String tempDirDrive = tempDir.getAbsolutePath().substring(0, 2);
      String fileDrive = file.getAbsolutePath().substring(0, 2);
      isSameDrive = tempDirDrive.equalsIgnoreCase(fileDrive);
    }

    if (isSameDrive) {
      // the optimization is reasonable only if destination dir is located on the same drive
      final String originalFileName = file.getName();
      File tempFile = getTempFile(originalFileName, tempDir);
      if (file.renameTo(tempFile)) {
        return tempFile;
      }
    }

    delete(file);

    return null;
  }

  private static File getTempFile(@NotNull String originalFileName, @NotNull File parent) {
    int randomSuffix = (int)(System.currentTimeMillis() % 1000);
    for (int i = randomSuffix; ; i++) {
      @NonNls String name = "___" + originalFileName + i + ASYNC_DELETE_EXTENSION;
      File tempFile = new File(parent, name);
      if (!tempFile.exists()) return tempFile;
    }
  }

  public static boolean delete(@NotNull File file) {
    FileAttributes attributes = FileSystemUtil.getAttributes(file);
    if (attributes == null) return true;

    if (attributes.isDirectory() && !attributes.isSymLink()) {
      if (!deleteChildren(file)) return false;
    }

    return deleteFile(file);
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

  public static void copy(@NotNull File fromFile, @NotNull File toFile) throws IOException {
    performCopy(fromFile, toFile, true);
  }

  public static void copyContent(@NotNull File fromFile, @NotNull File toFile) throws IOException {
    performCopy(fromFile, toFile, false);
  }

  private static void performCopy(@NotNull File fromFile, @NotNull File toFile, final boolean syncTimestamp) throws IOException {
    final FileOutputStream fos;
    try {
      fos = openOutputStream(toFile);
    }
    catch (IOException e) {
      if (SystemInfo.isWindows && e.getMessage() != null && e.getMessage().contains("denied") &&
          WinUACTemporaryFix.nativeCopy(fromFile, toFile, syncTimestamp)) {
        return;
      }
      throw e;
    }

    try {
      final FileInputStream fis = new FileInputStream(fromFile);
      try {
        copy(fis, fos);
      }
      finally {
        fis.close();
      }
    }
    finally {
      fos.close();
    }

    if (syncTimestamp) {
      final long timeStamp = fromFile.lastModified();
      if (timeStamp < 0) {
        LOG.warn("Invalid timestamp " + timeStamp + " of '" + fromFile + "'");
      }
      else if (!toFile.setLastModified(timeStamp)) {
        LOG.warn("Unable to set timestamp " + timeStamp + " to '" + toFile + "'");
      }
    }

    if (SystemInfo.isUnix && fromFile.canExecute()) {
      final int oldPermissions = FileSystemUtil.getPermissions(fromFile);
      final int newPermissions = FileSystemUtil.getPermissions(toFile);
      if (oldPermissions != -1 && newPermissions != -1) {
        FileSystemUtil.setPermissions(toFile, oldPermissions | newPermissions);
      }
    }
  }

  private static FileOutputStream openOutputStream(@NotNull final File file) throws IOException {
    try {
      return new FileOutputStream(file);
    }
    catch (FileNotFoundException e) {
      final File parentFile = file.getParentFile();
      if (parentFile == null) {
        throw new IOException("Parent file is null for " + file.getPath(), e);
      }
      createParentDirs(file);
      return new FileOutputStream(file);
    }
  }

  public static void copy(@NotNull InputStream inputStream, @NotNull OutputStream outputStream) throws IOException {
    FileUtilRt.copy(inputStream, outputStream);
  }

  public static void copy(@NotNull InputStream inputStream, int maxSize, @NotNull OutputStream outputStream) throws IOException {
    final byte[] buffer = BUFFER.get();
    int toRead = maxSize;
    while (toRead > 0) {
      int read = inputStream.read(buffer, 0, Math.min(buffer.length, toRead));
      if (read < 0) break;
      toRead -= read;
      outputStream.write(buffer, 0, read);
    }
  }

  public static void copyDir(@NotNull File fromDir, @NotNull File toDir) throws IOException {
    copyDir(fromDir, toDir, true);
  }

  /**
   * Copies content of {@code fromDir} to {@code toDir}.
   * It's equivalent to "cp -r fromDir/* toDir" unix command.
   *
   * @param fromDir source directory
   * @param toDir   destination directory
   * @throws IOException in case of any IO troubles
   */
  public static void copyDirContent(@NotNull File fromDir, @NotNull File toDir) throws IOException {
    File[] children = ObjectUtils.notNull(fromDir.listFiles(), ArrayUtil.EMPTY_FILE_ARRAY);
    for (File child : children) {
      File target = new File(toDir, child.getName());
      if (child.isFile()) {
        copy(child, target);
      } else {
        copyDir(child, target, true);
      }
    }
  }

  public static void copyDir(@NotNull File fromDir, @NotNull File toDir, boolean copySystemFiles) throws IOException {
    copyDir(fromDir, toDir, copySystemFiles ? null : new FileFilter() {
      public boolean accept(File file) {
        return !StringUtil.startsWithChar(file.getName(), '.');
      }
    });
  }

  public static void copyDir(@NotNull File fromDir, @NotNull File toDir, @Nullable final FileFilter filter) throws IOException {
    ensureExists(toDir);
    if (isAncestor(fromDir, toDir, true)) {
      LOG.error(fromDir.getAbsolutePath() + " is ancestor of " + toDir + ". Can't copy to itself.");
      return;
    }
    File[] files = fromDir.listFiles();
    if (files == null) throw new IOException(CommonBundle.message("exception.directory.is.invalid", fromDir.getPath()));
    if (!fromDir.canRead()) throw new IOException(CommonBundle.message("exception.directory.is.not.readable", fromDir.getPath()));
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

  public static void ensureExists(File dir) throws IOException {
    if (!dir.exists() && !dir.mkdirs()) {
      throw new IOException(CommonBundle.message("exception.directory.can.not.create", dir.getPath()));
    }
  }

  @NotNull
  public static String getNameWithoutExtension(@NotNull File file) {
    return getNameWithoutExtension(file.getName());
  }

  @NotNull
  public static String getNameWithoutExtension(@NotNull String name) {
    return FileUtilRt.getNameWithoutExtension(name);
  }

  public static String createSequentFileName(@NotNull File aParentFolder, @NotNull @NonNls String aFilePrefix, @NotNull String aExtension) {
    return findSequentNonexistentFile(aParentFolder, aFilePrefix, aExtension).getName();
  }

  public static File findSequentNonexistentFile(@NotNull File aParentFolder,
                                                @NotNull @NonNls final String aFilePrefix,
                                                @NotNull String aExtension) {
    int postfix = 0;
    String ext = aExtension.isEmpty() ? "" : "." + aExtension;

    File candidate = new File(aParentFolder, aFilePrefix + ext);
    while (candidate.exists()) {
      postfix++;
      candidate = new File(aParentFolder, aFilePrefix + Integer.toString(postfix) + ext);
    }
    return candidate;
  }

  @NotNull
  public static String toSystemDependentName(@NonNls @NotNull String aFileName) {
    return FileUtilRt.toSystemDependentName(aFileName);
  }

  @NotNull
  public static String toSystemIndependentName(@NonNls @NotNull String aFileName) {
    return FileUtilRt.toSystemIndependentName(aFileName);
  }

  @NotNull
  public static String nameToCompare(@NonNls @NotNull String name) {
    return (SystemInfo.isFileSystemCaseSensitive ? name : name.toLowerCase()).replace('\\', '/');
  }

  /**
   * Converts given path to canonical representation by eliminating '.'s, traversing '..'s, and omitting duplicate separators.
   * Please note that this method is symlink-unfriendly (i.e. result of "/path/to/link/../next" most probably will differ from
   * what {@link java.io.File#getCanonicalPath()} will return) - so use with care.
   */
  @Nullable
  public static String toCanonicalPath(@Nullable String path) {
    return toCanonicalPath(path, File.separatorChar);
  }

  @Nullable
  public static String toCanonicalPath(@Nullable String path, final char separatorChar) {
    if (path == null || path.isEmpty()) {
      return path;
    }
    else if (".".equals(path)) {
      return "";
    }

    path = path.replace(separatorChar, '/');
    if (path.indexOf('/') == -1) {
      return path;
    }

    int start = pathRootEnd(path) + 1, dots = 0;
    boolean separator = true;

    StringBuilder result = new StringBuilder(path.length());
    result.append(path, 0, start);

    for (int i = start; i < path.length(); ++i) {
      char c = path.charAt(i);
      if (c == '/') {
        if (!separator) {
          processDots(result, dots, start);
          dots = 0;
        }
        separator = true;
      }
      else if (c == '.') {
        if (separator || dots > 0) {
          ++dots;
        }
        else {
          result.append(c);
        }
        separator = false;
      }
      else {
        if (dots > 0) {
          StringUtil.repeatSymbol(result, '.', dots);
          dots = 0;
        }
        result.append(c);
        separator = false;
      }
    }

    if (dots > 0) {
      processDots(result, dots, start);
    }

    int lastChar = result.length() - 1;
    if (lastChar >= 0 && result.charAt(lastChar) == '/' && lastChar > start) {
      result.deleteCharAt(lastChar);
    }

    return result.toString();
  }

  private static int pathRootEnd(CharSequence path) {
    if (path.length() > 0 && path.charAt(0) == '/') {
      return 0;
    }
    else if (path.length() > 1 && path.charAt(0) == '/' && path.charAt(1) == '/') {
      return 1;
    }
    else if (path.length() > 2 && path.charAt(1) == ':' && path.charAt(2) == '/') {
      return 2;
    }
    return -1;
  }

  private static void processDots(StringBuilder result, int dots, int start) {
    if (dots == 2) {
      int pos = -1;
      if (!StringUtil.endsWith(result, "/../") && !StringUtil.equals(result, "../")) {
        pos = StringUtil.lastIndexOf(result, '/', start, result.length() - 1);
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
        result.delete(pos, result.length());
      }
      else {
        result.append("../");  // impossible to traverse, keep as-is
      }
    }
    else if (dots != 1) {
      StringUtil.repeatSymbol(result, '.', dots);
      result.append('/');
    }
  }

  @NotNull
  public static String normalize(@NotNull String path) {
    final StringBuilder result = new StringBuilder(path.length());

    int start = 0;
    if (SystemInfo.isWindows && (path.startsWith("//") || path.startsWith("\\\\"))) {
      start = 2;
      result.append("//");
    }

    boolean separator = false;
    for (int i = start; i < path.length(); ++i) {
      final char c = path.charAt(i);
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

  @NotNull
  public static String unquote(@NotNull String urlString) {
    urlString = urlString.replace('/', File.separatorChar);
    return URLUtil.unescapePercentSequences(urlString);
  }

  public static boolean isFilePathAcceptable(@NotNull File root, @Nullable FileFilter fileFilter) {
    File file = root;
    do {
      if (fileFilter != null && !fileFilter.accept(file)) return false;
      file = file.getParentFile();
    }
    while (file != null);
    return true;
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
    if (path1 == path2) return true;
    if (path1 == null || path2 == null) return false;

    path1 = toCanonicalPath(path1);
    path2 = toCanonicalPath(path2);
    //noinspection ConstantConditions
    return PATH_HASHING_STRATEGY.equals(path1, path2);
  }

  public static int compareFiles(@Nullable File file1, @Nullable File file2) {
    return comparePaths(file1 == null ? null : file1.getPath(), file2 == null ? null : file2.getPath());
  }

  public static int comparePaths(@Nullable String path1, @Nullable String path2) {
    path1 = path1 == null ? null : toSystemIndependentName(path1);
    path2 = path2 == null ? null : toSystemIndependentName(path2);
    return StringUtil.compare(path1, path2, !SystemInfo.isFileSystemCaseSensitive);
  }

  public static int fileHashCode(@Nullable File file) {
    return pathHashCode(file == null ? null : file.getPath());
  }

  public static int pathHashCode(@Nullable String path) {
    return StringUtil.isEmpty(path) || path == null ? 0 : PATH_HASHING_STRATEGY.computeHashCode(toCanonicalPath(path));
  }

  @NotNull
  public static String getExtension(@NotNull String fileName) {
    return FileUtilRt.getExtension(fileName);
  }

  @NotNull
  public static String resolveShortWindowsName(@NotNull final String path) throws IOException {
    if (SystemInfo.isWindows) {
      //todo: this resolves symlinks on Windows, but we'd rather not do it
      return new File(path.replace(File.separatorChar, '/')).getCanonicalPath();
    }
    return path;
  }

  public static void collectMatchedFiles(@NotNull File root, @NotNull Pattern pattern, @NotNull List<File> outFiles) {
    collectMatchedFiles(root, root, pattern, outFiles);
  }

  private static void collectMatchedFiles(@NotNull File absoluteRoot,
                                          @NotNull File root,
                                          @NotNull Pattern pattern,
                                          @NotNull List<File> files) {
    final File[] dirs = root.listFiles();
    if (dirs == null) return;
    for (File dir : dirs) {
      if (dir.isFile()) {
        final String relativePath = getRelativePath(absoluteRoot, dir);
        if (relativePath != null) {
          final String path = toSystemIndependentName(relativePath);
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
  @NotNull
  public static String convertAntToRegexp(@NotNull String antPattern) {
    return convertAntToRegexp(antPattern, true);
  }

  /**
   * @param antPattern ant-style path pattern
   * @return java regexp pattern.
   *         Note that no matter whether forward or backward slashes were used in the antPattern
   *         the returned regexp pattern will use forward slashes ('/') as file separators.
   *         Paths containing windows-style backslashes must be converted before matching against the resulting regexp
   * @see com.intellij.openapi.util.io.FileUtil#toSystemIndependentName
   */
  @RegExp
  @NotNull
  public static String convertAntToRegexp(@NotNull String antPattern, boolean ignoreStartingSlash) {
    final StringBuilder builder = new StringBuilder();
    int asteriskCount = 0;
    boolean recursive = true;
    final int start = ignoreStartingSlash && (StringUtil.startsWithChar(antPattern, '/') || StringUtil.startsWithChar(antPattern, '\\')) ? 1 : 0;
    for (int idx = start; idx < antPattern.length(); idx++) {
      final char ch = antPattern.charAt(idx);

      if (ch == '*') {
        asteriskCount++;
        continue;
      }

      final boolean foundRecursivePattern = recursive && asteriskCount == 2 && (ch == '/' || ch == '\\');
      final boolean asterisksFound = asteriskCount > 0;

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

    // handle ant shorthand: mypackage/test/ is interpreted as if it were mypackage/test/**
    final boolean isTrailingSlash = builder.length() > 0 && builder.charAt(builder.length() - 1) == '/';
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
    fromDir.delete();

    return success;
  }

  /**
   * Has duplicate: {@link com.intellij.coverage.listeners.CoverageListener#sanitize(java.lang.String, java.lang.String)}
   * as FileUtil is not available in client's vm
   */
  @NotNull
  public static String sanitizeFileName(@NotNull String name) {
    StringBuilder result = new StringBuilder();

    for (int i = 0; i < name.length(); i++) {
      final char ch = name.charAt(i);

      if (ch > 0 && ch < 255) {
        if (Character.isLetterOrDigit(ch)) {
          result.append(ch);
        }
        else {
          result.append("_");
        }
      }
    }

    return result.toString();
  }

  public static boolean canExecute(@NotNull File file) {
    return file.canExecute();
  }

  public static void setReadOnlyAttribute(@NotNull String path, boolean readOnlyFlag) throws IOException {
    final boolean writableFlag = !readOnlyFlag;
    final File file = new File(path);
    if (!file.setWritable(writableFlag) && file.canWrite() != writableFlag) {
      LOG.warn("Can't set writable attribute of '" + path + "' to " + readOnlyFlag);
    }
  }

  public static void appendToFile(@NotNull File file, @NotNull String text) throws IOException {
    writeToFile(file, text.getBytes("UTF-8"), true);
  }

  public static void writeToFile(@NotNull File file, @NotNull byte[] text) throws IOException {
    writeToFile(file, text, false);
  }

  public static void writeToFile(@NotNull File file, @NotNull String text) throws IOException {
    writeToFile(file, text.getBytes("UTF-8"), false);
  }

  public static void writeToFile(@NotNull File file, @NotNull byte[] text, int off, int len) throws IOException {
    writeToFile(file, text, off, len, false);
  }

  public static void writeToFile(@NotNull File file, @NotNull byte[] text, boolean append) throws IOException {
    writeToFile(file, text, 0, text.length, append);
  }

  private static void writeToFile(@NotNull File file, @NotNull byte[] text, final int off, final int len, boolean append)
    throws IOException {
    createParentDirs(file);
    OutputStream stream = new BufferedOutputStream(new FileOutputStream(file, append));
    try {
      stream.write(text, off, len);
    }
    finally {
      stream.close();
    }
  }

  public static boolean processFilesRecursively(@NotNull File root, @NotNull Processor<File> processor) {
    return processFilesRecursively(root, processor, null);
  }

  public static boolean processFilesRecursively(@NotNull File root, @NotNull Processor<File> processor,
                                                  final @Nullable Processor<File> directoryFilter) {
    final LinkedList<File> queue = new LinkedList<File>();
    queue.add(root);
    while (!queue.isEmpty()) {
      final File file = queue.removeFirst();
      if (!processor.process(file)) return false;
      if (file.isDirectory() && (directoryFilter == null || directoryFilter.process(file))) {
        final File[] children = file.listFiles();
        if (children != null) {
          ContainerUtil.addAll(queue, children);
        }
      }
    }
    return true;
  }

  @Nullable
  public static File findFirstThatExist(@NotNull String... paths) {
    for (String path : paths) {
      if (!StringUtil.isEmptyOrSpaces(path)) {
        File file = new File(toSystemDependentName(path));
        if (file.exists()) return file;
      }
    }

    return null;
  }

  @NotNull
  public static List<File> findFilesByMask(@NotNull Pattern pattern, @NotNull File dir) {
    final ArrayList<File> found = new ArrayList<File>();
    final File[] files = dir.listFiles();
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

  @NotNull
  public static List<File> findFilesOrDirsByMask(@NotNull Pattern pattern, @NotNull File dir) {
    final ArrayList<File> found = new ArrayList<File>();
    final File[] files = dir.listFiles();
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
  @Nullable
  public static String findFileInProvidedPath(String providedPath, String... fileNames) {
    if (StringUtil.isEmpty(providedPath)) {
      return "";
    }

    File providedFile = new File(providedPath);
    if (providedFile.exists()) {
      String name = providedFile.getName();
      for (String fileName : fileNames) {
        if (name.equals(fileName)) {
          return toSystemDependentName(providedFile.getPath());
        }
      }
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

  /** @deprecated use {@linkplain #isAbsolute(String)} (to remove in IDEA 13) */
  @SuppressWarnings("UnusedDeclaration")
  public static boolean isAbsoluteFilePath(String path) {
    return isAbsolute(path);
  }

  /** @deprecated use {@linkplain #isAbsolute(String)} (to remove in IDEA 13) */
  @SuppressWarnings("UnusedDeclaration")
  public static boolean isWindowsAbsolutePath(String path) {
    return isAbsolute(path);
  }

  @Nullable
  public static String getLocationRelativeToUserHome(@Nullable final String path) {
    if (path == null) return null;

    if (SystemInfo.isUnix) {
      final File projectDir = new File(path);
      final File userHomeDir = new File(SystemProperties.getUserHome());
      if (isAncestor(userHomeDir, projectDir, true)) {
        return  "~/" + getRelativePath(userHomeDir, projectDir);
      }
    }

    return path;
  }

  @NotNull
  public static File[] notNullize(@Nullable File[] files) {
    return notNullize(files, ArrayUtil.EMPTY_FILE_ARRAY);
  }

  @NotNull
  public static File[] notNullize(@Nullable File[] files, @NotNull File[] defaultFiles) {
    return files == null ? defaultFiles : files;
  }

  public static boolean isHashBangLine(CharSequence firstCharsIfText, String marker) {
    if (firstCharsIfText == null) {
      return false;
    }
    final int lineBreak = StringUtil.indexOf(firstCharsIfText, '\n');
    if (lineBreak < 0) {
      return false;
    }
    String firstLine = firstCharsIfText.subSequence(0, lineBreak).toString();
    if (!firstLine.startsWith("#!")) {
      return false;
    }
    return firstLine.contains(marker);
  }

  @NotNull
  public static File createTempDirectory(@NotNull @NonNls String prefix, @Nullable @NonNls String suffix) throws IOException {
    return FileUtilRt.createTempDirectory(prefix, suffix);
  }

  @NotNull
  public static File createTempDirectory(@NotNull @NonNls String prefix, @Nullable @NonNls String suffix, boolean deleteOnExit) throws IOException {
    return FileUtilRt.createTempDirectory(prefix, suffix, deleteOnExit);
  }

  @NotNull
  public static File createTempDirectory(@NotNull File dir, @NotNull @NonNls String prefix, @Nullable @NonNls String suffix) throws IOException {
    return FileUtilRt.createTempDirectory(dir, prefix, suffix);
  }

  @NotNull
  public static File createTempDirectory(@NotNull File dir,
                                         @NotNull @NonNls String prefix,
                                         @Nullable @NonNls String suffix,
                                         boolean deleteOnExit) throws IOException {
    return FileUtilRt.createTempDirectory(dir, prefix, suffix, deleteOnExit);
  }

  @NotNull
  public static File createTempFile(@NotNull @NonNls String prefix, @Nullable @NonNls String suffix) throws IOException {
    return FileUtilRt.createTempFile(prefix, suffix);
  }

  @NotNull
  public static File createTempFile(@NotNull @NonNls String prefix, @Nullable @NonNls String suffix, boolean deleteOnExit) throws IOException {
    return FileUtilRt.createTempFile(prefix, suffix, deleteOnExit);
  }

  @NotNull
  public static File createTempFile(@NonNls File dir, @NotNull @NonNls String prefix, @Nullable @NonNls String suffix) throws IOException {
    return FileUtilRt.createTempFile(dir, prefix, suffix);
  }

  @NotNull
  public static File createTempFile(@NonNls File dir, @NotNull @NonNls String prefix, @Nullable @NonNls String suffix, boolean create) throws IOException {
    return FileUtilRt.createTempFile(dir, prefix, suffix, create);
  }

  @NotNull
  public static File createTempFile(@NonNls File dir,
                                    @NotNull @NonNls String prefix,
                                    @Nullable @NonNls String suffix,
                                    boolean create,
                                    boolean deleteOnExit) throws IOException {
    return FileUtilRt.createTempFile(dir, prefix, suffix, create, deleteOnExit);
  }

  @NotNull
  public static String getTempDirectory() {
    return FileUtilRt.getTempDirectory();
  }

  @TestOnly
  public static void resetCanonicalTempPathCache(final String tempPath) {
    FileUtilRt.resetCanonicalTempPathCache(tempPath);
  }

  @NotNull
  public static File generateRandomTemporaryPath() throws IOException {
    return FileUtilRt.generateRandomTemporaryPath();
  }

  public static void setExecutableAttribute(@NotNull String path, boolean executableFlag) throws IOException {
    FileUtilRt.setExecutableAttribute(path, executableFlag);
  }

  @NotNull
  public static String loadFile(@NotNull File file) throws IOException {
    return FileUtilRt.loadFile(file);
  }

  @NotNull
  public static String loadFile(@NotNull File file, boolean convertLineSeparators) throws IOException {
    return FileUtilRt.loadFile(file, convertLineSeparators);
  }

  @NotNull
  public static String loadFile(@NotNull File file, @Nullable @NonNls String encoding) throws IOException {
    return FileUtilRt.loadFile(file, encoding);
  }

  @NotNull
  public static String loadFile(@NotNull File file, @Nullable @NonNls String encoding, boolean convertLineSeparators) throws IOException {
    return FileUtilRt.loadFile(file, encoding, convertLineSeparators);
  }

  @NotNull
  public static char[] loadFileText(@NotNull File file) throws IOException {
    return FileUtilRt.loadFileText(file);
  }

  @NotNull
  public static char[] loadFileText(@NotNull File file, @Nullable @NonNls String encoding) throws IOException {
    return FileUtilRt.loadFileText(file, encoding);
  }

  @NotNull
  public static char[] loadText(@NotNull Reader reader, int length) throws IOException {
    return FileUtilRt.loadText(reader, length);
  }

  @NotNull
  public static byte[] loadBytes(@NotNull InputStream stream) throws IOException {
    return FileUtilRt.loadBytes(stream);
  }

  @NotNull
  public static byte[] loadBytes(@NotNull InputStream stream, int length) throws IOException {
    return FileUtilRt.loadBytes(stream, length);
  }

  private static final class CompletedFuture<T> implements Future<T> {
    public boolean cancel(boolean mayInterruptIfRunning) {
      return false;
    }
    public boolean isCancelled() {
      return false;
    }
    public boolean isDone() {
      return true;
    }
    @Nullable
    public T get() throws InterruptedException, ExecutionException {
      return null;
    }
    @Nullable
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
      return null;
    }
  }
}
