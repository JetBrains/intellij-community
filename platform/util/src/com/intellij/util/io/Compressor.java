// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.function.BiPredicate;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public abstract class Compressor implements Closeable {
  public static class Tar extends Compressor {
    public enum Compression { GZIP, BZIP2, NONE }

    public Tar(@NotNull File file, @NotNull Compression compression) throws IOException {
      this(new FileOutputStream(file), compression);
    }

    //<editor-fold desc="Implementation">
    private final TarArchiveOutputStream myStream;

    public Tar(@NotNull OutputStream stream, @NotNull Compression compression) throws IOException {
      myStream = new TarArchiveOutputStream(compressedStream(stream, compression));
      myStream.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
    }

    private static OutputStream compressedStream(OutputStream stream, Compression compression) throws IOException {
      if (compression == Compression.GZIP) return new GzipCompressorOutputStream(stream);
      if (compression == Compression.BZIP2) return new BZip2CompressorOutputStream(stream);
      return stream;
    }

    @Override
    protected void writeDirectoryEntry(String name, long timestamp) throws IOException {
      TarArchiveEntry e = new TarArchiveEntry(name + '/');
      e.setModTime(timestamp);
      myStream.putArchiveEntry(e);
      myStream.closeArchiveEntry();
    }

    @Override
    protected void writeFileEntry(String name, InputStream source, long length, long timestamp) throws IOException {
      TarArchiveEntry e = new TarArchiveEntry(name);
      e.setSize(length);
      e.setModTime(timestamp);
      myStream.putArchiveEntry(e);
      FileUtilRt.copy(source, myStream);
      myStream.closeArchiveEntry();
    }

    @Override
    public void close() throws IOException {
      myStream.close();
    }
    //</editor-fold>
  }

  public static class Zip extends Compressor {
    public Zip(@NotNull File file) throws FileNotFoundException {
      this(new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(file))));
    }

    public Zip(@NotNull OutputStream stream) {
      this(new ZipOutputStream(stream));
    }

    public Zip withLevel(int compressionLevel) {
      myStream.setLevel(compressionLevel);
      return this;
    }

    //<editor-fold desc="Implementation">
    private final ZipOutputStream myStream;

    protected Zip(ZipOutputStream stream) {
      myStream = stream;
    }

    @Override
    protected void writeDirectoryEntry(String name, long timestamp) throws IOException {
      ZipEntry e = new ZipEntry(name + '/');
      e.setMethod(ZipEntry.STORED);
      e.setSize(0);
      e.setCrc(0);
      e.setTime(timestamp);
      myStream.putNextEntry(e);
      myStream.closeEntry();
    }

    @Override
    protected void writeFileEntry(String name, InputStream source, long length, long timestamp) throws IOException {
      ZipEntry e = new ZipEntry(name);
      if (length == 0) {
        e.setMethod(ZipEntry.STORED);
        e.setSize(0);
        e.setCrc(0);
      }
      e.setTime(timestamp);
      myStream.putNextEntry(e);
      FileUtilRt.copy(source, myStream);
      myStream.closeEntry();
    }

    @Override
    public void close() throws IOException {
      myStream.close();
    }
    //</editor-fold>
  }

  public static class Jar extends Zip {
    public Jar(@NotNull File file) throws IOException {
      super(new JarOutputStream(new BufferedOutputStream(new FileOutputStream(file))));
    }

    public final void addManifest(@NotNull Manifest manifest) throws IOException {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      manifest.write(buffer);
      addFile(JarFile.MANIFEST_NAME, buffer.toByteArray());
    }
  }

  private BiPredicate<String, File> myFilter;
  private BiPredicate<String, Path> nioFilter;

  /**
   * Allows filtering entries being added to the archive.
   * Please note that <b>the second parameter of a filter ({@code File}) could be {@code null}</b> when the filter is applied
   * to an entry not present on a disk - e.g. via {@link #addFile(String, byte[])}.
   */
  public Compressor filter(@Nullable BiPredicate<String, @Nullable File> filter) {
    myFilter = filter;
    return this;
  }

  /**
   * Allows filtering entries being added to the archive.
   * Please note that <b>the second parameter of a filter ({@code File}) could be {@code null}</b> when the filter is applied
   * to an entry not present on a disk - e.g. via {@link #addFile(String, byte[])}.
   */
  public Compressor nioFilter(@Nullable BiPredicate<String, @Nullable Path> filter) {
    nioFilter = filter;
    return this;
  }

  public final void addFile(@NotNull String entryName, @NotNull File file) throws IOException {
    if (accepts(entryName, file)) {
      try (InputStream source = new FileInputStream(file)) {
        writeFileEntry(entryName, source, file.length(), file.lastModified());
      }
    }
  }

  public final void addFile(@NotNull String entryName, @NotNull Path file) throws IOException {
    if (nioFilter == null || nioFilter.test(entryName, file)) {
      try (InputStream source = Files.newInputStream(file)) {
        BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
        writeFileEntry(entryName, source, attributes.size(), attributes.lastModifiedTime().toMillis());
      }
    }
  }

  public final void addFile(@NotNull String entryName, byte @NotNull [] content) throws IOException {
    addFile(entryName, content, -1);
  }

  public final void addFile(@NotNull String entryName, byte @NotNull [] content, long timestamp) throws IOException {
    entryName = entryName(entryName);
    if (accepts(entryName, null)) {
      writeFileEntry(entryName, new ByteArrayInputStream(content), content.length, timestamp(timestamp));
    }
  }

  public final void addFile(@NotNull String entryName, @NotNull InputStream content) throws IOException {
    addFile(entryName, content, -1);
  }

  public final void addFile(@NotNull String entryName, @NotNull InputStream content, long timestamp) throws IOException {
    entryName = entryName(entryName);
    if (accepts(entryName, null)) {
      writeFileEntry(entryName, content, -1, timestamp(timestamp));
    }
  }

  public final void addDirectory(@NotNull String entryName) throws IOException {
    addDirectory(entryName, -1);
  }

  public final void addDirectory(@NotNull String entryName, long timestamp) throws IOException {
    entryName = entryName(entryName);
    if (accepts(entryName, null)) {
      writeDirectoryEntry(entryName, timestamp(timestamp));
    }
  }

  public final void addDirectory(@NotNull File directory) throws IOException {
    addRecursively("", directory);
  }

  public final void addDirectory(@NotNull String prefix, @NotNull File directory) throws IOException {
    addRecursively(entryName(prefix), directory);
  }

  public final void addDirectory(@NotNull String prefix, @NotNull Path directory) throws IOException {
    addRecursively(entryName(prefix), directory);
  }

  //<editor-fold desc="Internal interface">
  protected Compressor() { }

  private static String entryName(String name) {
    String entryName = StringUtil.trimLeading(StringUtil.trimTrailing(name.replace('\\', '/'), '/'), '/');
    if (StringUtil.isEmpty(entryName)) {
      throw new IllegalArgumentException("Invalid entry name: " + name);
    }
    return entryName;
  }

  private static long timestamp(long timestamp) {
    return timestamp == -1 ? System.currentTimeMillis() : timestamp;
  }

  private boolean accepts(String entryName, @Nullable File file) {
    if (myFilter == null) {
      return file != null || nioFilter == null || nioFilter.test(entryName, null);
    }
    else {
      return myFilter.test(entryName, file);
    }
  }

  private void addRecursively(String prefix, File directory) throws IOException {
    if (!accepts(prefix, directory)) {
      return;
    }

    if (!prefix.isEmpty()) {
      writeDirectoryEntry(prefix, directory.lastModified());
    }

    File[] children = directory.listFiles();
    if (children != null) {
      for (File child: children) {
        String name = prefix.isEmpty() ? child.getName() : prefix + '/' + child.getName();
        if (child.isDirectory()) {
          addRecursively(name, child);
        }
        else {
          addFile(name, child);
        }
      }
    }
  }

  private void addRecursively(@NotNull String prefix, @NotNull Path dir) throws IOException {
    if (nioFilter == null || nioFilter.test(prefix, dir)) {
      addRecursively(prefix, dir, Files.readAttributes(dir, BasicFileAttributes.class));
    }
  }

  private void addRecursively(@NotNull String prefix, @NotNull Path dir, @NotNull BasicFileAttributes dirAttributes) throws IOException {
    if (!prefix.isEmpty()) {
      writeDirectoryEntry(prefix, dirAttributes.lastModifiedTime().toMillis());
    }

    try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
      for (Path child : stream) {
        String name = prefix.isEmpty() ? child.getFileName().toString() : prefix + '/' + child.getFileName().toString();
        if (nioFilter != null && !nioFilter.test(name, dir)) {
          continue;
        }

        BasicFileAttributes attributes = Files.readAttributes(child, BasicFileAttributes.class);
        if (attributes.isDirectory()) {
          addRecursively(name, child, attributes);
        }
        else {
          try (InputStream source = Files.newInputStream(child)) {
            writeFileEntry(name, source, attributes.size(), attributes.lastModifiedTime().toMillis());
          }
        }
      }
    }
  }

  protected abstract void writeDirectoryEntry(String name, long timestamp) throws IOException;
  protected abstract void writeFileEntry(String name, InputStream source, long length, long timestamp) throws IOException;
  //</editor-fold>
}