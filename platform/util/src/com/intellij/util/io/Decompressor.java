// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public abstract class Decompressor {
  /**
   * The Tar decompressor automatically detects the compression of an input file/stream.
   */
  public static class Tar extends Decompressor {
    public Tar(@NotNull File file) {
      mySource = file;
    }

    public Tar(@NotNull InputStream stream) {
      mySource = stream;
    }

    //<editor-fold desc="Implementation">
    private final Object mySource;
    private TarArchiveInputStream myStream;

    @Override
    protected void openStream() throws IOException {
      InputStream input = new BufferedInputStream(mySource instanceof File ? new FileInputStream(((File)mySource)) : (InputStream)mySource);
      try {
        input = new CompressorStreamFactory().createCompressorInputStream(input);
      }
      catch (CompressorException e) {
        Throwable cause = e.getCause();
        if (cause instanceof IOException) throw (IOException)cause;
      }
      myStream = new TarArchiveInputStream(input);
    }

    @Override
    protected Entry nextEntry() throws IOException {
      TarArchiveEntry tarEntry = myStream.getNextTarEntry();
      return tarEntry == null ? null : new Entry(tarEntry.getName(), tarEntry.isDirectory());
    }

    @Override
    protected InputStream openEntryStream(Entry entry) {
      return myStream;
    }

    @Override
    protected void closeEntryStream(InputStream stream) { }

    @Override
    protected void closeStream() throws IOException {
      if (mySource instanceof File) {
        myStream.close();
        myStream = null;
      }
    }
    //</editor-fold>
  }

  public static class Zip extends Decompressor {
    public Zip(@NotNull File file) {
      mySource = file;
    }

    //<editor-fold desc="Implementation">
    private final File mySource;
    private ZipFile myZip;
    private Enumeration<? extends ZipEntry> myEntries;
    private ZipEntry myEntry;

    @Override
    protected void openStream() throws IOException {
      myZip = new ZipFile(mySource);
      myEntries = myZip.entries();
    }

    @Override
    protected Entry nextEntry() {
      myEntry = myEntries.hasMoreElements() ? myEntries.nextElement() : null;
      return myEntry == null ? null : new Entry(myEntry.getName(), myEntry.isDirectory());
    }

    @Override
    protected InputStream openEntryStream(Entry entry) throws IOException {
      return myZip.getInputStream(myEntry);
    }

    @Override
    protected void closeEntryStream(InputStream stream) throws IOException {
      stream.close();
    }

    @Override
    protected void closeStream() throws IOException {
      myZip.close();
      myZip = null;
    }
    //</editor-fold>
  }

  private Condition<? super String> myFilter = null;
  private boolean myOverwrite = true;
  private Consumer<? super File> myConsumer;

  public Decompressor filter(@Nullable Condition<? super String> filter) {
    myFilter = filter;
    return this;
  }

  public Decompressor overwrite(boolean overwrite) {
    myOverwrite = overwrite;
    return this;
  }

  public Decompressor postprocessor(@Nullable Consumer<? super File> consumer) {
    myConsumer = consumer;
    return this;
  }

  public final void extract(@NotNull File outputDir) throws IOException {
    openStream();
    try {
      Entry entry;
      while ((entry = nextEntry()) != null) {
        String name = entry.name;
        if (myFilter != null && !myFilter.value(name)) {
          continue;
        }

        File outputFile = entryFile(outputDir, name);

        if (entry.isDirectory) {
          FileUtil.createDirectory(outputFile);
        }
        else if (!outputFile.exists() || myOverwrite) {
          InputStream inputStream = openEntryStream(entry);
          try {
            FileUtil.createParentDirs(outputFile);
            try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
              FileUtil.copy(inputStream, outputStream);
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
      closeStream();
    }
  }

  //<editor-fold desc="Internal interface">
  protected Decompressor() { }

  protected static class Entry {
    private final String name;
    private final boolean isDirectory;

    Entry(String name, boolean isDirectory) {
      this.name = name;
      this.isDirectory = isDirectory;
    }
  }

  protected abstract void openStream() throws IOException;
  protected abstract Entry nextEntry() throws IOException;
  protected abstract InputStream openEntryStream(Entry entry) throws IOException;
  protected abstract void closeEntryStream(InputStream stream) throws IOException;
  protected abstract void closeStream() throws IOException;
  //</editor-fold>

  @NotNull
  public static File entryFile(@NotNull File outputDir, @NotNull String entryName) throws IOException {
    if (entryName.contains("..") && ArrayUtil.contains("..", entryName.split("[/\\\\]"))) {
      throw new IOException("Invalid entry name: " + entryName);
    }
    return new File(outputDir, entryName);
  }
}