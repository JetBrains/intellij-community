// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.DosFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.function.BiPredicate;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public abstract class Compressor implements Closeable {
  /**
   * <b>NOTE</b>: requires {@code commons-compress} and {@code commons-io} libraries to be on the classpath.
   */
  public static class Tar extends Compressor {
    public enum Compression {GZIP, BZIP2, NONE}

    public Tar(@NotNull Path file, @NotNull Compression compression) throws IOException {
      this(Files.newOutputStream(file), compression);
    }

    @ApiStatus.Obsolete
    public Tar(@NotNull File file, @NotNull Compression compression) throws IOException {
      this(file.toPath(), compression);
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
    protected void writeFileEntry(String name, InputStream source, long length, long timestamp, int mode, @Nullable String symlinkTarget) throws IOException {
      TarArchiveEntry e;
      if (symlinkTarget == null) {
        e = new TarArchiveEntry(name);
      }
      else {
        e = new TarArchiveEntry(name, TarConstants.LF_SYMLINK);
        e.setLinkName(symlinkTarget);
        length = 0;
      }
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
      if (mode != 0) {
        e.setMode(mode);
      }
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

  /**
   * ZIP extensions (file modes, symlinks, etc.) are not supported.
   */
  public static class Zip extends Compressor {
    @ApiStatus.Obsolete
    public Zip(@NotNull File file) throws IOException {
      this(file.toPath());
    }

    public Zip(@NotNull Path file) throws IOException {
      this(new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(file))));
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
    protected void writeFileEntry(String name, InputStream source, long length, long timestamp, int mode, @Nullable String symlinkTarget) throws IOException {
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

  public static final class Jar extends Zip {
    @ApiStatus.Obsolete
    public Jar(@NotNull File file) throws IOException {
      this(file.toPath());
    }

    public Jar(@NotNull Path file) throws IOException {
      super(new JarOutputStream(new BufferedOutputStream(Files.newOutputStream(file))));
    }

    public void addManifest(@NotNull Manifest manifest) throws IOException {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      manifest.write(buffer);
      addFile(JarFile.MANIFEST_NAME, buffer.toByteArray());
    }
  }

  private @Nullable BiPredicate<? super String, ? super @Nullable Path> myFilter;

  /**
   * Filtering entries being added to the archive.
   * Please note that the second parameter of a filter ({@code Path}) <b>might be {@code null}</b> when it is applied
   * to an entry not present on a disk - e.g., via {@link #addFile(String, byte[])}.
   */
  public Compressor filter(@Nullable BiPredicate<? super String, ? super @Nullable Path> filter) {
    myFilter = filter;
    return this;
  }

  @ApiStatus.Obsolete
  public final void addFile(@NotNull String entryName, @NotNull File file) throws IOException {
    addFile(entryName, file.toPath());
  }

  public final void addFile(@NotNull String entryName, @NotNull Path file) throws IOException {
    addFile(entryName, file, -1);
  }

  public final void addFile(@NotNull String entryName, @NotNull Path file, long timestamp) throws IOException {
    entryName = entryName(entryName);
    if (accept(entryName, file)) {
      addFile(file, Files.readAttributes(file, BasicFileAttributes.class), entryName, timestamp);
    }
  }

  public final void addFile(@NotNull String entryName, byte @NotNull [] content) throws IOException {
    addFile(entryName, content, -1);
  }

  public final void addFile(@NotNull String entryName, byte @NotNull [] content, long timestamp) throws IOException {
    entryName = entryName(entryName);
    if (accept(entryName, null)) {
      writeFileEntry(entryName, new ByteArrayInputStream(content), content.length, timestamp(timestamp), 0, null);
    }
  }

  public final void addFile(@NotNull String entryName, @NotNull InputStream content) throws IOException {
    addFile(entryName, content, -1);
  }

  public final void addFile(@NotNull String entryName, @NotNull InputStream content, long timestamp) throws IOException {
    entryName = entryName(entryName);
    if (accept(entryName, null)) {
      writeFileEntry(entryName, content, -1, timestamp(timestamp), 0, null);
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

  @ApiStatus.Obsolete
  public final void addDirectory(@NotNull File directory) throws IOException {
    addDirectory(directory.toPath());
  }

  public final void addDirectory(@NotNull Path directory) throws IOException {
    addDirectory("", directory);
  }

  @ApiStatus.Obsolete
  public final void addDirectory(@NotNull String prefix, @NotNull File directory) throws IOException {
    addDirectory(prefix, directory.toPath());
  }

  public final void addDirectory(@NotNull String prefix, @NotNull Path directory) throws IOException {
    addDirectory(prefix, directory, -1);
  }

  public final void addDirectory(@NotNull String prefix, @NotNull Path directory, long timestampInMillis) throws IOException {
    prefix = prefix.isEmpty() ? "" : entryName(prefix);
    addRecursively(prefix, directory, timestampInMillis);
  }

  //<editor-fold desc="Internal interface">
  private static final Logger LOG = Logger.getInstance(Compressor.class);

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

  private void addFile(Path file, BasicFileAttributes attrs, String name, long explicitTimestamp) throws IOException {
    try (InputStream source = Files.newInputStream(file)) {
      long timestamp = explicitTimestamp == -1 ? attrs.lastModifiedTime().toMillis() : explicitTimestamp;
      String symlinkTarget = attrs.isSymbolicLink() ? Files.readSymbolicLink(file).toString() : null;
      writeFileEntry(name, source, attrs.size(), timestamp, mode(file), symlinkTarget);
    }
  }

  private static int mode(Path file) throws IOException {
    if (SystemInfo.isWindows) {
      DosFileAttributeView attrs = Files.getFileAttributeView(file, DosFileAttributeView.class);
      if (attrs != null) {
        DosFileAttributes dosAttrs = attrs.readAttributes();
        int mode = 0;
        if (dosAttrs.isReadOnly()) mode |= Decompressor.Entry.DOS_READ_ONLY;
        if (dosAttrs.isHidden()) mode |= Decompressor.Entry.DOS_HIDDEN;
        return mode;
      }
    }
    else {
      PosixFileAttributeView attrs = Files.getFileAttributeView(file, PosixFileAttributeView.class);
      if (attrs != null) {
        return PosixFilePermissionsUtil.toUnixMode(attrs.readAttributes().permissions());
      }
    }
    return 0;
  }

  private void addRecursively(String prefix, Path root, long timestampMs) throws IOException {
    boolean traceEnabled = LOG.isTraceEnabled();
    if (traceEnabled) LOG.trace("dir=" + root + " prefix=" + prefix);

    Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        String name = dir == root ? prefix : entryName(dir);
        if (name.isEmpty()) {
          return FileVisitResult.CONTINUE;
        }
        else if (accept(name, dir)) {
          if (traceEnabled) LOG.trace("  " + dir + " -> " + name + '/');
          writeDirectoryEntry(name, timestampMs == -1 ? attrs.lastModifiedTime().toMillis() : timestampMs);
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
          if (traceEnabled) LOG.trace("  " + file + " -> " + name + (attrs.isSymbolicLink() ? " symlink" : " size=" + attrs.size()));
          addFile(file, attrs, name, timestampMs);
        }
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
        if (exc instanceof NoSuchFileException) return FileVisitResult.CONTINUE;  // ignoring disappearing files
        throw exc;
      }

      private String entryName(Path fileOrDir) {
        String relativeName = Compressor.entryName(root.relativize(fileOrDir).toString());
        return prefix.isEmpty() ? relativeName : prefix + '/' + relativeName;
      }
    });

    LOG.trace(".");
  }

  protected abstract void writeDirectoryEntry(String name, long timestamp) throws IOException;
  protected abstract void writeFileEntry(String name, InputStream source, long length, long timestamp, int mode, @Nullable String symlinkTarget) throws IOException;
  //</editor-fold>
}
