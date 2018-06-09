// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
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
}