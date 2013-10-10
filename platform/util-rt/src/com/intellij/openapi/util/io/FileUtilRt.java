/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.LoggerRt;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.text.StringUtilRt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.*;
import java.nio.channels.FileChannel;
import java.util.UUID;

/**
 * Stripped-down version of {@code com.intellij.openapi.util.io.FileUtil}.
 * Intended to use by external (out-of-IDE-process) runners and helpers so it should not contain any library dependencies.
 *
 * @since 12.0
 */
@SuppressWarnings({"UtilityClassWithoutPrivateConstructor"})
public class FileUtilRt {
  private static final int KILOBYTE = 1024;
  public static final int MEGABYTE = KILOBYTE * KILOBYTE;
  public static final int LARGE_FOR_CONTENT_LOADING = Math.max(20 * MEGABYTE, getUserFileSizeLimit());

  private static final LoggerRt LOG = LoggerRt.getInstance("#com.intellij.openapi.util.io.FileUtilLight");
  private static final int MAX_FILE_IO_ATTEMPTS = 10;
  private static final boolean USE_FILE_CHANNELS = "true".equalsIgnoreCase(System.getProperty("idea.fs.useChannels"));

  public static final FileFilter ALL_FILES = new FileFilter() {
    @Override
    public boolean accept(File file) {
      return true;
    }
  };
  public static final FileFilter ALL_DIRECTORIES = new FileFilter() {
    @Override
    public boolean accept(File file) {
      return file.isDirectory();
    }
  };

  protected static final ThreadLocal<byte[]> BUFFER = new ThreadLocal<byte[]>() {
    @Override
    protected byte[] initialValue() {
      return new byte[1024 * 20];
    }
  };

  private static String ourCanonicalTempPathCache = null;

  @NotNull
  public static String getExtension(@NotNull String fileName) {
    int index = fileName.lastIndexOf('.');
    if (index < 0) return "";
    return fileName.substring(index + 1);
  }

  public static boolean extensionEquals(@NotNull String fileName, @NotNull String extension) {
    int extLen = extension.length();
    if (extLen == 0) {
      return fileName.indexOf('.') == -1;
    }
    int extStart = fileName.length() - extLen;
    return extStart >= 1 && fileName.charAt(extStart-1) == '.'
           && fileName.regionMatches(!SystemInfoRt.isFileSystemCaseSensitive, extStart, extension, 0, extLen);
  }

  @NotNull
  public static String toSystemDependentName(@NonNls @NotNull String aFileName) {
    return toSystemDependentName(aFileName, File.separatorChar);
  }

  @NotNull
  public static String toSystemDependentName(@NonNls @NotNull String aFileName, final char separatorChar) {
    return aFileName.replace('/', separatorChar).replace('\\', separatorChar);
  }

  @NotNull
  public static String toSystemIndependentName(@NonNls @NotNull String aFileName) {
    return aFileName.replace('\\', '/');
  }

  @Nullable
  public static String getRelativePath(File base, File file) {
    if (base == null || file == null) return null;

    if (!base.isDirectory()) {
      base = base.getParentFile();
      if (base == null) return null;
    }

    //noinspection FileEqualsUsage
    if (base.equals(file)) return ".";

    final String filePath = file.getAbsolutePath();
    String basePath = base.getAbsolutePath();
    return getRelativePath(basePath, filePath, File.separatorChar);
  }

  @Nullable
  public static String getRelativePath(@NotNull String basePath, @NotNull String filePath, final char separator) {
    return getRelativePath(basePath, filePath, separator, SystemInfoRt.isFileSystemCaseSensitive);
  }

