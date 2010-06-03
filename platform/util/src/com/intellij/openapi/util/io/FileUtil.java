/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Processor;
import com.intellij.util.io.URLUtil;
import org.intellij.lang.annotations.RegExp;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.channels.FileChannel;
import java.util.*;
import java.util.regex.Pattern;

@SuppressWarnings({"UtilityClassWithoutPrivateConstructor"})
public class FileUtil {
  public static final int MEGABYTE = 1024 * 1024;

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.io.FileUtil");
  private static final ThreadLocal<byte[]> BUFFER = new ThreadLocal<byte[]>() {
    protected byte[] initialValue() {
      return new byte[1024 * 20];
    }
  };

  // do not use channels to copy files larger than 5 Mb because of possible MapFailed error
  private static final long CHANNELS_COPYING_LIMIT = 5L * 1024L *  1024L;
  private static String ourCanonicalTempPathCache = null;

  //private static final byte[] BUFFER = new byte[1024 * 20];

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

  public static String getRelativePath(String basePath, String filePath, final char separator) {
    return getRelativePath(basePath, filePath, separator, SystemInfo.isFileSystemCaseSensitive);
  }

  private static String ensureEnds(final String s, final char endsWith) {
    return StringUtil.endsWithChar(s, endsWith) ? s : s + endsWith;
  }

  public static String getRelativePath(String basePath, String filePath, final char separator, final boolean caseSensitive) {
    basePath = ensureEnds(basePath, separator);

    int len = 0;
    int lastSeparatorIndex = 0; // need this for cases like this: base="/temp/abcde/base" and file="/temp/ab"
    String basePathToCompare = caseSensitive ? basePath : basePath.toLowerCase();
    String filePathToCompare = caseSensitive ? filePath : filePath.toLowerCase();
    if (basePathToCompare.equals(ensureEnds(filePathToCompare, separator))) return ".";
    while (len < filePath.length() && len < basePath.length() && filePathToCompare.charAt(len) == basePathToCompare.charAt(len)) {
      if (basePath.charAt(len) == separator) {
        lastSeparatorIndex = len;
      }
      len++;
    }

    if (len == 0) return null;

    StringBuilder relativePath = new StringBuilder();
    for (int i=len; i < basePath.length(); i++) {
      if (basePath.charAt(i) == separator) {
        relativePath.append("..");
        relativePath.append(separator);
      }
    }
    relativePath.append(filePath.substring(lastSeparatorIndex + 1));

    return relativePath.toString();
  }

  public static boolean isAbsolute(String path) {
    return new File(path).isAbsolute();
  }

