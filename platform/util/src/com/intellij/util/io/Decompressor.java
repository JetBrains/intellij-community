// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.util.ArrayUtil;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.jetbrains.annotations.ApiStatus;
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
import java.util.Set;
import java.util.function.Consumer;
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
      TarArchiveEntry te;
      while ((te = myStream.getNextTarEntry()) != null && !(te.isFile() || te.isDirectory() || te.isSymbolicLink())) /* skipping unsupported */;
      if (te == null) return null;
      if (!SystemInfo.isWindows) return new Entry(te.getName(), type(te), te.getMode(), te.getLinkName());
      // Unix permissions are ignored on Windows
      if (te.isSymbolicLink()) return new Entry(te.getName(), Entry.Type.SYMLINK, 0, te.getLinkName());
      return new Entry(te.getName(), te.isDirectory());
    }

    private static Entry.Type type(TarArchiveEntry te) {
      return te.isSymbolicLink() ? Entry.Type.SYMLINK : te.isDirectory() ? Entry.Type.DIR : Entry.Type.FILE;
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
    public Zip(@NotNull Path file) {
      mySource = file.toFile();
    }

    public Zip(@NotNull File file) {
      mySource = file;
    }

    /**
     * <p>Returns an alternative implementation that is slower but supports ZIP extensions (UNIX/DOS attributes, symlinks).</p>
     * <p><b>NOTE</b>: requires Commons Compress to be on the classpath.</p>
     */
    public @NotNull Decompressor withZipExtensions() {
      return new CommonsZip(mySource);
    }

    //<editor-fold desc="Implementation">
    /** @deprecated please use {@link #withZipExtensions()} instead */
    @Deprecated
    @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
    public @NotNull Decompressor withUnixPermissionsAndSymlinks() {
      return new CommonsZip(mySource);
    }

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

    private static class CommonsZip extends Decompressor {
      private final File mySource;
      private org.apache.commons.compress.archivers.zip.ZipFile myZip;
      private Enumeration<? extends ZipArchiveEntry> myEntries;
      private ZipArchiveEntry myEntry;

      CommonsZip(File file) {
        mySource = file;
      }

      @Override
      protected void openStream() throws IOException {
        myZip = new org.apache.commons.compress.archivers.zip.ZipFile(mySource);
        myEntries = myZip.getEntries();
      }

      @Override
      protected Entry nextEntry() throws IOException {
        myEntry = myEntries.hasMoreElements() ? myEntries.nextElement() : null;
        if (myEntry == null) return null;
        int platform = myEntry.getPlatform();
        if (SystemInfo.isWindows) {
          // Unix permissions are ignored on Windows
          if (platform == ZipArchiveEntry.PLATFORM_UNIX) {
            return new Entry(myEntry.getName(), type(myEntry), 0, myZip.getUnixSymlink(myEntry));
          }
          if (platform == ZipArchiveEntry.PLATFORM_FAT) {
            return new Entry(myEntry.getName(), type(myEntry), (int)(myEntry.getExternalAttributes()), null);
          }
        }
        else {
          if (platform == ZipArchiveEntry.PLATFORM_UNIX) {
            return new Entry(myEntry.getName(), type(myEntry), myEntry.getUnixMode(), myZip.getUnixSymlink(myEntry));
          }
          if (platform == ZipArchiveEntry.PLATFORM_FAT) {
            // DOS attributes are converted into Unix permissions
            long attributes = myEntry.getExternalAttributes();
            @SuppressWarnings("OctalInteger") int unixMode = isSet(attributes, Entry.DOS_READ_ONLY) ? 0444 : 0644;
            return new Entry(myEntry.getName(), type(myEntry), unixMode, myZip.getUnixSymlink(myEntry));
          }
        }
        return new Entry(myEntry.getName(), myEntry.isDirectory());
      }

      private static Entry.Type type(ZipArchiveEntry e) {
        return e.isUnixSymlink() ? Entry.Type.SYMLINK : e.isDirectory() ? Entry.Type.DIR : Entry.Type.FILE;
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
    }
    //</editor-fold>
  }

  private @Nullable Predicate<? super String> myFilter = null;
  private @Nullable List<String> myPathsPrefix = null;
  private boolean myOverwrite = true;
  private @Nullable Consumer<? super Path> myPostProcessor;

  @SuppressWarnings("LambdaUnfriendlyMethodOverload")
  public Decompressor filter(@Nullable Predicate<? super String> filter) {
    myFilter = filter;
    return this;
  }

  public Decompressor overwrite(boolean overwrite) {
    myOverwrite = overwrite;
    return this;
  }

  public Decompressor postProcessor(@Nullable Consumer<? super Path> consumer) {
    myPostProcessor = consumer;
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
  public Decompressor removePrefixPath(@Nullable String prefix) throws IOException {
    myPathsPrefix = prefix != null ? normalizePathAndSplit(prefix) : null;
    return this;
  }

  public final void extract(@NotNull File outputDir) throws IOException {
    extract(outputDir.toPath());
  }

  public final void extract(@NotNull Path outputDir) throws IOException {
    openStream();
    try {
      Entry entry;
      while ((entry = nextEntry()) != null) {
        if (myFilter != null) {
          String entryName = entry.type == Entry.Type.DIR && !Strings.endsWithChar(entry.name, '/') ? entry.name + '/' : entry.name;
          if (!myFilter.test(entryName)) {
            continue;
          }
        }

        if (myPathsPrefix != null) {
          entry = mapPathPrefix(entry, myPathsPrefix);
          if (entry == null) continue;
        }

        Path outputFile = entryFile(outputDir, entry.name);
        switch (entry.type) {
          case DIR:
            Files.createDirectories(outputFile);
            break;

          case FILE:
            if (myOverwrite || !Files.exists(outputFile)) {
              InputStream inputStream = openEntryStream(entry);
              try {
                Files.createDirectories(outputFile.getParent());
                try (OutputStream outputStream = Files.newOutputStream(outputFile)) {
                  StreamUtil.copy(inputStream, outputStream);
                }
                if (entry.mode != 0) {
                  setAttributes(entry.mode, outputFile);
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
            if (myOverwrite || !Files.exists(outputFile, LinkOption.NOFOLLOW_LINKS)) {
              try {
                Path outputTarget = Paths.get(entry.linkTarget);
                Files.createDirectories(outputFile.getParent());
                Files.deleteIfExists(outputFile);
                Files.createSymbolicLink(outputFile, outputTarget);
              }
              catch (InvalidPathException e) {
                throw new IOException("Invalid symlink entry: " + entry.name + " -> " + entry.linkTarget, e);
              }
            }
            break;
        }

        if (myPostProcessor != null) {
          myPostProcessor.accept(outputFile);
        }
      }
    }
    finally {
      closeStream();
    }
  }

  private @Nullable static Entry mapPathPrefix(Entry e, List<String> prefix) throws IOException {
    List<String> ourPathSplit = normalizePathAndSplit(e.name);
    if (prefix.size() >= ourPathSplit.size() || !ourPathSplit.subList(0, prefix.size()).equals(prefix)) {
      return null;
    }
    String newName = String.join("/", ourPathSplit.subList(prefix.size(), ourPathSplit.size()));
    return new Entry(newName, e.type, e.mode, e.linkTarget);
  }

  private static List<String> normalizePathAndSplit(String path) throws IOException {
    ensureValidPath(path);
    String canonicalPath = FileUtilRt.toCanonicalPath(path, '/', true);
    return FileUtilRt.splitPath(StringUtil.trimLeading(canonicalPath, '/'), '/');
  }

  @SuppressWarnings("OctalInteger")
  private static void setAttributes(int mode, Path outputFile) throws IOException {
    if (SystemInfo.isWindows) {
      DosFileAttributeView attrs = Files.getFileAttributeView(outputFile, DosFileAttributeView.class);
      if (attrs != null) {
        if (isSet(mode, Entry.DOS_READ_ONLY)) attrs.setReadOnly(true);
        if (isSet(mode, Entry.DOS_HIDDEN)) attrs.setHidden(true);
      }
    }
    else {
      PosixFileAttributeView attrs = Files.getFileAttributeView(outputFile, PosixFileAttributeView.class);
      if (attrs != null) {
        Set<PosixFilePermission> permissions = EnumSet.noneOf(PosixFilePermission.class);
        if (isSet(mode, 0400)) permissions.add(PosixFilePermission.OWNER_READ);
        if (isSet(mode, 0200)) permissions.add(PosixFilePermission.OWNER_WRITE);
        if (isSet(mode, 0100)) permissions.add(PosixFilePermission.OWNER_EXECUTE);
        if (isSet(mode, 040)) permissions.add(PosixFilePermission.GROUP_READ);
        if (isSet(mode, 020)) permissions.add(PosixFilePermission.GROUP_WRITE);
        if (isSet(mode, 010)) permissions.add(PosixFilePermission.GROUP_EXECUTE);
        if (isSet(mode, 04)) permissions.add(PosixFilePermission.OTHERS_READ);
        if (isSet(mode, 02)) permissions.add(PosixFilePermission.OTHERS_WRITE);
        if (isSet(mode, 01)) permissions.add(PosixFilePermission.OTHERS_EXECUTE);
        attrs.setPermissions(permissions);
      }
    }
  }

  //<editor-fold desc="Internal interface">
  protected Decompressor() { }

  protected static final class Entry {
    enum Type {FILE, DIR, SYMLINK}

    static final int DOS_READ_ONLY = 0b01;
    static final int DOS_HIDDEN = 0b010;

    public final String name;
    public final @Nullable String linkTarget;
    final Type type;
    final int mode;

    Entry(String name, boolean isDirectory) {
      this(name, isDirectory ? Type.DIR : Type.FILE, 0, null);
    }

    Entry(String name, Type type, int mode, @Nullable String linkTarget) {
      this.name = name;
      this.type = type;
      this.mode = mode;
      this.linkTarget = linkTarget;
    }
  }

  protected abstract void openStream() throws IOException;
  protected abstract @Nullable Entry nextEntry() throws IOException;
  protected abstract InputStream openEntryStream(Entry entry) throws IOException;
  protected abstract void closeEntryStream(InputStream stream) throws IOException;
  protected abstract void closeStream() throws IOException;
  //</editor-fold>

  private static void ensureValidPath(String entryName) throws IOException {
    if (entryName.contains("..") && ArrayUtil.contains("..", entryName.split("[/\\\\]"))) {
      throw new IOException("Invalid entry name: " + entryName);
    }
  }

  public static @NotNull Path entryFile(@NotNull Path outputDir, @NotNull String entryName) throws IOException {
    ensureValidPath(entryName);
    return outputDir.resolve(StringUtil.trimLeading(entryName, '/'));
  }

  //<editor-fold desc="Deprecated stuff.">
  /** @deprecated please use {@link #filter(Predicate)} instead */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  @SuppressWarnings("LambdaUnfriendlyMethodOverload")
  public Decompressor filter(@Nullable Condition<? super String> filter) {
    return filter(filter == null ? null : (Predicate<? super String>)(it -> filter.value(it)));
  }

  /** @deprecated please use {@link #postProcessor(Consumer)} instead */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public Decompressor postprocessor(@Nullable com.intellij.util.Consumer<? super File> consumer) {
    return postProcessor(consumer == null ? null : path -> consumer.consume(path.toFile()));
  }
  //</editor-fold>
}
