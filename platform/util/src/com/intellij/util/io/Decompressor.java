// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.List;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static com.intellij.util.BitUtil.isSet;

public abstract class Decompressor {
  /**
   * The Tar decompressor automatically detects the compression of an input file/stream.
   */
  public static class Tar extends Decompressor {
    public Tar(@NotNull Path file) {
      mySource = file.toFile();
    }

    public Tar(@NotNull File file) {
      mySource = file;
    }

    public Tar(@NotNull InputStream stream) {
      mySource = stream;
    }

    public @NotNull Tar withSymlinks() {
      symlinks = true;
      return this;
    }

    //<editor-fold desc="Implementation">
    private final Object mySource;
    private TarArchiveInputStream myStream;
    private boolean symlinks;

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
    @SuppressWarnings("OctalInteger")
    protected Entry nextEntry() throws IOException {
      TarArchiveEntry te;
      while ((te = myStream.getNextTarEntry()) != null && !(te.isFile() || te.isDirectory() || te.isSymbolicLink() && symlinks)) /* skips unsupported */;
      return te == null ? null : new Entry(te.getName(), type(te), isSet(te.getMode(), 0200), isSet(te.getMode(), 0100), te.getLinkName());
    }

    private static Type type(TarArchiveEntry te) {
      return te.isSymbolicLink() ? Type.SYMLINK : te.isDirectory() ? Type.DIR : Type.FILE;
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

  //NOTE. This class should work without CommonsCompress!
  public static class Zip extends Decompressor {
    public Zip(@NotNull Path file) {
      mySource = file.toFile();
    }

    public Zip(@NotNull File file) {
      mySource = file;
    }

    //<editor-fold desc="Implementation">
    private final File mySource;
    private ZipFile myZip;
    private Enumeration<? extends ZipEntry> myEntries;
    private ZipEntry myEntry;

    /**
     * Enables Zip Extensions to consider symlinks and unix file permissions.
     * NOTE. It will require CommonsCompress in the classpath
     */
    @NotNull
    public Decompressor withUnixPermissionsAndSymlinks() {
      return new CommonsZip(mySource);
    }

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
      if (myZip != null) {
        myZip.close();
        myZip = null;
      }
    }
    //</editor-fold>
  }

  private static class CommonsZip extends Decompressor {
    CommonsZip(@NotNull File file) {
      mySource = file;
    }

    //<editor-fold desc="Implementation">
    private final File mySource;
    private org.apache.commons.compress.archivers.zip.ZipFile myZip;
    private Enumeration<? extends ZipArchiveEntry> myEntries;
    private ZipArchiveEntry myEntry;

    @Override
    protected void openStream() throws IOException {
      myZip = new org.apache.commons.compress.archivers.zip.ZipFile(mySource);
      myEntries = myZip.getEntries();
    }

    @Override
    protected Entry nextEntry() throws IOException {
      if (!myEntries.hasMoreElements()) {
        myEntry = null;
        return null;
      }

      myEntry = myEntries.nextElement();
      if (myEntry == null) {
        return null;
      }

      String linkTarget = myEntry.isUnixSymlink() ? myZip.getUnixSymlink(myEntry) : null;
      //noinspection OctalInteger
      return new Entry(myEntry.getName(),
                       type(myEntry),
                       isSet(myEntry.getUnixMode(), 0200),
                       isSet(myEntry.getUnixMode(), 0100),
                       linkTarget);
    }

