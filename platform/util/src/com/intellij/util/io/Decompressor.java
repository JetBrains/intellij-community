// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.Consumer;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public abstract class Decompressor<Stream> {
  public static class Tar extends Decompressor<TarArchiveInputStream> {
    public Tar(@NotNull File file) {
      mySource = file;
    }

    public Tar(@NotNull TarArchiveInputStream stream) {
      mySource = stream;
    }

    //<editor-fold desc="Implementation">
    private final Object mySource;

    @Override
    protected TarArchiveInputStream openStream() throws IOException {
      return mySource instanceof TarArchiveInputStream
             ? (TarArchiveInputStream)mySource
             : new TarArchiveInputStream(new GzipCompressorInputStream(new FileInputStream(((File)mySource))));
    }

    @Override
    protected Entry nextEntry(TarArchiveInputStream tar) throws IOException {
      TarArchiveEntry tarEntry = tar.getNextTarEntry();
      return tarEntry == null ? null : new Entry(tarEntry.getName(), tarEntry.isDirectory());
    }

    @Override
    protected InputStream openEntryStream(TarArchiveInputStream stream, Entry entry) {
      return stream;
    }

    @Override
    protected void closeEntryStream(InputStream stream) { }

    @Override
    protected void closeStream(TarArchiveInputStream tar) throws IOException {
      if (mySource instanceof File) {
        tar.close();
      }
    }
    //</editor-fold>
  }

  public static class Zip extends Decompressor<ZipInputStream> {
    public Zip(@NotNull File file) {
      mySource = file;
    }

    //<editor-fold desc="Implementation">
    private final File mySource;

    @Override
    protected ZipInputStream openStream() throws IOException {
      return new ZipInputStream(new FileInputStream(mySource));
    }

    @Override
    protected Entry nextEntry(ZipInputStream zip) throws IOException {
      ZipEntry zipEntry = zip.getNextEntry();
      return zipEntry == null ? null : new Entry(zipEntry.getName(), zipEntry.isDirectory());
    }

    @Override
    protected InputStream openEntryStream(ZipInputStream stream, Entry entry) {
      return stream;
    }

    @Override
    protected void closeEntryStream(InputStream stream) { }

    @Override
    protected void closeStream(ZipInputStream stream) throws IOException {
      stream.close();
    }
    //</editor-fold>
  }

  private Condition<String> myFilter = null;
  private boolean myOverwrite = true;
  private Consumer<File> myConsumer;

  public Decompressor<Stream> filter(@Nullable Condition<String> filter) {
    myFilter = filter;
    return this;
  }

  public Decompressor<Stream> overwrite(boolean overwrite) {
    myOverwrite = overwrite;
    return this;
  }

  public Decompressor<Stream> postprocessor(@Nullable Consumer<File> consumer) {
    myConsumer = consumer;
    return this;
  }

  public final void extract(@NotNull File outputDir) throws IOException {
    Stream stream = openStream();
    try {
      Entry entry;
      while ((entry = nextEntry(stream)) != null) {
        String name = entry.name;
        if (myFilter != null && !myFilter.value(name)) {
          continue;
        }

        File outputFile = ZipUtil.newFileForEntry(outputDir, name);

        if (entry.isDirectory) {
          FileUtil.createDirectory(outputFile);
        }
        else if (!outputFile.exists() || myOverwrite) {
          InputStream inputStream = openEntryStream(stream, entry);
          try {
            FileUtil.createParentDirs(outputFile);
            FileOutputStream outputStream = new FileOutputStream(outputFile);
            try {
              FileUtil.copy(inputStream, outputStream);
            }
            finally {
              outputStream.close();
            }
          }
          finally {
            closeEntryStream(inputStream);
          }
        }

        if (myConsumer != null) {
          myConsumer.consume(outputFile);
        }
      }
    }
    finally {
      closeStream(stream);
    }
  }

  //<editor-fold desc="Internal interface">
  protected Decompressor() { }

  private static class Entry {
    private final String name;
    private final boolean isDirectory;

    public Entry(String name, boolean isDirectory) {
      this.name = name;
      this.isDirectory = isDirectory;
    }
  }

  protected abstract Stream openStream() throws IOException;
  protected abstract Entry nextEntry(Stream stream) throws IOException;
  protected abstract InputStream openEntryStream(Stream stream, Entry entry) throws IOException;
  protected abstract void closeEntryStream(InputStream stream) throws IOException;
  protected abstract void closeStream(Stream stream) throws IOException;
  //</editor-fold>
}