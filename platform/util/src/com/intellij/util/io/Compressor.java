// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public abstract class Compressor implements Closeable {
  public static class Tar extends Compressor {
    public Tar(@NotNull File file) throws IOException {
      myStream = new TarArchiveOutputStream(new GzipCompressorOutputStream(new FileOutputStream(file)));
      myStream.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
    }

    public Tar(@NotNull TarArchiveOutputStream stream) {
      myStream = stream;
    }

    //<editor-fold desc="Implementation">
    private final TarArchiveOutputStream myStream;

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
      myStream = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
    }

    public Zip(@NotNull ZipOutputStream stream) {
      myStream = stream;
    }

    //<editor-fold desc="Implementation">
    private final ZipOutputStream myStream;

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

  private Condition<String> myFilter = null;

  public Compressor filter(@Nullable Condition<String> filter) {
    myFilter = filter;
    return this;
  }

  public final void addFile(@NotNull String entryName, @NotNull File file) throws IOException {
    entryName = entryName(entryName);
    if (accepts(entryName)) {
      InputStream source = new FileInputStream(file);
      try {
        writeFileEntry(entryName, source, file.length(), file.lastModified());
      }
      finally {
        source.close();
      }
    }
  }

  public final void addFile(@NotNull String entryName, @NotNull byte[] content) throws IOException {
    addFile(entryName, content, -1);
  }

  public final void addFile(@NotNull String entryName, @NotNull byte[] content, long timestamp) throws IOException {
    entryName = entryName(entryName);
    if (accepts(entryName)) {
      writeFileEntry(entryName, new ByteArrayInputStream(content), content.length, timestamp(timestamp));
    }
  }

  public final void addFile(@NotNull String entryName, @NotNull InputStream content) throws IOException {
    addFile(entryName, content, -1);
  }

  public final void addFile(@NotNull String entryName, @NotNull InputStream content, long timestamp) throws IOException {
    entryName = entryName(entryName);
    if (accepts(entryName)) {
      writeFileEntry(entryName, content, -1, timestamp(timestamp));
    }
  }

  public final void addDirectory(@NotNull String entryName) throws IOException {
    addDirectory(entryName, -1);
  }

  public final void addDirectory(@NotNull String entryName, long timestamp) throws IOException {
    entryName = entryName(entryName);
    if (accepts(entryName)) {
      writeDirectoryEntry(entryName, timestamp(timestamp));
    }
  }

  public final void addDirectory(@NotNull File directory) throws IOException {
    addRecursively("", directory);
  }

  public final void addDirectory(@NotNull String prefix, @NotNull File directory) throws IOException {
    addRecursively(entryName(prefix), directory);
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

  private boolean accepts(String entryName) {
    return myFilter == null || myFilter.value(entryName);
  }

  private void addRecursively(String prefix, File directory) throws IOException {
    File[] children = directory.listFiles();
    if (children != null) {
      if (!prefix.isEmpty()) {
        addDirectory(prefix, directory.lastModified());
      }
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

  protected abstract void writeDirectoryEntry(String name, long timestamp) throws IOException;
  protected abstract void writeFileEntry(String name, InputStream source, long length, long timestamp) throws IOException;
  //</editor-fold>
}