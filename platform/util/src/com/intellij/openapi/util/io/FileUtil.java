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
import com.intellij.Patches;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.Processor;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import com.intellij.util.io.URLUtil;
import com.intellij.util.text.CaseInsensitiveStringHashingStrategy;
import gnu.trove.TObjectHashingStrategy;
import org.intellij.lang.annotations.MagicConstant;
import org.intellij.lang.annotations.RegExp;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.util.rt.FileUtilRt;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.channels.FileChannel;
import java.util.*;
import java.util.regex.Pattern;

@SuppressWarnings({"UtilityClassWithoutPrivateConstructor"})
public class FileUtil extends FileUtilLight {
  public static final int MEGABYTE = 1024 * 1024;

  @NonNls public static final String ASYNC_DELETE_EXTENSION = ".__del__";

  public static final TObjectHashingStrategy<String> PATH_HASHING_STRATEGY;
  static {
    if (SystemInfo.isFileSystemCaseSensitive) {
      @SuppressWarnings({"unchecked", "UnnecessaryLocalVariable"})
      final TObjectHashingStrategy<String> canonical = TObjectHashingStrategy.CANONICAL;
      PATH_HASHING_STRATEGY = canonical;
    }
    else {
      PATH_HASHING_STRATEGY = CaseInsensitiveStringHashingStrategy.INSTANCE;
    }
  }

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.io.FileUtil");

  private static final ThreadLocal<byte[]> BUFFER = new ThreadLocal<byte[]>() {
    protected byte[] initialValue() {
      return new byte[1024 * 20];
    }
  };

  // do not use channels to copy files larger than 5 Mb because of possible MapFailed error
  private static final long CHANNELS_COPYING_LIMIT = 5L * MEGABYTE;

  private static final int MAX_FILE_DELETE_ATTEMPTS = 10;

  private static final Method JAVA_IO_FILESYSTEM_GET_BOOLEAN_ATTRIBUTES_METHOD;
  private static final Object JAVA_IO_FILESYSTEM; // java.io.FileSystem

  @Nullable
  public static String getRelativePath(File base, File file) {
    if (base == null || file == null) return null;

    if (!base.isDirectory()) {
      base = base.getParentFile();
      if (base == null) return null;
    }

    if (base.equals(file)) return ".";

    final String filePath = file.getAbsolutePath();
    String basePath = base.getAbsolutePath();
    return getRelativePath(basePath, filePath, File.separatorChar);
  }

  public static String getRelativePath(@NotNull String basePath, @NotNull String filePath, final char separator) {
    return getRelativePath(basePath, filePath, separator, SystemInfo.isFileSystemCaseSensitive);
  }

  private static String ensureEnds(@NotNull String s, final char endsWith) {
    return StringUtil.endsWithChar(s, endsWith) ? s : s + endsWith;
  }