  @Nullable
  public static String getRelativePath(@NotNull String basePath,
                                       @NotNull String filePath,
                                       final char separator,
                                       final boolean caseSensitive) {
    basePath = ensureEnds(basePath, separator);

    String basePathToCompare = caseSensitive ? basePath : basePath.toLowerCase();
    String filePathToCompare = caseSensitive ? filePath : filePath.toLowerCase();
    if (basePathToCompare.equals(ensureEnds(filePathToCompare, separator))) return ".";
    int len = 0;
    int lastSeparatorIndex = 0; // need this for cases like this: base="/temp/abc/base" and file="/temp/ab"
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

  private static String ensureEnds(@NotNull String s, final char endsWith) {
    return StringUtilRt.endsWithChar(s, endsWith) ? s : s + endsWith;
  }

  @NotNull
  public static String getNameWithoutExtension(@NotNull String name) {
    int i = name.lastIndexOf('.');
    if (i != -1) {
      name = name.substring(0, i);
    }
    return name;
  }

  @NotNull
  public static File createTempDirectory(@NotNull @NonNls String prefix, @Nullable @NonNls String suffix) throws IOException {
    return createTempDirectory(prefix, suffix, true);
  }

  public static File createTempDirectory(@NotNull @NonNls String prefix, @Nullable @NonNls String suffix,
                                         boolean deleteOnExit) throws IOException {
    final File dir = new File(getTempDirectory());
    return createTempDirectory(dir, prefix, suffix, deleteOnExit);
  }

  @NotNull
  public static File createTempDirectory(@NotNull File dir,
                                         @NotNull @NonNls String prefix, @Nullable @NonNls String suffix) throws IOException {
    return createTempDirectory(dir, prefix, suffix, true);
  }

  @NotNull
  public static File createTempDirectory(@NotNull File dir,
                                         @NotNull @NonNls String prefix, @Nullable @NonNls String suffix,
                                         boolean deleteOnExit) throws IOException {
    File file = doCreateTempFile(dir, prefix, suffix);
    if (deleteOnExit) {
      file.deleteOnExit();
    }
    if (!file.delete() && file.exists()) {
      throw new IOException("Cannot delete file: " + file);
    }
    if (!file.mkdir() && !file.isDirectory()) {
      throw new IOException("Cannot create directory: " + file);
    }
    return file;
  }

  @NotNull
  public static File createTempFile(@NotNull @NonNls String prefix, @Nullable @NonNls String suffix) throws IOException {
    return createTempFile(prefix, suffix, false); //false until TeamCity fixes its plugin
  }

  @NotNull
  public static File createTempFile(@NotNull @NonNls String prefix, @Nullable @NonNls String suffix,
                                    boolean deleteOnExit) throws IOException {
    final File dir = new File(getTempDirectory());
    return createTempFile(dir, prefix, suffix, true, deleteOnExit);
  }

  @NotNull
  public static File createTempFile(@NonNls File dir,
                                    @NotNull @NonNls String prefix, @Nullable @NonNls String suffix) throws IOException {
    return createTempFile(dir, prefix, suffix, true, true);
  }

  @NotNull
  public static File createTempFile(@NonNls File dir,
                                    @NotNull @NonNls String prefix, @Nullable @NonNls String suffix,
                                    boolean create) throws IOException {
    return createTempFile(dir, prefix, suffix, create, true);
  }

  @NotNull
  public static File createTempFile(@NonNls File dir,
                                    @NotNull @NonNls String prefix, @Nullable @NonNls String suffix,
                                    boolean create, boolean deleteOnExit) throws IOException {
    File file = doCreateTempFile(dir, prefix, suffix);
    if (deleteOnExit) {
      file.deleteOnExit();
    }
    if (!create) {
      if (!file.delete() && file.exists()) {
        throw new IOException("Cannot delete file: " + file);
      }
    }
    return file;
  }

  @NotNull
  private static File doCreateTempFile(@NotNull File dir,
                                       @NotNull @NonNls String prefix, @Nullable @NonNls String suffix) throws IOException {
    //noinspection ResultOfMethodCallIgnored
    dir.mkdirs();

    if (prefix.length() < 3) {
      prefix = (prefix + "___").substring(0, 3);
    }

    int exceptionsCount = 0;
    while (true) {
      try {
        //noinspection SSBasedInspection
        final File temp = File.createTempFile(prefix, suffix, dir);
        return normalizeFile(temp);
      }
      catch (IOException e) { // Win32 createFileExclusively access denied
        if (++exceptionsCount >= 100) {
          throw e;
        }
      }
    }
  }

  private static File normalizeFile(File temp) throws IOException {
    final File canonical = temp.getCanonicalFile();
    return SystemInfoRt.isWindows && canonical.getAbsolutePath().contains(" ") ? temp.getAbsoluteFile() : canonical;
  }

  @NotNull
  public static String getTempDirectory() {
    if (ourCanonicalTempPathCache == null) {
      ourCanonicalTempPathCache = calcCanonicalTempPath();
    }
    return ourCanonicalTempPathCache;
  }

  private static String calcCanonicalTempPath() {
    final File file = new File(System.getProperty("java.io.tmpdir"));
    try {
      final String canonical = file.getCanonicalPath();
      if (!SystemInfoRt.isWindows || !canonical.contains(" ")) {
        return canonical;
      }
    }
    catch (IOException ignore) { }
    return file.getAbsolutePath();
  }

  @TestOnly
  public static void resetCanonicalTempPathCache(final String tempPath) {
    ourCanonicalTempPathCache = tempPath;
  }

  @NotNull
  public static File generateRandomTemporaryPath() throws IOException {
    File file = new File(getTempDirectory(), UUID.randomUUID().toString());
    int i = 0;
    while (file.exists() && i < 5) {
      file = new File(getTempDirectory(), UUID.randomUUID().toString());
      ++i;
    }
    if (file.exists()) {
      throw new IOException("Couldn't generate unique random path.");
    }
    return normalizeFile(file);
  }

  /**
   * Set executable attribute, it makes sense only on non-windows platforms.
   *
   * @param path           the path to use
   * @param executableFlag new value of executable attribute
   * @throws java.io.IOException if there is a problem with setting the flag
   */
  public static void setExecutableAttribute(@NotNull String path, boolean executableFlag) throws IOException {
    final File file = new File(path);
    if (!file.setExecutable(executableFlag) && file.canExecute() != executableFlag) {
      LOG.warn("Can't set executable attribute of '" + path + "' to " + executableFlag);
    }
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
  public static String loadFile(@NotNull File file, @Nullable @NonNls String encoding) throws IOException {
    return loadFile(file, encoding, false);
  }

  @NotNull
  public static String loadFile(@NotNull File file, @Nullable @NonNls String encoding, boolean convertLineSeparators) throws IOException {
    final String s = new String(loadFileText(file, encoding));
    return convertLineSeparators ? StringUtilRt.convertLineSeparators(s) : s;
  }

  @NotNull
  public static char[] loadFileText(@NotNull File file) throws IOException {
    return loadFileText(file, null);
  }

  @NotNull
  public static char[] loadFileText(@NotNull File file, @Nullable @NonNls String encoding) throws IOException {
    InputStream stream = new FileInputStream(file);
    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
    Reader reader = encoding == null ? new InputStreamReader(stream) : new InputStreamReader(stream, encoding);
    try {
      return loadText(reader, (int)file.length());
    }
    finally {
      reader.close();
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
      char[] newChars = new char[count];
      System.arraycopy(chars, 0, newChars, 0, count);
      return newChars;
    }
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

  public static boolean isTooLarge(long len) {
    return len > LARGE_FOR_CONTENT_LOADING;
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

  public static boolean delete(@NotNull File file) {
    if (file.isDirectory()) {
      File[] files = file.listFiles();
      if (files != null) {
        for (File child : files) {
          if (!delete(child)) return false;
        }
      }
    }

    return deleteFile(file);
  }

  public interface RepeatableIOOperation<T, E extends Throwable> {
    @Nullable T execute(boolean lastAttempt) throws E;
  }

  public static @Nullable <T, E extends Throwable> T doIOOperation(@NotNull RepeatableIOOperation<T, E> ioTask) throws E {
    for (int i = MAX_FILE_IO_ATTEMPTS; i > 0; i--) {
      T result = ioTask.execute(i == 1);
      if (result != null) return result;

      try {
        //noinspection BusyWait
        Thread.sleep(10);
      }
      catch (InterruptedException ignored) { }
    }
    return null;
  }

  protected static boolean deleteFile(@NotNull final File file) {
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
    if (file.exists()) return file.canWrite();
    if (!createIfNotExists(file)) return false;
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
      LOG.info(e);
      return false;
    }
  }

  public static boolean createParentDirs(@NotNull File file) {
    if (!file.exists()) {
      final File parentFile = file.getParentFile();
      if (parentFile != null) {
        return createDirectory(parentFile);
      }
    }
    return true;
  }

  public static boolean createDirectory(@NotNull File path) {
    return path.isDirectory() || path.mkdirs();
  }

  public static void copy(@NotNull File fromFile, @NotNull File toFile) throws IOException {
    if (!ensureCanCreateFile(toFile)) {
      return;
    }

    FileOutputStream fos = new FileOutputStream(toFile);
    try {
      FileInputStream fis = new FileInputStream(fromFile);
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

    long timeStamp = fromFile.lastModified();
    if (timeStamp < 0) {
      LOG.warn("Invalid timestamp " + timeStamp + " of '" + fromFile + "'");
    }
    else if (!toFile.setLastModified(timeStamp)) {
      LOG.warn("Unable to set timestamp " + timeStamp + " to '" + toFile + "'");
    }
  }

  public static void copy(@NotNull InputStream inputStream, @NotNull OutputStream outputStream) throws IOException {
    if (USE_FILE_CHANNELS && inputStream instanceof FileInputStream && outputStream instanceof FileOutputStream) {
      final FileChannel fromChannel = ((FileInputStream)inputStream).getChannel();
      try {
        final FileChannel toChannel = ((FileOutputStream)outputStream).getChannel();
        try {
          fromChannel.transferTo(0, Long.MAX_VALUE, toChannel);
        }
        finally {
          toChannel.close();
        }
      }
      finally {
        fromChannel.close();
      }
    }
    else {
      final byte[] buffer = BUFFER.get();
      while (true) {
        int read = inputStream.read(buffer);
        if (read < 0) break;
        outputStream.write(buffer, 0, read);
      }
    }
  }

  public static int getUserFileSizeLimit() {
    try {
      return Integer.parseInt(System.getProperty("idea.max.intellisense.filesize")) * KILOBYTE;
    }
    catch (NumberFormatException e) {
      return 2500 * KILOBYTE;
    }
  }
}
