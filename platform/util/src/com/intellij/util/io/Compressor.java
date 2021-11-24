// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
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
      if (length < 0) {
        if (source instanceof ByteArrayInputStream || source instanceof UnsyncByteArrayInputStream) {
          length = source.available();
        }
        else {
          BufferExposingByteArrayOutputStream temp = new BufferExposingByteArrayOutputStream();
          StreamUtil.copy(source, temp);
          length = temp.size();
          source = new ByteArrayInputStream(temp.getInternalBuffer(), 0, temp.size());
        }
      }
      e.setSize(length);
      e.setModTime(timestamp);
      myStream.putArchiveEntry(e);
      if (length > 0) {
        StreamUtil.copy(source, myStream);
      }
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
      if (length != 0) {
        StreamUtil.copy(source, myStream);
      }
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

  private @Nullable BiPredicate<? super String, ? super @Nullable Path> myFilter;

  /**
   * Allows filtering entries being added to the archive.
   * Please note that the second parameter of a filter ({@code Path}) <b>might be {@code null}</b> when it is applied
   * to an entry not present on a disk - e.g. via {@link #addFile(String, byte[])}.
   */
  public Compressor filter(@Nullable BiPredicate<? super String, ? super @Nullable Path> filter) {
    myFilter = filter;
    return this;
  }

  public final void addFile(@NotNull String entryName, @NotNull File file) throws IOException {
    addFile(entryName, file.toPath());
  }

  public final void addFile(@NotNull String entryName, @NotNull Path file) throws IOException {
    entryName = entryName(entryName);
    if (accept(entryName, file)) {
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
    if (accept(entryName, null)) {
      writeFileEntry(entryName, new ByteArrayInputStream(content), content.length, timestamp(timestamp));
    }
  }

  public final void addFile(@NotNull String entryName, @NotNull InputStream content) throws IOException {
    addFile(entryName, content, -1);
  }

  public final void addFile(@NotNull String entryName, @NotNull InputStream content, long timestamp) throws IOException {
    entryName = entryName(entryName);
    if (accept(entryName, null)) {
      writeFileEntry(entryName, content, -1, timestamp(timestamp));
    }
  }

  public final void addDirectory(@NotNull String entryName) throws IOException {
    addDirectory(entryName, -1);
  }

  public final void addDirectory(@NotNull String entryName, long timestamp) throws IOException {
    entryName = entryName(entryName);
    if (accept(entryName, null)) {
      writeDirectoryEntry(entryName, timestamp(timestamp));
    }
  }

  public final void addDirectory(@NotNull File directory) throws IOException {
    addDirectory(directory.toPath());
  }

  public final void addDirectory(@NotNull Path directory) throws IOException {
    addRecursively("", directory);
  }

  public final void addDirectory(@NotNull String prefix, @NotNull File directory) throws IOException {
    addDirectory(prefix, directory.toPath());
  }

  public final void addDirectory(@NotNull String prefix, @NotNull Path directory) throws IOException {
    addRecursively(entryName(prefix), directory);
  }

  //<editor-fold desc="Internal interface">
  protected Compressor() { }

  private static String entryName(String name) {
    String entryName = StringUtil.trimLeading(StringUtil.trimTrailing(name.replace('\\', '/'), '/'), '/');
    if (entryName.isEmpty()) throw new IllegalArgumentException("Invalid entry name: " + name);
    return entryName;
  }

  private static long timestamp(long timestamp) {
    return timestamp == -1 ? System.currentTimeMillis() : timestamp;
  }

  private boolean accept(String entryName, @Nullable Path file) {
    return myFilter == null || myFilter.test(entryName, file);
  }

  private void addRecursively(String prefix, Path root) throws IOException {
    Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        String name = dir == root ? prefix : entryName(dir);
        if (name.isEmpty()) {
          return FileVisitResult.CONTINUE;
        }
        else if (accept(name, dir)) {
          writeDirectoryEntry(name, attrs.lastModifiedTime().toMillis());
          return FileVisitResult.CONTINUE;
        }
        else {
          return FileVisitResult.SKIP_SUBTREE;
        }
      }

      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        String name = entryName(file);
        if (accept(name, file)) {
          try (InputStream source = Files.newInputStream(file)) {
            writeFileEntry(name, source, attrs.size(), attrs.lastModifiedTime().toMillis());
          }
        }
        return FileVisitResult.CONTINUE;
      }

      private String entryName(Path fileOrDir) {
        String relativeName = Compressor.entryName(root.relativize(fileOrDir).toString());
        return prefix.isEmpty() ? relativeName : prefix + '/' + relativeName;
      }
    });
  }

  protected abstract void writeDirectoryEntry(String name, long timestamp) throws IOException;
  protected abstract void writeFileEntry(String name, InputStream source, long length, long timestamp) throws IOException;
  //</editor-fold>
}
