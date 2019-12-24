// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.Enumeration;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class ZipUtil {
  private static final Logger LOG = Logger.getInstance(ZipUtil.class);

  private ZipUtil() { }

  public interface FileContentProcessor {
    FileContentProcessor STANDARD = new FileContentProcessor() {
      @Override
      public InputStream getContent(@NotNull File file) throws IOException {
        return new FileInputStream(file);
      }
    };

    InputStream getContent(@NotNull File file) throws IOException;
  }

  public static boolean addFileToZip(@NotNull ZipOutputStream zos,
                                     @NotNull File file,
                                     @NotNull String relativeName,
                                     @Nullable Set<? super String> writtenItemRelativePaths,
                                     @Nullable FileFilter fileFilter) throws IOException {
    return addFileToZip(zos, file, relativeName, writtenItemRelativePaths, fileFilter, FileContentProcessor.STANDARD, file.isDirectory());
  }

  /*
   * Adds a new file entry to the ZIP output stream.
   */
  public static boolean addFileToZip(@NotNull ZipOutputStream zos,
                                     @NotNull File file,
                                     @NotNull String relativeName,
                                     @Nullable Set<? super String> writtenItemRelativePaths,
                                     @Nullable FileFilter fileFilter,
                                     @NotNull FileContentProcessor contentProcessor,
                                     boolean isDir) throws IOException {
    while (!relativeName.isEmpty() && relativeName.charAt(0) == '/') {
      relativeName = relativeName.substring(1);
    }

    if (isDir && !StringUtil.endsWithChar(relativeName, '/')) {
      relativeName += "/";
    }
    if (fileFilter != null && !FileUtil.isFilePathAcceptable(file, fileFilter)) return false;
    if (writtenItemRelativePaths != null && !writtenItemRelativePaths.add(relativeName)) return false;

    if (LOG.isDebugEnabled()) {
      LOG.debug("Add " + file + " as " + relativeName);
    }

    long size = isDir ? 0 : file.length();
    ZipEntry e = new ZipEntry(relativeName);
    e.setTime(file.lastModified());
    if (size == 0) {
      e.setMethod(ZipEntry.STORED);
      e.setSize(0);
      e.setCrc(0);
    }
    zos.putNextEntry(e);
    if (!isDir) {
      try (InputStream is = contentProcessor.getContent(file)) {
        FileUtilRt.copy(is, zos);
      }
    }
    zos.closeEntry();
    return true;
  }

  public static boolean addFileOrDirRecursively(@NotNull ZipOutputStream jarOutputStream,
                                                @Nullable File jarFile,
                                                @NotNull File file,
                                                @NotNull String relativePath,
                                                @Nullable FileFilter fileFilter,
                                                @Nullable Set<String> writtenItemRelativePaths) throws IOException {
    if (file.isDirectory()) {
      return addDirToZipRecursively(jarOutputStream, jarFile, file, relativePath, fileFilter, writtenItemRelativePaths);
    }
    else {
      return addFileToZip(jarOutputStream, file, relativePath, writtenItemRelativePaths, fileFilter);
    }
  }

  public static boolean addDirToZipRecursively(@NotNull ZipOutputStream outputStream,
                                               @Nullable File jarFile,
                                               @NotNull File dir,
                                               @NotNull String relativePath,
                                               @Nullable FileFilter fileFilter,
                                               @Nullable Set<String> writtenItemRelativePaths) throws IOException {
    if (jarFile != null && FileUtil.isAncestor(dir, jarFile, false)) {
      return false;
    }
    if (!relativePath.isEmpty()) {
      addFileToZip(outputStream, dir, relativePath, writtenItemRelativePaths, fileFilter, FileContentProcessor.STANDARD, true);
    }

    File[] children = dir.listFiles();
    if (children != null) {
      for (File child : children) {
        String childRelativePath = (relativePath.isEmpty() ? "" : relativePath + "/") + child.getName();
        addFileOrDirRecursively(outputStream, jarFile, child, childRelativePath, fileFilter, writtenItemRelativePaths);
      }
    }
    return true;
  }

  /** @see Decompressor.Zip */
  public static void extract(@NotNull File file, @NotNull File outputDir, @Nullable FilenameFilter filter) throws IOException {
    new Decompressor.Zip(file).filter(FileFilterAdapter.wrap(outputDir, filter)).extract(outputDir);
  }

  /** @see Decompressor.Zip */
  public static void extract(@NotNull File file, @NotNull File outputDir, @Nullable FilenameFilter filter, boolean overwrite) throws IOException {
    new Decompressor.Zip(file).filter(FileFilterAdapter.wrap(outputDir, filter)).overwrite(overwrite).extract(outputDir);
  }

  private static class FileFilterAdapter implements Condition<String> {
    private static FileFilterAdapter wrap(File outputDir, @Nullable FilenameFilter filter) {
      return filter == null ? null : new FileFilterAdapter(outputDir, filter);
    }

    private final File myOutputDir;
    private final FilenameFilter myFilter;

    private FileFilterAdapter(File outputDir, FilenameFilter filter) {
      myOutputDir = outputDir;
      myFilter = filter;
    }

    @Override
    public boolean value(String entryName) {
      File outputFile = new File(myOutputDir, entryName);
      return myFilter.accept(outputFile.getParentFile(), outputFile.getName());
    }
  }

  @SuppressWarnings("unused")
  public static boolean isZipContainsFolder(File zip) throws IOException {
    try (ZipFile zipFile = new ZipFile(zip)) {
      Enumeration<? extends ZipEntry> en = zipFile.entries();
      while (en.hasMoreElements()) {
        ZipEntry zipEntry = en.nextElement();
        // we do not necessarily get a separate entry for the subdirectory when the file
        // in the ZIP archive is placed in a subdirectory, so we need to check if the slash
        // is found anywhere in the path
        if (zipEntry.getName().indexOf('/') >= 0) {
          return true;
        }
      }
      return false;
    }
  }

  public static void compressFile(@NotNull File srcFile, @NotNull File zipFile) throws IOException {
    try (InputStream is = new FileInputStream(srcFile)) {
      try (ZipOutputStream os = new ZipOutputStream(new FileOutputStream(zipFile))) {
        os.putNextEntry(new ZipEntry(srcFile.getName()));
        FileUtilRt.copy(is, os);
        os.closeEntry();
      }
    }
  }

  //<editor-fold desc="Deprecated stuff.">
  /** @deprecated use {@link Decompressor.Zip} */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
  public static void extract(@NotNull ZipFile zip, @NotNull File outputDir, @Nullable FilenameFilter filter) throws IOException {
    new Decompressor.Zip(new File(zip.getName())).filter(FileFilterAdapter.wrap(outputDir, filter)).extract(outputDir);
  }

  /** @deprecated use {@link Decompressor.Zip} */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
  public static void extractEntry(@NotNull ZipEntry entry, @NotNull InputStream inputStream, @NotNull File outputDir, boolean overwrite) throws IOException {
    File outputFile = Decompressor.entryFile(outputDir, entry.getName());
    try {
      if (entry.isDirectory()) {
        FileUtil.createDirectory(outputFile);
      }
      else if (!outputFile.exists() || overwrite) {
        FileUtil.createParentDirs(outputFile);
        try (FileOutputStream os = new FileOutputStream(outputFile)) {
          FileUtilRt.copy(inputStream, os);
        }
      }
    }
    finally {
      inputStream.close();
    }
  }
  //</editor-fold>
}