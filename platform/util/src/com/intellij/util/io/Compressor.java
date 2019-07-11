// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
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

    private Tar(OutputStream stream, Compression compression) throws IOException {
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
      FileUtil.copy(source, myStream);
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
      FileUtil.copy(source, myStream);
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

  private BiPredicate<String, Boolean> myFilter = null;

  /** @deprecated use {@link #filter(BiPredicate)} instead */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
  public Compressor filter(@Nullable Condition<? super String> filter) {
    return filter(filter != null ? (entryName, isDirectory) -> filter.value(entryName) : null);
  }

  public Compressor filter(@Nullable BiPredicate<String, Boolean> filter) {
    myFilter = filter;
    return this;
  }

  public final void addFile(@NotNull String entryName, @NotNull File file) throws IOException {
    addFile(entryName(entryName), file, true);
  }

  private void addFile(String entryName, File file, boolean checkParents) throws IOException {
    if (accepts(entryName, Boolean.FALSE, checkParents)) {
      try (InputStream source = new FileInputStream(file)) {
        writeFileEntry(entryName, source, file.length(), file.lastModified());
      }
    }
  }

  public final void addFile(@NotNull String entryName, @NotNull byte[] content) throws IOException {
    addFile(entryName, content, -1);
  }

  public final void addFile(@NotNull String entryName, @NotNull byte[] content, long timestamp) throws IOException {
    entryName = entryName(entryName);
    if (accepts(entryName, Boolean.FALSE, true)) {
      writeFileEntry(entryName, new ByteArrayInputStream(content), content.length, timestamp(timestamp));
    }
  }

  public final void addFile(@NotNull String entryName, @NotNull InputStream content) throws IOException {
    addFile(entryName, content, -1);
  }

  public final void addFile(@NotNull String entryName, @NotNull InputStream content, long timestamp) throws IOException {
    entryName = entryName(entryName);
    if (accepts(entryName, Boolean.FALSE, true)) {
      writeFileEntry(entryName, content, -1, timestamp(timestamp));
    }
  }

  public final void addDirectory(@NotNull String entryName) throws IOException {
    addDirectory(entryName, -1);
  }

  public final void addDirectory(@NotNull String entryName, long timestamp) throws IOException {
    entryName = entryName(entryName);
    if (accepts(entryName, Boolean.TRUE, true)) {
      writeDirectoryEntry(entryName, timestamp(timestamp));
    }
  }

  public final void addDirectory(@NotNull File directory) throws IOException {
    addRecursively("", directory);
  }

  public final void addDirectory(@NotNull String prefix, @NotNull File directory) throws IOException {
    prefix = entryName(prefix);
    if (accepts(prefix, Boolean.TRUE, true)) {
      addRecursively(prefix, directory);
    }
  }

  //<editor-fold desc="Internal interface">
  protected Compressor() { }

  private static String entryName(String name) {
    String entryName = StringUtil.trimLeading(StringUtil.trimTrailing(name.replace('\\', '/'), '/'), '/');
    if (StringUtil.isEmpty(entryName)) throw new IllegalArgumentException("Invalid entry name: " + name);
    return entryName;
  }

  private static long timestamp(long timestamp) {
    return timestamp == -1 ? System.currentTimeMillis() : timestamp;
  }

  private boolean accepts(String entryName, Boolean isDirectory, boolean checkParents) {
    if (myFilter == null) return true;
    if (checkParents) {
      int p = -1;
      while ((p = entryName.indexOf('/', p + 1)) > 0) {
        if (!myFilter.test(entryName.substring(0, p), Boolean.TRUE)) {
          return false;
        }
      }
    }
    return myFilter.test(entryName, isDirectory);
  }

  private void addRecursively(String prefix, File directory) throws IOException {
    if (!prefix.isEmpty()) {
      if (!accepts(prefix, Boolean.TRUE, false)) {
        return;
      }
      else {
        writeDirectoryEntry(prefix, directory.lastModified());
      }
    }

    File[] children = directory.listFiles();
    if (children != null) {
      for (File child: children) {
        String name = prefix.isEmpty() ? child.getName() : prefix + '/' + child.getName();
        if (child.isDirectory()) {
          addRecursively(name, child);
        }
        else {
          addFile(name, child, false);
        }
      }
    }
  }

  protected abstract void writeDirectoryEntry(String name, long timestamp) throws IOException;
  protected abstract void writeFileEntry(String name, InputStream source, long length, long timestamp) throws IOException;
  //</editor-fold>
}