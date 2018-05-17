// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.Enumeration;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ZipUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.io.ZipUtil");

  private ZipUtil() {}

  public interface FileContentProcessor {
    FileContentProcessor STANDARD = new FileContentProcessor() {
      @Override
      public InputStream getContent(File file) throws IOException {
        return new FileInputStream(file);
      }
    };

    InputStream getContent(File file) throws IOException;
  }

  public static boolean addFileToZip(@NotNull ZipOutputStream zos,
                                     @NotNull File file,
                                     @NotNull String relativeName,
                                     @Nullable Set<String> writtenItemRelativePaths,
                                     @Nullable FileFilter fileFilter) throws IOException {
    return addFileToZip(zos, file, relativeName, writtenItemRelativePaths, fileFilter, FileContentProcessor.STANDARD);
  }

  /*
   * Adds a new file entry to the ZIP output stream.
   */
  public static boolean addFileToZip(@NotNull ZipOutputStream zos,
                                     @NotNull File file,
                                     @NotNull String relativeName,
                                     @Nullable Set<String> writtenItemRelativePaths,
                                     @Nullable FileFilter fileFilter,
                                     @NotNull FileContentProcessor contentProcessor) throws IOException {
    while (!relativeName.isEmpty() && relativeName.charAt(0) == '/') {
      relativeName = relativeName.substring(1);
    }

    boolean isDir = file.isDirectory();
    if (isDir && !StringUtil.endsWithChar(relativeName, '/')) {
      relativeName += "/";
    }
    if (fileFilter != null && !FileUtil.isFilePathAcceptable(file, fileFilter)) return false;
    if (writtenItemRelativePaths != null && !writtenItemRelativePaths.add(relativeName)) return false;

    if (LOG.isDebugEnabled()) {
      LOG.debug("Add "+file+" as "+relativeName);
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
      InputStream is = contentProcessor.getContent(file);
      try {
        FileUtil.copy(is, zos);
      }
      finally {
        is.close();
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
    addFileToZip(jarOutputStream, file, relativePath, writtenItemRelativePaths, fileFilter);
    return true;
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
      addFileToZip(outputStream, dir, relativePath, writtenItemRelativePaths, fileFilter);
    }
    final File[] children = dir.listFiles();
    if (children != null) {
      for (File child : children) {
        final String childRelativePath = (relativePath.isEmpty() ? "" : relativePath + "/") + child.getName();
        addFileOrDirRecursively(outputStream, jarFile, child, childRelativePath, fileFilter, writtenItemRelativePaths);
      }
    }
    return true;
  }

  public static void extract(@NotNull File file, @NotNull File outputDir, @Nullable FilenameFilter filenameFilter) throws IOException {
    extract(file, outputDir, filenameFilter, true);
  }

  public static void extract(@NotNull File file, @NotNull File outputDir, @Nullable FilenameFilter filenameFilter, boolean overwrite) throws IOException {
    final ZipFile zipFile = new ZipFile(file);
    try {
      extract(zipFile, outputDir, filenameFilter, overwrite);
    }
    finally {
      zipFile.close();
    }
  }

  public static void extract(@NotNull final ZipFile zipFile,
                             @NotNull File outputDir,
                             @Nullable FilenameFilter filenameFilter) throws IOException {
    extract(zipFile, outputDir, filenameFilter, true);
  }

  public static void extract(@NotNull final ZipFile zipFile,
                             @NotNull File outputDir,
                             @Nullable FilenameFilter filenameFilter,
                             boolean overwrite) throws IOException {
    final Enumeration entries = zipFile.entries();
    while (entries.hasMoreElements()) {
      ZipEntry entry = (ZipEntry)entries.nextElement();
      final File file = createFileForEntry(outputDir, entry.getName());
      if (filenameFilter == null || filenameFilter.accept(file.getParentFile(), file.getName())) {
        extractEntry(entry.isDirectory() ? null : zipFile.getInputStream(entry), file, overwrite);
      }
    }
  }

  @NotNull
  public static File createFileForEntry(@NotNull File outputDir, @NotNull String entryName) throws IOException {
    File result = new File(outputDir, entryName);
    // we cannot use Path, but File doesn't provide Path.normalize,
    // so, our FileUtil.toCanonicalPath is used to normalized (isAncestor uses it under the hood)
    if (entryName.contains("..") && !FileUtil.isAncestor(outputDir, result, true)) {
      throw new IOException("Invalid entry name: " + entryName);
    }
    return result;
  }

  public static void extractEntry(@NotNull ZipEntry entry, @NotNull InputStream inputStream, @NotNull File outputDir) throws IOException {
    extractEntry(entry, inputStream, outputDir, true);
  }

  public static void extractEntry(@NotNull ZipEntry entry, @NotNull InputStream inputStream, @NotNull File outputDir, boolean isOverwrite) throws IOException {
    File outputFile = createFileForEntry(outputDir, entry.getName());
    extractEntry(entry.isDirectory() ? null : inputStream, outputFile, isOverwrite);
  }

  public static void extractEntry(@Nullable InputStream inputStream, @NotNull File outputFile, boolean isOverwrite) throws IOException {
    if (outputFile.exists()) {
      if (!isOverwrite || inputStream == null) {
        return;
      }
    }
    else if (inputStream == null) {
      //noinspection ResultOfMethodCallIgnored
      outputFile.mkdirs();
      return;
    }
    else {
      FileUtilRt.createParentDirs(outputFile);
    }

    final FileOutputStream os = new FileOutputStream(outputFile);
    try {
      FileUtilRt.copy(inputStream, os);
    }
    finally {
      try {
        os.close();
      }
      finally {
        inputStream.close();
      }
    }
  }

  @SuppressWarnings("unused")
  public static boolean isZipContainsFolder(File zip) throws IOException {
    ZipFile zipFile = new ZipFile(zip);
    try {
      Enumeration en = zipFile.entries();

      while (en.hasMoreElements()) {
        ZipEntry zipEntry = (ZipEntry)en.nextElement();

        // we do not necessarily get a separate entry for the subdirectory when the file
        // in the ZIP archive is placed in a subdirectory, so we need to check if the slash
        // is found anywhere in the path
        if (zipEntry.getName().indexOf('/') >= 0) {
          return true;
        }
      }
      zipFile.close();
      return false;
    }
    finally {
      zipFile.close();
    }
  }

  @SuppressWarnings("unused")
  public static boolean isZipContainsEntry(File zip, String relativePath) throws IOException {
    ZipFile zipFile = new ZipFile(zip);
    try {
      Enumeration en = zipFile.entries();

      while (en.hasMoreElements()) {
        ZipEntry zipEntry = (ZipEntry)en.nextElement();
        if (relativePath.equals(zipEntry.getName())) {
          return true;
        }
      }
      zipFile.close();
      return false;
    }
    finally {
      zipFile.close();
    }
  }

  /*
   * update an existing jar file. Adds/replace files specified in relPathToFile map
   */
  public static void update(InputStream in, OutputStream out, Map<String, File> relPathToFile) throws IOException {
    ZipInputStream zis = new ZipInputStream(in);
    ZipOutputStream zos = new ZipOutputStream(out);

    try {
      // put the old entries first, replace if necessary
      ZipEntry e;
      while ((e = zis.getNextEntry()) != null) {
        String name = e.getName();

        if (!relPathToFile.containsKey(name)) { // copy the old stuff
          // do our own compression
          ZipEntry e2 = new ZipEntry(name);
          e2.setMethod(e.getMethod());
          e2.setTime(e.getTime());
          e2.setComment(e.getComment());
          e2.setExtra(e.getExtra());
          if (e.getMethod() == ZipEntry.STORED) {
            e2.setSize(e.getSize());
            e2.setCrc(e.getCrc());
          }
          zos.putNextEntry(e2);
          FileUtil.copy(zis, zos);
        }
        else { // replace with the new files
          final File file = relPathToFile.get(name);
          //addFile(file, name, zos);
          relPathToFile.remove(name);
          addFileToZip(zos, file, name, null, null);
        }
      }

      // add the remaining new files
      for (final String path : relPathToFile.keySet()) {
        File file = relPathToFile.get(path);
        addFileToZip(zos, file, path, null, null);
      }
    }
    finally {
      zis.close();
      zos.close();
    }
  }

  public static void compressFile(@NotNull File srcFile, @NotNull File zipFile) throws IOException {
    InputStream is = new FileInputStream(srcFile);
    try {
      ZipOutputStream os = new ZipOutputStream(new FileOutputStream(zipFile));
      try {
        os.putNextEntry(new ZipEntry(srcFile.getName()));
        FileUtilRt.copy(is, os);
        os.closeEntry();
      }
      finally {
        os.close();
      }
    }
    finally {
      is.close();
    }
  }
}