  public static String getRelativePath(@NotNull String basePath,
                                       @NotNull String filePath,
                                       final char separator,
                                       final boolean caseSensitive) {
    basePath = ensureEnds(basePath, separator);

    String basePathToCompare = caseSensitive ? basePath : basePath.toLowerCase();
    String filePathToCompare = caseSensitive ? filePath : filePath.toLowerCase();
    if (basePathToCompare.equals(ensureEnds(filePathToCompare, separator))) return ".";
    int len = 0;
    int lastSeparatorIndex = 0; // need this for cases like this: base="/temp/abcde/base" and file="/temp/ab"
    while (len < filePath.length() && len < basePath.length() && filePathToCompare.charAt(len) == basePathToCompare.charAt(len)) {
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

  public static boolean isAbsolute(@NotNull String path) {
    return new File(path).isAbsolute();
  }

  public static boolean isAncestor(@NotNull final String ancestor, @NotNull final String descendant, final boolean strict) {
    return isAncestor(new File(toSystemDependentName(ancestor)), new File(toSystemDependentName(descendant)), strict);
  }

  /**
   * Check if the {@code ancestor} is an ancestor of {@code file}.
   *
   * @param ancestor the file
   * @param file     the file
   * @param strict   if {@code false} then this method returns {@code true} if {@code ancestor}
   *                 and {@code file} are equal
   * @return {@code true} if {@code ancestor} is parent of {@code file}; {@code false} otherwise
   */
  public static boolean isAncestor(@NotNull File ancestor, @NotNull File file, boolean strict) {
    File parent = strict ? getParentFile(file) : file;
    while (true) {
      if (parent == null) {
        return false;
      }
      // Do not user file.equals as it incorrectly works on MacOS
      if (pathsEqual(parent.getPath(), ancestor.getPath())) {
        return true;
      }
      parent = getParentFile(parent);
    }
  }

  /**
   * Get parent for the file. The method correctly
   * processes "." and ".." in file names. The name
   * remains relative if was relative before.
   *
   * @param file a file to analyze
   * @return a parent or the null if the file has no parent.
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

  @NotNull
  public static byte[] loadFileBytes(@NotNull File file) throws IOException {
    byte[] bytes;
    final InputStream stream = new FileInputStream(file);
    try {
      final long len = file.length();
      if (len < 0) {
        throw new IOException("File length reported negative, probably doesn't exist");
      }

      if (len > 100 * MEGABYTE) {
        throw new FileTooBigException("Attempt to load '" + file + "' in memory buffer, file length is " + len + " bytes.");
      }

      bytes = loadBytes(stream, (int)len);
    }
    finally {
      stream.close();
    }
    return bytes;
  }

  @NotNull
  public static byte[] loadBytes(@NotNull InputStream stream, int length) throws IOException {
    byte[] bytes = new byte[length];
    int count = 0;
    while (count < length) {
      int n = stream.read(bytes, count, length - count);
      if (n <= 0) break;
      count += n;
    }
    return bytes;
  }

  @NotNull
  public static byte[] loadBytes(@NotNull InputStream stream) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    final byte[] bytes = BUFFER.get();
    while (true) {
      int n = stream.read(bytes, 0, bytes.length);
      if (n <= 0) break;
      buffer.write(bytes, 0, n);
    }
    buffer.close();
    return buffer.toByteArray();
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

  public static void asyncDelete(@NotNull File file) {
    final File tempFile = renameToTempFileOrDelete(file);
    if (tempFile == null) {
      return;
    }
    startDeletionThread(tempFile);
  }

  public static void asyncDelete(@NotNull Collection<File> files) {
    List<File> tempFiles = new ArrayList<File>();
    for (File file : files) {
      final File tempFile = renameToTempFileOrDelete(file);
      if (tempFile != null) {
        tempFiles.add(tempFile);
      }
    }
    if (!tempFiles.isEmpty()) {
      startDeletionThread(tempFiles.toArray(new File[tempFiles.size()]));
    }
  }

  private static void startDeletionThread(@NotNull final File... tempFiles) {
    final Runnable deleteFilesTask = new Runnable() {
      public void run() {
        final Thread currentThread = Thread.currentThread();
        currentThread.setPriority(Thread.MIN_PRIORITY);
        //ShutDownTracker.getInstance().registerStopperThread(currentThread);
        try {
          for (File tempFile : tempFiles) {
            delete(tempFile);
          }
        }
        finally {
          //ShutDownTracker.getInstance().unregisterStopperThread(currentThread);
          currentThread.setPriority(Thread.NORM_PRIORITY);
        }
      }
    };

    try {
// Attempt to execute on pooled thread
      final Class<?> aClass = Class.forName("com.intellij.openapi.application.ApplicationManager");
      final Method getApplicationMethod = aClass.getMethod("getApplication");
      final Object application = getApplicationMethod.invoke(null);
      final Method executeOnPooledThreadMethod = application.getClass().getMethod("executeOnPooledThread", Runnable.class);
      executeOnPooledThreadMethod.invoke(application, deleteFilesTask);
    }
    catch (Exception e) {
      //noinspection HardCodedStringLiteral
      Thread t = new Thread(deleteFilesTask, "File deletion thread");
      t.start();
    }
  }

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
    if (file.isDirectory() && !FileSystemUtil.isSymLink(file)) {
      File[] files = file.listFiles();
      if (files != null) {
        for (File child : files) {
          if (!delete(child)) return false;
        }
      }
    }

    for (int i = 0; i < MAX_FILE_DELETE_ATTEMPTS; i++) {
      if (file.delete() || !file.exists()) return true;
      try {
        //noinspection BusyWait
        Thread.sleep(10);
      }
      catch (InterruptedException ignored) { }
    }
    return false;
  }

  public static boolean createParentDirs(@NotNull File file) {
    if (!file.exists()) {
      String parentDirPath = file.getParent();
      if (parentDirPath != null) {
        final File parentFile = new File(parentDirPath);
        int parentAttributes = getBooleanAttributes(parentFile);
        boolean ok = parentAttributes != -1 ? (parentAttributes & (BA_EXISTS | BA_DIRECTORY)) == (BA_EXISTS | BA_DIRECTORY)
                     : parentFile.exists() && parentFile.isDirectory();
        return ok || parentFile.mkdirs();
      }
    }
    return true;
  }

  public static boolean createIfDoesntExist(@NotNull File file) {
    if (file.exists()) return true;
    try {
      if (!createParentDirs(file)) return false;

      OutputStream s = new FileOutputStream(file);
      s.close();
      return true;
    }
    catch (IOException e) {
      LOG.info(e);
      return false;
    }
  }

  public static boolean ensureCanCreateFile(@NotNull File file) {
    if (file.exists()) return file.canWrite();
    if (!createIfDoesntExist(file)) return false;
    return delete(file);
  }

  public static void copy(@NotNull File fromFile, @NotNull File toFile) throws IOException {
    performCopy(fromFile, toFile, true);
  }

  public static void copyContent(@NotNull File fromFile, @NotNull File toFile) throws IOException {
    performCopy(fromFile, toFile, false);
  }

  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  private static void performCopy(@NotNull File fromFile, @NotNull File toFile, final boolean syncTimestamp) throws IOException {
    FileOutputStream fos;
    try {
      fos = new FileOutputStream(toFile);
    }
    catch (FileNotFoundException e) {
      File parentFile = toFile.getParentFile();
      if (parentFile == null) {
        final IOException ioException = new IOException("parent file is null for " + toFile.getPath());
        ioException.initCause(e);
        throw ioException;
      }
      createParentDirs(toFile);
      fos = new FileOutputStream(toFile);
    }

    if (Patches.FILE_CHANNEL_TRANSFER_BROKEN || fromFile.length() > CHANNELS_COPYING_LIMIT) {
      FileInputStream fis = new FileInputStream(fromFile);
      try {
        copy(fis, fos);
      }
      finally {
        fis.close();
        fos.close();
      }
    }
    else {
      FileChannel fromChannel = new FileInputStream(fromFile).getChannel();
      FileChannel toChannel = fos.getChannel();
      try {
        fromChannel.transferTo(0, Long.MAX_VALUE, toChannel);
      }
      finally {
        fromChannel.close();
        toChannel.close();
      }
    }

    if (syncTimestamp) {
      final long timeStamp = fromFile.lastModified();
      if (!toFile.setLastModified(timeStamp)) {
        LOG.warn("Unable to set timestamp " + timeStamp + " to " + toFile);
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

  public static void copy(@NotNull InputStream inputStream, @NotNull OutputStream outputStream) throws IOException {
    final byte[] buffer = BUFFER.get();
    while (true) {
      int read = inputStream.read(buffer);
      if (read < 0) break;
      outputStream.write(buffer, 0, read);
    }
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
    if (!toDir.exists() && !toDir.mkdirs()) {
      throw new IOException(CommonBundle.message("exception.directory.can.not.create", toDir.getPath()));
    }
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

  @NotNull
  public static String getNameWithoutExtension(@NotNull File file) {
    return getNameWithoutExtension(file.getName());
  }

  @NotNull
  public static String getNameWithoutExtension(@NotNull String name) {
    int i = name.lastIndexOf('.');
    if (i != -1) {
      name = name.substring(0, i);
    }
    return name;
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
    return aFileName.replace('/', File.separatorChar).replace('\\', File.separatorChar);
  }

  @NotNull
  public static String toSystemIndependentName(@NonNls @NotNull String aFileName) {
    return aFileName.replace('\\', '/');
  }

  @NotNull
  public static String nameToCompare(@NonNls @NotNull String name) {
    return (SystemInfo.isFileSystemCaseSensitive ? name : name.toLowerCase()).replace('\\', '/');
  }

  public static String toCanonicalPath(String path) {
    if (path == null || path.isEmpty()) {
      return path;
    }
    path = path.replace(File.separatorChar, '/');
    final StringTokenizer tok = new StringTokenizer(path, "/");
    final Stack<String> stack = new Stack<String>();
    while (tok.hasMoreTokens()) {
      final String token = tok.nextToken();
      if ("..".equals(token)) {
        if (stack.isEmpty()) {
          return null;
        }
        stack.pop();
      }
      else if (!token.isEmpty() && !".".equals(token)) {
        stack.push(token);
      }
    }
    final StringBuilder result = new StringBuilder(path.length());
    if (path.charAt(0) == '/') {
      result.append("/");
    }
    for (int i = 0; i < stack.size(); i++) {
      String str = stack.get(i);
      if (i > 0) {
        result.append('/');
      }
      result.append(str);
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

  public static boolean startsWith(@NotNull @NonNls String path, @NotNull @NonNls String start) {
    return startsWith(path, start, SystemInfo.isFileSystemCaseSensitive);
  }

  public static boolean startsWith(@NotNull String path, @NotNull String start, final boolean caseSensitive) {
    final int length1 = path.length();
    final int length2 = start.length();
    if (length2 == 0) return true;
    if (length2 > length1) return false;
    if (!path.regionMatches(!caseSensitive, 0, start, 0, length2)) return false;
    if (length1 == length2) return true;
    char last2 = start.charAt(length2 - 1);
    char next1;
    if (last2 == '/' || last2 == File.separatorChar) {
      next1 = path.charAt(length2 - 1);
    }
    else {
      next1 = path.charAt(length2);
    }
    return next1 == '/' || next1 == File.separatorChar;
  }

  public static boolean pathsEqual(@NotNull String path1, @NotNull String path2) {
    return pathsEqual(path1, path2, false);
  }

  public static boolean pathsEqual(@NotNull String path1, @NotNull String path2, boolean convertSeparators) {
    if (convertSeparators) {
      path1 = toSystemIndependentName(path1);
      path2 = toSystemIndependentName(path2);
    }
    return SystemInfo.isFileSystemCaseSensitive ? path1.equals(path2) : path1.equalsIgnoreCase(path2);
  }

  public static int comparePaths(@NotNull String path1, @NotNull String path2) {
    return SystemInfo.isFileSystemCaseSensitive ? path1.compareTo(path2) : path1.compareToIgnoreCase(path2);
  }

  public static int pathHashCode(@NotNull String path) {
    return SystemInfo.isFileSystemCaseSensitive ? path.hashCode() : path.toLowerCase().hashCode();
  }

  @NotNull
  public static String getExtension(@NotNull String fileName) {
    int index = fileName.lastIndexOf('.');
    if (index < 0) return "";
    return fileName.substring(index + 1).toLowerCase();
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
        final String path = toSystemIndependentName(getRelativePath(absoluteRoot, dir));
        if (pattern.matcher(path).matches()) {
          files.add(dir);
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

  /** @deprecated use {@link FileSystemUtil#isSymLink(File)} (to remove in IDEA 12) */
  @SuppressWarnings("UnusedDeclaration")
  public static boolean isSymbolicLink(File file) {
    return FileSystemUtil.isSymLink(file);
  }

  /** @deprecated use {@link FileSystemUtil#isSymLink(File)} (to remove in IDEA 12) */
  @SuppressWarnings("UnusedDeclaration")
  public static boolean isSymbolicLink(File parent, String name) {
    return FileSystemUtil.isSymLink(new File(parent, name));
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
    final LinkedList<File> queue = new LinkedList<File>();
    queue.add(root);
    while (!queue.isEmpty()) {
      final File file = queue.removeFirst();
      if (!processor.process(file)) return false;
      if (file.isDirectory()) {
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
    for (File file : dir.listFiles()) {
      if (file.isDirectory()) {
        found.addAll(findFilesByMask(pattern, file));
      }
      else {
        if (pattern.matcher(file.getName()).matches()) {
          found.add(file);
        }
      }
    }
    return found;
  }

  @NotNull
  public static List<File> findFilesOrDirsByMask(@NotNull Pattern pattern, @NotNull File dir) {
    final ArrayList<File> found = new ArrayList<File>();
    for (File file : dir.listFiles()) {
      if (file.isDirectory()) {
        found.addAll(findFilesOrDirsByMask(pattern, file));
      }

      if (pattern.matcher(file.getName()).matches()) {
        found.add(file);
      }
    }
    return found;
  }

  /**
   * Returns empty string for empty path.
   * First checks whether provided path is a path of a file with sought-for name.
   * Unless found, checks if provided file was a directory. In this case checks existance
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

  /**
   * Treats C: as absolute file path.
   */
  public static boolean isAbsoluteFilePath(String path) {
    return isWindowsAbsolutePath(path) || isAbsolute(path);
  }

  public static boolean isWindowsAbsolutePath(String pathString) {
    if (SystemInfo.isWindows && pathString.length() >= 2 && Character.isLetter(pathString.charAt(0)) && pathString.charAt(1) == ':') {
      return true;
    }
    return false;
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

  // copied from FileSystem: they are package local there
  public static final int BA_EXISTS    = 0x01;
  public static final int BA_REGULAR   = 0x02;
  public static final int BA_DIRECTORY = 0x04;
  public static final int BA_HIDDEN    = 0x08;

  // todo[r.sh] use NIO2 API after migration to JDK 7
  // returns -1 if could not get attributes
  @MagicConstant(flags = {BA_EXISTS, BA_REGULAR, BA_DIRECTORY, BA_HIDDEN})
  public static int getBooleanAttributes(@NotNull File f) {
    if (JAVA_IO_FILESYSTEM_GET_BOOLEAN_ATTRIBUTES_METHOD != null) {
      try {
        Object flags = JAVA_IO_FILESYSTEM_GET_BOOLEAN_ATTRIBUTES_METHOD.invoke(JAVA_IO_FILESYSTEM, f);
        return ((Integer)flags).intValue();
      }
      catch (Exception ignored) { }
    }
    return -1;
  }

  @MagicConstant(flags = {BA_EXISTS, BA_REGULAR, BA_DIRECTORY, BA_HIDDEN})
  public @interface FileBooleanAttributes {}


  static {
    Object fs;
    Method getBooleanAttributes;
    try {
      Class<?> fsClass = Class.forName("java.io.FileSystem");
      Method getFileSystem = fsClass.getMethod("getFileSystem");
      getFileSystem.setAccessible(true);
      fs = getFileSystem.invoke(null);
      getBooleanAttributes = fsClass.getDeclaredMethod("getBooleanAttributes", File.class);
      if (fs == null || getBooleanAttributes == null) {
        fs = null;
        getBooleanAttributes = null;
      }
      else {
        getBooleanAttributes.setAccessible(true);
      }
    }
    catch (Exception e) {
      fs = null;
      getBooleanAttributes = null;
    }
    JAVA_IO_FILESYSTEM = fs;
    JAVA_IO_FILESYSTEM_GET_BOOLEAN_ATTRIBUTES_METHOD = getBooleanAttributes;
  }
}