  /**
   * Check if the {@code ancestor} is an ancestor of {@code file}.
   *
   * @param ancestor the file
   * @param file     the file
   * @param strict   if {@code false} then this method returns {@code true} if {@code ancestor}
   *                 and {@code file} are equal
   * @return {@code true} if {@code ancestor} is parent of {@code file}; {@code false} otherwise
   * @throws IOException this exception is never thrown and left here for backward compatibilty 
   */
  public static boolean isAncestor(File ancestor, File file, boolean strict) throws IOException {
    File parent = strict ? getParentFile(file) : file;
    while (true) {
      if (parent == null) {
        return false;
      }
      if (parent.equals(ancestor)) {
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
  public static File getParentFile(final File file) {
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
  public static char[] loadFileText(File file) throws IOException {
    return loadFileText(file, null);
  }

  @NotNull
  public static char[] loadFileText(File file, @NonNls String encoding) throws IOException{
    InputStream stream = new FileInputStream(file);
    Reader reader = encoding == null ? new InputStreamReader(stream) : new InputStreamReader(stream, encoding);
    try{
      return loadText(reader, (int)file.length());
    }
    finally{
      reader.close();
    }
  }

  @NotNull
  public static char[] loadText(Reader reader, int length) throws IOException {
    char[] chars = new char[length];
    int count = 0;
    while (count < chars.length) {
      int n = reader.read(chars, count, chars.length - count);
      if (n <= 0) break;
      count += n;
    }
    if (count == chars.length){
      return chars;
    }
    else{
      char[] newChars = new char[count];
      System.arraycopy(chars, 0, newChars, 0, count);
      return newChars;
    }
  }

  @NotNull
  public static byte[] loadFileBytes(File file) throws IOException {
    byte[] bytes;
    final InputStream stream = new FileInputStream(file);
    try{
      final long len = file.length();
      if (len < 0) {
        throw new IOException("File length reported negative, probably doesn't exist");
      }

      if (len > 100 * MEGABYTE) {
        throw new FileTooBigException("Attempt to load '" + file + "' in memory buffer, file length is " + len + " bytes.");
      }

      bytes = loadBytes(stream, (int)len);
    }
    finally{
      stream.close();
    }
    return bytes;
  }

  @NotNull
  public static byte[] loadBytes(InputStream stream, int length) throws IOException{
    byte[] bytes = new byte[length];
    int count = 0;
    while(count < length) {
      int n = stream.read(bytes, count, length - count);
      if (n <= 0) break;
      count += n;
    }
    return bytes;
  }

  @NotNull
  public static byte[] loadBytes(InputStream stream) throws IOException{
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    final byte[] bytes = BUFFER.get();
    while(true) {
      int n = stream.read(bytes, 0, bytes.length);
      if (n <= 0) break;
      buffer.write(bytes, 0, n);
    }
    buffer.close();
    return buffer.toByteArray();
  }

  @NotNull
  public static String loadTextAndClose(Reader reader) throws IOException {
    try {
      return new String(adaptiveLoadText(reader));
    }
    finally {
      reader.close();
    }
  }

  @NotNull
  public static char[] adaptiveLoadText(Reader reader) throws IOException {
    char[] chars = new char[4096];
    List<char[]> buffers = null;
    int count = 0;
    int total = 0;
    while (true) {
      int n = reader.read(chars, count, chars.length-count);
      if (n <= 0) break;
      count += n;
      if (total > 1024*1024*10) throw new FileTooBigException("File too big "+reader);
      total += n;
      if (count == chars.length) {
        if (buffers == null) {
          buffers = new ArrayList<char[]>();
        }
        buffers.add(chars);
        int newLength = Math.min(1024*1024, chars.length * 2);
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
  public static byte[] adaptiveLoadBytes(InputStream stream) throws IOException{
    byte[] bytes = new byte[4096];
    List<byte[]> buffers = null;
    int count = 0;
    int total = 0;
    while (true) {
      int n = stream.read(bytes, count, bytes.length-count);
      if (n <= 0) break;
      count += n;
      if (total > 1024*1024*10) throw new FileTooBigException("File too big "+stream);
      total += n;
      if (count == bytes.length) {
        if (buffers == null) {
          buffers = new ArrayList<byte[]>();
        }
        buffers.add(bytes);
        int newLength = Math.min(1024*1024, bytes.length * 2);
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

  public static File createTempDirectory(@NonNls String prefix, @NonNls String suffix) throws IOException{
    File file = doCreateTempFile(prefix, suffix);
    file.delete();
    file.mkdir();
    return file;
  }

  public static File createTempFile(@NonNls final File dir, @NonNls String prefix, @NonNls String suffix, final boolean create) throws IOException{
    File file = doCreateTempFile(prefix, suffix, dir);
    file.delete();
    if (create) {
      file.createNewFile();
    }
    return file;
  }

  public static File createTempFile(@NonNls String prefix, @NonNls String suffix) throws IOException{
    File file = doCreateTempFile(prefix, suffix);
    file.delete();
    file.createNewFile();
    return file;
  }

  private static File doCreateTempFile(String prefix, String suffix) throws IOException {
    return doCreateTempFile(prefix, suffix, new File(getTempDirectory()));
  }

  private static File doCreateTempFile(String prefix, String suffix, final File dir) throws IOException {
    if (prefix.length() < 3) {
      prefix = (prefix + "___").substring(0, 3);
    }

    int exceptionsCount = 0;
    while(true){
      try{
        return File.createTempFile(prefix, suffix, dir).getCanonicalFile();
      }
      catch(IOException e){ // Win32 createFileExclusively access denied
        if (++exceptionsCount >= 100) {
          throw e;
        }
      }
    }
  }

  public static String getTempDirectory() {
    if (ourCanonicalTempPathCache == null) {
      ourCanonicalTempPathCache = calcCanonicalTempPath();
    }
    return ourCanonicalTempPathCache;
  }

  public static void resetCanonicalTempPathCache() {
    ourCanonicalTempPathCache = null;
  }

  private static String calcCanonicalTempPath() {
    final String prop = System.getProperty("java.io.tmpdir");
    try {
      return new File(prop).getCanonicalPath();
    }
    catch (IOException e) {
      return prop;
    }
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
        ShutDownTracker.getInstance().registerStopperThread(currentThread);
        try {
          for (File tempFile : tempFiles) {
            delete(tempFile);
          }
        }
        finally {
          ShutDownTracker.getInstance().unregisterStopperThread(currentThread);
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

  private static File renameToTempFileOrDelete(File file) {
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

  private static File getTempFile(String originalFileName, File parent) {
    int randomSuffix = (int)(System.currentTimeMillis() % 1000);
    for (int i = randomSuffix; ; i++) {
      @NonNls String name = "___" + originalFileName + i + ".__del__";
      File tempFile = new File(parent, name);
      if (!tempFile.exists()) return tempFile;
    }
  }

  public static boolean delete(File file){
    File[] files = file.listFiles();
    if (files != null) {
      for (File file1 : files) {
        if (!delete(file1)) return false;
      }
    }

    for (int i = 0; i < 10; i++){
      if (file.delete() || !file.exists()) return true;
      try {
        Thread.sleep(10);
      }
      catch (InterruptedException ignored) {

      }
    }
    return false;
  }

  public static boolean createParentDirs(File file) {
    if (!file.exists()) {
      String parentDirPath = file.getParent();
      if (parentDirPath != null) {
        final File parentFile = new File(parentDirPath);
        return parentFile.exists() && parentFile.isDirectory() || parentFile.mkdirs();
      }
    }
    return false;
  }

  public static boolean createIfDoesntExist(File file) {
    if (file.exists()) return true;
    try {
      if (!createParentDirs(file)) return false;

      OutputStream s = new FileOutputStream(file);
      s.close();
      return true;
    }
    catch (IOException e) {
      return false;
    }
  }

  public static boolean ensureCanCreateFile(File file) {
    if (file.exists()) return file.canWrite();
    if (!createIfDoesntExist(file)) return false;
    return delete(file);
  }

  public static void copy(File fromFile, File toFile) throws IOException {
    performCopy(fromFile, toFile, true);
  }

  public static void copyContent(File fromFile, File toFile) throws IOException {
    performCopy(fromFile, toFile, false);
  }

  private static void performCopy(File fromFile, File toFile, final boolean syncTimestamp) throws IOException {
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
      toFile.setLastModified(fromFile.lastModified());
    }
  }

  public static void copy(InputStream inputStream, OutputStream outputStream) throws IOException {
    final byte[] buffer = BUFFER.get();
    while (true) {
      int read = inputStream.read(buffer);
      if (read < 0) break;
      outputStream.write(buffer, 0, read);
    }
  }

  public static void copy(InputStream inputStream, int size, OutputStream outputStream) throws IOException {
    final byte[] buffer = BUFFER.get();
    int toRead = size;
    while (toRead > 0) {
      int read = inputStream.read(buffer, 0, Math.min(buffer.length, toRead));
      if (read < 0) break;
      toRead -= read;
      outputStream.write(buffer, 0, read);
    }
  }

  public static void copyDir(File fromDir, File toDir) throws IOException {
    copyDir(fromDir, toDir, true);
  }

  public static void copyDir(File fromDir, File toDir, boolean copySystemFiles) throws IOException {
    copyDir(fromDir, toDir, copySystemFiles ? null : new FileFilter() {
      public boolean accept(File file) {
        return !file.getName().startsWith(".");
      }
    });
  }

  public static void copyDir(File fromDir, File toDir, final @Nullable FileFilter filter) throws IOException {
    toDir.mkdirs();
    if (isAncestor(fromDir, toDir, true)) {
      LOG.error(fromDir.getAbsolutePath() + " is ancestor of " + toDir + ". Can't copy to itself.");
      return;
    }
    File[] files = fromDir.listFiles();
    if(files == null) throw new IOException(CommonBundle.message("exception.directory.is.invalid", fromDir.getPath()));
    if(!fromDir.canRead()) throw new IOException(CommonBundle.message("exception.directory.is.not.readable", fromDir.getPath()));
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

  public static String getNameWithoutExtension(File file) {
    return getNameWithoutExtension(file.getName());
  }

  public static String getNameWithoutExtension(String name) {
    int i = name.lastIndexOf('.');
    if (i != -1) {
      name = name.substring(0, i);
    }
    return name;
  }

  public static String createSequentFileName(File aParentFolder, @NonNls String aFilePrefix, String aExtension) {
    return findSequentNonexistentFile(aParentFolder, aFilePrefix, aExtension).getName();
  }

  public static File findSequentNonexistentFile(final File aParentFolder, @NonNls final String aFilePrefix, final String aExtension) {
    int postfix = 0;
    String ext = 0 == aExtension.length() ? "" : "." + aExtension;

    File candidate = new File(aParentFolder, aFilePrefix + ext);
    while (candidate.exists()) {
      postfix++;
      candidate = new File(aParentFolder, aFilePrefix + Integer.toString(postfix) + ext);
    }
    return candidate;
  }

  public static String toSystemDependentName(@NonNls @NotNull String aFileName) {
    return aFileName.replace('/', File.separatorChar).replace('\\', File.separatorChar);
  }

  public static String toSystemIndependentName(@NonNls @NotNull String aFileName) {
    return aFileName.replace('\\', '/');
  }

  public static String nameToCompare(@NonNls @NotNull String name) {
    return (SystemInfo.isFileSystemCaseSensitive ? name : name.toLowerCase()).replace('\\', '/');
  }

  public static String unquote(String urlString) {
    urlString = urlString.replace('/', File.separatorChar);
    return URLUtil.unescapePercentSequences(urlString);
  }

  public static boolean isFilePathAcceptable(File file, @Nullable FileFilter fileFilter) {
    do {
      if (fileFilter != null && !fileFilter.accept(file)) return false;
      file = file.getParentFile();
    }
    while (file != null);
    return true;
  }

  public static void rename(final File source, final File target) throws IOException {
    if (source.renameTo(target)) return;
    if (!source.exists()) return;

    copy(source, target);
    delete(source);
  }

  public static boolean startsWith(@NonNls String path, @NonNls String start) {
    return startsWith(path, start, SystemInfo.isFileSystemCaseSensitive);
  }

  public static boolean startsWith(final String path, final String start, final boolean caseSensitive) {
    final int length1 = path.length();
    final int length2 = start.length();
    if (length2 == 0) return true;
    if (length2 > length1) return false;
    if (!path.regionMatches(!caseSensitive, 0, start, 0, length2)) return false;
    if (length1 == length2) return true;
    char last2 = start.charAt(length2 - 1);
    char next1;
    if (last2 == '/' || last2 == File.separatorChar) {
      next1 = path.charAt(length2 -1);
    }
    else {
      next1 = path.charAt(length2);
    }
    return next1 == '/' || next1 == File.separatorChar;
  }

  public static boolean pathsEqual(String path1, String path2) {
    return SystemInfo.isFileSystemCaseSensitive? path1.equals(path2) : path1.equalsIgnoreCase(path2);
  }

  public static int comparePaths(String path1, String path2) {
    return SystemInfo.isFileSystemCaseSensitive? path1.compareTo(path2) : path1.compareToIgnoreCase(path2);
  }

  public static int pathHashCode(String path) {
    return SystemInfo.isFileSystemCaseSensitive? path.hashCode() : path.toLowerCase().hashCode();
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

  public static void collectMatchedFiles(final File root, final Pattern pattern, final List<File> files) {
    collectMatchedFiles(root, root, pattern, files);
  }

  private static void collectMatchedFiles(final File absoluteRoot, final File root, final Pattern pattern, final List<File> files) {
    final File[] dirs = root.listFiles();
    if (dirs == null) return;
    for (File dir : dirs) {
      if (dir.isFile()) {
        final String path = toSystemIndependentName(getRelativePath(absoluteRoot, dir));
        if (pattern.matcher(path).matches()) {
          files.add(dir);
        }
      } else {
        collectMatchedFiles(absoluteRoot, dir, pattern, files);
      }
    }
  }

  @RegExp
  public static String convertAntToRegexp(String antPattern) {
    return convertAntToRegexp(antPattern, true);
  }

  /**
   * @param antPattern ant-style path pattern
   * @return java regexp pattern.
   * Note that no matter whether forward or backward slashes were used in the antPattern
   * the returned regexp pattern will use forward slashes ('/') as file separators.
   * Paths containing windows-style backslashes must be converted before matching against the resulting regexp
   * @see com.intellij.openapi.util.io.FileUtil#toSystemIndependentName
   */
  @RegExp
  public static String convertAntToRegexp(String antPattern, boolean ignoreStartingSlash) {
    final StringBuilder builder = new StringBuilder(antPattern.length());
    int asteriskCount = 0;
    boolean recursive = true;
    final int start = ignoreStartingSlash && (antPattern.startsWith("/") || antPattern.startsWith("\\")) ? 1 : 0;
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

      if (asterisksFound){
        builder.append("[^/]*?");
      }

      if (ch == '(' || ch == ')' || ch == '[' || ch == ']' || ch == '^' || ch == '$' || ch == '.' || ch == '{' || ch == '}' || ch == '+' || ch == '|') {
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
    final boolean isTrailingSlash =  builder.length() > 0 && builder.charAt(builder.length() - 1) == '/';
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

  public static boolean moveDirWithContent(File fromDir, File toDir) {
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

  public static String sanitizeFileName(String name) {
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
      else {

      }

    }

    return result.toString();
  }

  private static final Method IO_FILE_CAN_EXECUTE_METHOD;
  static {
    Method method;
    try {
      method = File.class.getDeclaredMethod("canExecute", boolean.class);
    }
    catch (NoSuchMethodException e) {
      method = null;
    }
    IO_FILE_CAN_EXECUTE_METHOD = method;
  }

  public static boolean canCallCanExecute() {
    return IO_FILE_CAN_EXECUTE_METHOD != null;
  }

  public static boolean canExecute(File file) {
    try {
      return ((Boolean)IO_FILE_CAN_EXECUTE_METHOD.invoke(file));
    }
    catch (Exception e) {
      LOG.error(e);
      return false;
    }
  }

  //File.setWritable() since java 6.0
  private static final Method IO_FILE_SET_WRITABLE_METHOD;
  static {
    Method method;
    try {
      method = File.class.getDeclaredMethod("setWritable", boolean.class);
    }
    catch (NoSuchMethodException e) {
      method = null;
    }
    IO_FILE_SET_WRITABLE_METHOD = method;
  }
  public static void setReadOnlyAttribute(String path, boolean readOnlyStatus) throws IOException {
    if (IO_FILE_SET_WRITABLE_METHOD != null) {
      try {
        IO_FILE_SET_WRITABLE_METHOD.invoke(new File(path), !readOnlyStatus);
        return;
      }
      catch (IllegalAccessException e) {
        LOG.error(e);
      }
      catch (InvocationTargetException e) {
        LOG.error(e);
      }
    }
    Process process;
    if (SystemInfo.isWindows) {
      process = Runtime.getRuntime().exec(new String[]{"attrib", readOnlyStatus ? "+r" : "-r", path});
    }
    else { // UNIXes go here
      process = Runtime.getRuntime().exec(new String[]{"chmod", readOnlyStatus ? "u-w" : "u+w", path});
    }
    try {
      process.waitFor();
    }
    catch (InterruptedException ignored) {
    }
  }

  /**
   * The method File.setExecutalbe() (which is avaialable since java 6.0)
   */
  private static final Method IO_FILE_SET_EXECUTABLE_METHOD;
  static {
    Method method;
    try {
      method = File.class.getDeclaredMethod("setExecutable", boolean.class);
    }
    catch (NoSuchMethodException e) {
      method = null;
    }
    IO_FILE_SET_EXECUTABLE_METHOD = method;
  }

  /**
   * Set executable attibute, it makes sense only on non-windows platforms.
   *
   * @param path the path to use
   * @param executableFlag new value of executable attribute
   * @throws IOException if there is a problem with setting the flag
   */
  public static void setExectuableAttribute(String path, boolean executableFlag) throws IOException {
    if (IO_FILE_SET_EXECUTABLE_METHOD != null) {
      try {
        IO_FILE_SET_EXECUTABLE_METHOD.invoke(new File(path), executableFlag);
        return;
      }
      catch (IllegalAccessException e) {
        LOG.error(e);
      }
      catch (InvocationTargetException e) {
        LOG.error(e);
      }
    }
    if (!SystemInfo.isWindows) {
      // UNIXes go here
      Process process = Runtime.getRuntime().exec(new String[]{"chmod", executableFlag ? "u+x" : "u-x", path});
      try {
        process.waitFor();
      }
      catch (InterruptedException ignored) {
      }
    }
  }

  public static void appendToFile(File file, String text) throws IOException {
    writeToFile(file, text.getBytes("UTF-8"), true);
  }

  public static void writeToFile(final File file, final byte[] text) throws IOException {
    writeToFile(file, text, false);
  }

  public static void writeToFile(final File file, final byte[] text, boolean append) throws IOException {
    createParentDirs(file);
    OutputStream stream = new BufferedOutputStream(new FileOutputStream(file, append));
    try {
      stream.write(text);
    }
    finally {
      stream.close();
    }
  }

  

  public static boolean processFilesRecursively(final File root, final Processor<File> processor) {
    final LinkedList<File> queue = new LinkedList<File>();
    queue.add(root);
    while (!queue.isEmpty()) {
      final File file = queue.removeFirst();
      if (!processor.process(file)) return false;
      if (file.isDirectory()) {
        final File[] children = file.listFiles();
        if (children != null) {
          queue.addAll(Arrays.asList(children));
        }
      }
    }
    return true;
  }

  @Nullable
  public static File findFirstThatExist(String... paths) {
    for (String path : paths) {
      if (!StringUtil.isEmptyOrSpaces(path)) {
        File file = new File(toSystemDependentName(path));
        if (file.exists()) return file;
      }
    }

    return null;
  }
}
