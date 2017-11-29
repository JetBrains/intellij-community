/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.Map;
import java.util.Set;

/**
 * @author traff
 */
public class TarUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.io.TarUtil");

  private TarUtil() {}

  @NotNull
  public static TarArchiveOutputStream getTarGzOutputStream(File zipFile) throws IOException {
    FileOutputStream fos = new FileOutputStream(zipFile);
    GzipCompressorOutputStream gcos = new GzipCompressorOutputStream(fos);
    TarArchiveOutputStream zip = new TarArchiveOutputStream(gcos);
    zip.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
    return zip;
  }

  public interface FileContentProcessor {

    FileContentProcessor STANDARD = new FileContentProcessor() {
      @Override
      public InputStream getContent(File file) throws IOException {
        return new FileInputStream(file);
      }
    };

    InputStream getContent(File file) throws IOException;
  }

  public static boolean addFileToTar(@NotNull TarArchiveOutputStream tos,
                                     @NotNull File file,
                                     @NotNull String relativeName,
                                     @Nullable Set<String> writtenItemRelativePaths,
                                     @Nullable FileFilter fileFilter) throws IOException {
    return addFileToTar(tos, file, relativeName, writtenItemRelativePaths, fileFilter, FileContentProcessor.STANDARD);
  }

  /*
   * Adds a new file entry to the TAR output stream.
   */
  public static boolean addFileToTar(@NotNull TarArchiveOutputStream tos,
                                     @NotNull File file,
                                     @NotNull String relativeName,
                                     @Nullable Set<String> writtenItemRelativePaths,
                                     @Nullable FileFilter fileFilter,
                                     @NotNull FileContentProcessor contentProcessor) throws IOException {
    while (!relativeName.isEmpty() && relativeName.charAt(0) == '/') {
      relativeName = relativeName.substring(1);
    }

    boolean isDir = file.isDirectory();
    if (isDir) {
      return true;
    }

    if (fileFilter != null && !FileUtil.isFilePathAcceptable(file, fileFilter)) return false;
    if (writtenItemRelativePaths != null && !writtenItemRelativePaths.add(relativeName)) return false;

    if (LOG.isDebugEnabled()) {
      LOG.debug("Add " + file + " as " + relativeName);
    }

    long size = file.length();
    TarArchiveEntry e = new TarArchiveEntry(relativeName);

    e.setModTime(file.lastModified());
    e.setSize(size);
    tos.putArchiveEntry(e);
    InputStream is = contentProcessor.getContent(file);
    try {
      FileUtil.copy(is, tos);
    }
    finally {
      is.close();
    }
    tos.closeArchiveEntry();
    return true;
  }

  public static boolean addFileOrDirRecursively(@NotNull TarArchiveOutputStream tarOutputStream,
                                                @Nullable File tarFile,
                                                @NotNull File file,
                                                @NotNull String relativePath,
                                                @Nullable FileFilter fileFilter,
                                                @Nullable Set<String> writtenItemRelativePaths) throws IOException {
    if (file.isDirectory()) {
      return addDirToTarRecursively(tarOutputStream, tarFile, file, relativePath, fileFilter, writtenItemRelativePaths);
    }
    addFileToTar(tarOutputStream, file, relativePath, writtenItemRelativePaths, fileFilter);
    return true;
  }

  public static boolean addDirToTarRecursively(@NotNull TarArchiveOutputStream outputStream,
                                               @Nullable File tarFile,
                                               @NotNull File dir,
                                               @NotNull String relativePath,
                                               @Nullable FileFilter fileFilter,
                                               @Nullable Set<String> writtenItemRelativePaths) throws IOException {
    if (tarFile != null && FileUtil.isAncestor(dir, tarFile, false)) {
      return false;
    }
    if (!relativePath.isEmpty()) {
      addFileToTar(outputStream, dir, relativePath, writtenItemRelativePaths, fileFilter);
    }
    final File[] children = dir.listFiles();
    if (children != null) {
      for (File child : children) {
        final String childRelativePath = (relativePath.isEmpty() ? "" : relativePath + "/") + child.getName();
        addFileOrDirRecursively(outputStream, tarFile, child, childRelativePath, fileFilter, writtenItemRelativePaths);
      }
    }
    return true;
  }

  public static void extract(@NotNull File file, @NotNull File outputDir, @Nullable FilenameFilter filenameFilter) throws IOException {
    extract(file, outputDir, filenameFilter, true);
  }

  public static void extract(@NotNull File file, @NotNull File outputDir, @Nullable FilenameFilter filenameFilter, boolean overwrite)
    throws IOException {
    FileInputStream fis = new FileInputStream(file);
    GzipCompressorInputStream gcis = new GzipCompressorInputStream(fis);
    TarArchiveInputStream tis = new TarArchiveInputStream(gcis);
    try {
      extract(tis, outputDir, filenameFilter, overwrite);
    }
    finally {
      fis.close();
    }
  }

  public static void extract(@NotNull final TarArchiveInputStream tis,
                             @NotNull File outputDir,
                             @Nullable FilenameFilter filenameFilter) throws IOException {
    extract(tis, outputDir, filenameFilter, true);
  }

  public static void extract(@NotNull final TarArchiveInputStream tis,
                             @NotNull File outputDir,
                             @Nullable FilenameFilter filenameFilter,
                             boolean overwrite) throws IOException {
    TarArchiveEntry entry;
    while ((entry = tis.getNextTarEntry()) != null) {
      final File file = new File(outputDir, entry.getName());
      if (filenameFilter == null || filenameFilter.accept(file.getParentFile(), file.getName())) {
        extractEntry(entry, tis, outputDir, overwrite);
      }
    }
  }

  public static void extractEntry(TarArchiveEntry entry, final InputStream inputStream, File outputDir) throws IOException {
    extractEntry(entry, inputStream, outputDir, true);
  }

  public static void extractEntry(TarArchiveEntry entry, final InputStream inputStream, File outputDir, boolean overwrite)
    throws IOException {
    final boolean isDirectory = entry.isDirectory();
    final String relativeName = entry.getName();
    final File file = new File(outputDir, relativeName);
    if (file.exists() && !overwrite) return;

    FileUtil.createParentDirs(file);
    if (isDirectory) {
      file.mkdir();
    }
    else {
      if (entry.getSize() > Integer.MAX_VALUE) {
        throw new IllegalArgumentException("Tar entries bigger then " + Integer.MAX_VALUE + " aren't supported");
      }
      int len = (int)entry.getSize();

      byte[] content = new byte[len];

      int n = 0;
      while (n < len) {
        int count = inputStream.read(content, n, len - n);
        if (count < 0) {
          throw new EOFException();
        }
        n += count;
      }

      FileUtil.writeToFile(file, content, false);
    }
  }

  /*
   * update an existing jar file. Adds/replace files specified in relpathToFile map
   */
  public static void update(InputStream in, OutputStream out, Map<String, File> relpathToFile) throws IOException {
    TarArchiveInputStream tis = new TarArchiveInputStream(in);
    TarArchiveOutputStream tos = new TarArchiveOutputStream(out);

    try {
      // put the old entries first, replace if necessary
      TarArchiveEntry e;
      while ((e = tis.getNextTarEntry()) != null) {
        String name = e.getName();

        if (!relpathToFile.containsKey(name)) { // copy the old stuff
          // do our own compression
          TarArchiveEntry e2 = new TarArchiveEntry(name);
          //e2.setMethod(e.getMethod());
          e2.setModTime(e.getModTime());
          //e2.setComment(e.getComment());
          //e2.setExtra(e.getExtra());
          //if (e.getMethod() == ZipEntry.STORED) {
          e2.setSize(e.getSize());
          //e2.setCrc(e.getCrc());
          //}
          tos.putArchiveEntry(e2);
          FileUtil.copy(tis, tos);
        }
        else { // replace with the new files
          final File file = relpathToFile.get(name);
          //addFile(file, name, tos);
          relpathToFile.remove(name);
          addFileToTar(tos, file, name, null, null);
        }
      }

      // add the remaining new files
      for (final String path : relpathToFile.keySet()) {
        File file = relpathToFile.get(path);
        addFileToTar(tos, file, path, null, null);
      }
    }
    finally {
      tis.close();
      tos.close();
    }
  }
}