    private static Type type(ZipArchiveEntry te) {
      return te.isUnixSymlink() ? Type.SYMLINK : te.isDirectory() ? Type.DIR : Type.FILE;
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

  @Nullable private Predicate<? super String> myFilter = null;
  @Nullable private Condition<? super Entry> myEntryFilter = null;
  @Nullable private List<String> myPathsPrefix = null;
  private boolean myOverwrite = true;
  @Nullable private java.util.function.Consumer<? super Path> myConsumer;

  public Decompressor filter(@Nullable Predicate<? super String> filter) {
    myFilter = filter;
    return this;
  }

  /**
   * @deprecated Use {@link #filter(Predicate)}
   */
  @Deprecated
  public Decompressor filter(@Nullable Condition<? super String> filter) {
    myFilter = filter == null ? null : it -> filter.value(it);
    return this;
  }

  public Decompressor filterEntries(@Nullable Condition<? super Entry> filter) {
    myEntryFilter = filter;
    return this;
  }

  public Decompressor overwrite(boolean overwrite) {
    myOverwrite = overwrite;
    return this;
  }

  /**
   * @deprecated Use {@link #postProcessor}
   */
  @Deprecated
  public Decompressor postprocessor(@Nullable Consumer<? super File> consumer) {
    myConsumer = consumer == null ? null : path -> consumer.consume(path.toFile());
    return this;
  }

  public Decompressor postProcessor(@Nullable java.util.function.Consumer<? super Path> consumer) {
    myConsumer = consumer;
    return this;
  }

  /**
   * Extracts only items whose paths starts with the normalized prefix of {@code prefix + '/'} <br/>
   * Paths are normalized before comparison. <br/>
   * The prefix test is applied after {@link #filter(Condition)} predicate is tested. <br/>
   * Some entries may clash, so use {@link #overwrite(boolean)} to control it. <br/>
   * Some items with path that does not start from the prefix could be ignored
   *
   * @param prefix prefix to remove from every archive entry paths
   * @return self
   */
  @NotNull
  public Decompressor removePrefixPath(@Nullable final String prefix) throws IOException {
    myPathsPrefix = prefix != null ? normalizePathAndSplit(prefix) : null;
    return this;
  }

  /**
   * @deprecated Use {@link #extract(Path)}
   */
  @Deprecated
  public final void extract(@NotNull File outputDir) throws IOException {
    extract(outputDir.toPath());
  }

  public final void extract(@NotNull Path outputDir) throws IOException {
    openStream();
    try {
      Entry entry;
      while ((entry = nextEntry()) != null) {
        if (myFilter != null) {
          String entryName = entry.type == Type.DIR && !Strings.endsWithChar(entry.name, '/') ? entry.name + '/' : entry.name;
          if (!myFilter.test(entryName)) {
            continue;
          }
        }

        if (myEntryFilter != null && !myEntryFilter.value(entry)) {
          continue;
        }

        if (myPathsPrefix != null) {
          entry = entry.mapPathPrefix(myPathsPrefix);
          if (entry == null) continue;
        }

        Path outputFile = entryFile(outputDir, entry.name);
        switch (entry.type) {
          case DIR:
            Files.createDirectories(outputFile);
            break;

          case FILE:
            if (!Files.exists(outputFile) || myOverwrite) {
              InputStream inputStream = openEntryStream(entry);
              try {
                Files.createDirectories(outputFile.getParent());
                Files.copy(inputStream, outputFile, StandardCopyOption.REPLACE_EXISTING);
                if (!entry.isWritable || entry.isExecutable) {
                  if (SystemInfoRt.isWindows) {
                    if (!entry.isWritable) {
                      DosFileAttributeView attrs = Files.getFileAttributeView(outputFile, DosFileAttributeView.class);
                      if (attrs != null) {
                        attrs.setReadOnly(true);
                      }
                    }
                  }
                  else {
                    PosixFileAttributeView attrs = Files.getFileAttributeView(outputFile, PosixFileAttributeView.class);
                    if (attrs != null) {
                      EnumSet<PosixFilePermission> permissions = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ);
                      if (entry.isWritable) {
                        permissions.add(PosixFilePermission.OWNER_WRITE);
                        permissions.add(PosixFilePermission.GROUP_WRITE);
                      }
                      if (entry.isExecutable) {
                        permissions.add(PosixFilePermission.OWNER_EXECUTE);
                      }
                      attrs.setPermissions(permissions);
                    }
                  }
                }
              }
              finally {
                closeEntryStream(inputStream);
              }
            }
            break;

          case SYMLINK:
            if (Strings.isEmpty(entry.linkTarget)) {
              throw new IOException("Invalid symlink entry: " + entry.name + " (empty target)");
            }
            try {
              Path outputTarget = Paths.get(entry.linkTarget);
              Files.createDirectories(outputFile.getParent());
              Files.createSymbolicLink(outputFile, outputTarget);
            }
            catch (InvalidPathException e) {
              throw new IOException("Invalid symlink entry: " + entry.name + " -> " + entry.linkTarget, e);
            }
            break;
        }

        if (myConsumer != null) {
          myConsumer.accept(outputFile);
        }
      }
    }
    finally {
      closeStream();
    }
  }

  //<editor-fold desc="Internal interface">
  protected Decompressor() { }

  public enum Type {FILE, DIR, SYMLINK}

  public static class Entry {
    public final String name;
    public final Type type;
    public final boolean isWritable;
    public final boolean isExecutable;
    public final String linkTarget;

    protected Entry(String name, boolean isDirectory) {
      this(name, isDirectory ? Type.DIR : Type.FILE, true, false, null);
    }

    protected Entry(String name, Type type, boolean isWritable, boolean isExecutable, String linkTarget) {
      this.name = name;
      this.type = type;
      this.isWritable = isWritable;
      this.isExecutable = isExecutable;
      this.linkTarget = linkTarget;
    }

    @Nullable
    protected Entry mapPathPrefix(@NotNull List<String> prefix) throws IOException {
      List<String> ourPathSplit = normalizePathAndSplit(name);
      if (prefix.size() >= ourPathSplit.size() || !ourPathSplit.subList(0, prefix.size()).equals(prefix)) {
        return null;
      }
      String newName = String.join("/", ourPathSplit.subList(prefix.size(), ourPathSplit.size()));
      return new Entry(newName, this.type, this.isWritable, isExecutable, linkTarget);
    }
  }

  protected abstract void openStream() throws IOException;
  protected abstract Entry nextEntry() throws IOException;
  protected abstract InputStream openEntryStream(Entry entry) throws IOException;
  protected abstract void closeEntryStream(InputStream stream) throws IOException;
  protected abstract void closeStream() throws IOException;
  //</editor-fold>

  private static @NotNull List<String> normalizePathAndSplit(@NotNull String path) throws IOException {
    ensureValidPath(path);
    String canonicalPath = FileUtilRt.toCanonicalPath(path, '/', true);
    return FileUtilRt.splitPath(StringUtil.trimLeading(canonicalPath, '/'), '/');
  }

  private static void ensureValidPath(@NotNull String entryName) throws IOException {
    if (entryName.contains("..") && ArrayUtil.contains("..", entryName.split("[/\\\\]"))) {
      throw new IOException("Invalid entry name: " + entryName);
    }
  }

  public static @NotNull Path entryFile(@NotNull Path outputDir, @NotNull String entryName) throws IOException {
    ensureValidPath(entryName);
    return outputDir.resolve(StringUtil.trimLeading(entryName, '/'));
  }
}