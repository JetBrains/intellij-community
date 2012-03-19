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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.*;
import java.util.UUID;

/**
 * This is a light version of {@link FileUtil}.
 * It is used by scripts loaded externally and almost independent of IDEA, so add here as few dependencies as possible.
 */
public class FileUtilLight {

  private static String ourCanonicalTempPathCache = null;
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.io.FileUtilLight");


  @NotNull
  public static File createTempDirectory(@NotNull @NonNls String prefix, @Nullable @NonNls String suffix) throws IOException {
    File file = doCreateTempFile(prefix, suffix);
    file.delete();
    file.mkdir();
    file.deleteOnExit();
    return file;
  }

  @NotNull
  public static File createTempDirectory(File dir, @NotNull @NonNls String prefix, @Nullable @NonNls String suffix) throws IOException {
    File file = doCreateTempFile(prefix, suffix, dir);
    file.delete();
    file.mkdir();
    file.deleteOnExit();
    return file;
  }

  @NotNull
  public static File createTempFile(@NonNls final File dir, @NotNull @NonNls String prefix, @Nullable @NonNls String suffix, final boolean create)
    throws IOException {
    return createTempFile(dir, prefix, suffix, create, true);
  }

  public static File createTempFile(@NonNls final File dir,
                                    @NotNull @NonNls String prefix,
                                    @Nullable @NonNls String suffix,
                                    final boolean create,
                                    boolean deleteOnExit) throws IOException {
    File file = doCreateTempFile(prefix, suffix, dir);
    file.delete();
    if (create) {
      file.createNewFile();
    }
    if (deleteOnExit) {
      file.deleteOnExit();
    }
    return file;
  }

  @NotNull
  public static File createTempFile(@NotNull @NonNls String prefix, @Nullable @NonNls String suffix) throws IOException {
    return createTempFile(prefix, suffix, false); //false until TeamCity fixes its plugin
  }

  @NotNull
  public static File createTempFile(@NotNull @NonNls String prefix, @Nullable @NonNls String suffix, boolean deleteOnExit) throws IOException {
    File file = doCreateTempFile(prefix, suffix);
    file.delete();
    file.createNewFile();
    if (deleteOnExit) {
      file.deleteOnExit();
    }
    return file;
  }

  @NotNull
  private static File doCreateTempFile(String prefix, String suffix) throws IOException {
    return doCreateTempFile(prefix, suffix, new File(getTempDirectory()));
  }

  @NotNull
  private static File doCreateTempFile(@NotNull String prefix, String suffix, final File dir) throws IOException {
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
    return SystemInfo.isWindows && canonical.getAbsolutePath().contains(" ") ? temp.getAbsoluteFile() : canonical;
  }

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
      if (!SystemInfo.isWindows || !canonical.contains(" ")) {
        return canonical;
      }
    }
    catch (IOException ignore) {
    }
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
   * @throws IOException if there is a problem with setting the flag
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
  public static String loadFile(@NotNull File file, String encoding) throws IOException {
    return loadFile(file, encoding, false);
  }

  @NotNull
  public static String loadFile(@NotNull File file, String encoding, boolean convertLineSeparators) throws IOException {
    final String s = new String(loadFileText(file, encoding));
    return convertLineSeparators ? StringUtil.convertLineSeparators(s) : s;
  }

  @NotNull
  public static char[] loadFileText(@NotNull File file) throws IOException {
    return loadFileText(file, null);
  }

  @NotNull
  public static char[] loadFileText(@NotNull File file, @NonNls String encoding) throws IOException {
    InputStream stream = new FileInputStream(file);
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

}
