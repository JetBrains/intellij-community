// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
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
import java.util.Enumeration;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public abstract class Decompressor {
  /**
   * The Tar decompressor automatically detects the compression of an input file/stream.
   */
  public static final class Tar extends Decompressor {
    public Tar(@NotNull Path file) {
      mySource = file;
    }

    public Tar(@NotNull File file) {
      mySource = file.toPath();
    }

    public Tar(@NotNull InputStream stream) {
      mySource = stream;
    }

    //<editor-fold desc="Implementation">
    private final Object mySource;
    private TarArchiveInputStream myStream;

    @Override
    protected void openStream() throws IOException {
      InputStream input = new BufferedInputStream(mySource instanceof Path ? Files.newInputStream((Path)mySource) : (InputStream)mySource);
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
      if (!SystemInfo.isWindows) return new Entry(te.getName(), type(te), te.getMode(), te.getLinkName(), te.getSize());
      // UNIX permissions are ignored on Windows
      if (te.isSymbolicLink()) return new Entry(te.getName(), Entry.Type.SYMLINK, 0, te.getLinkName(), te.getSize());
      return new Entry(te.getName(), te.isDirectory(), te.getSize());
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
      if (mySource instanceof Path) {
        myStream.close();
      }
    }
    //</editor-fold>
  }

  public static final class Zip extends Decompressor {
    public Zip(@NotNull Path file) {
      mySource = file;
    }

    public Zip(@NotNull File file) {
      mySource = file.toPath();
    }

    /**
     * <p>Returns an alternative implementation that is slower but supports ZIP extensions (UNIX/DOS attributes, symlinks).</p>
     * <p><b>NOTE</b>: requires Commons Compress to be on the classpath.</p>
     */
    public @NotNull Decompressor withZipExtensions() {
      return new CommonsZip(mySource);
    }

    //<editor-fold desc="Implementation">
    private final Path mySource;
    private ZipFile myZip;
    private Enumeration<? extends ZipEntry> myEntries;
    private ZipEntry myEntry;

    @Override
    protected void openStream() throws IOException {
      myZip = new ZipFile(mySource.toFile());
      myEntries = myZip.entries();
    }

    @Override
    protected Entry nextEntry() {
      myEntry = myEntries.hasMoreElements() ? myEntries.nextElement() : null;
      return myEntry == null ? null : new Entry(myEntry.getName(), myEntry.isDirectory(), myEntry.getSize());
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
    }

    private static final class CommonsZip extends Decompressor {
      private final Path mySource;
      private org.apache.commons.compress.archivers.zip.ZipFile myZip;
      private Enumeration<? extends ZipArchiveEntry> myEntries;
      private ZipArchiveEntry myEntry;

      CommonsZip(Path file) {
        mySource = file;
      }

      @Override
      protected void openStream() throws IOException {
        myZip = new org.apache.commons.compress.archivers.zip.ZipFile(Files.newByteChannel(mySource));
        myEntries = myZip.getEntries();
      }

      @Override
      protected Entry nextEntry() throws IOException {
        myEntry = myEntries.hasMoreElements() ? myEntries.nextElement() : null;
        if (myEntry == null) return null;
        int platform = myEntry.getPlatform();
        if (SystemInfo.isWindows) {
          // UNIX permissions are ignored on Windows
          if (platform == ZipArchiveEntry.PLATFORM_UNIX) {
            return new Entry(myEntry.getName(), type(myEntry), 0, myZip.getUnixSymlink(myEntry), myEntry.getSize());
          }
          if (platform == ZipArchiveEntry.PLATFORM_FAT) {
            return new Entry(myEntry.getName(), type(myEntry), (int)(myEntry.getExternalAttributes()), null, myEntry.getSize());
          }
        }
        else {
          if (platform == ZipArchiveEntry.PLATFORM_UNIX) {
            return new Entry(myEntry.getName(), type(myEntry), myEntry.getUnixMode(), myZip.getUnixSymlink(myEntry), myEntry.getSize());
          }
          if (platform == ZipArchiveEntry.PLATFORM_FAT) {
            // DOS attributes are converted into Unix permissions
            long attributes = myEntry.getExternalAttributes();
            @SuppressWarnings("OctalInteger") int unixMode = (attributes & Entry.DOS_READ_ONLY) != 0 ? 0444 : 0644;
            return new Entry(myEntry.getName(), type(myEntry), unixMode, myZip.getUnixSymlink(myEntry), myEntry.getSize());
          }
        }
        return new Entry(myEntry.getName(), myEntry.isDirectory(), myEntry.getSize());
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
      }
    }
    //</editor-fold>
  }

  public static final class Entry {
    public enum Type {FILE, DIR, SYMLINK}

    public static final int DOS_READ_ONLY = 0b01;
    public static final int DOS_HIDDEN = 0b010;

    /** An entry name (separators converted to '/' and trimmed); handle with care */
    public final String name;
    public final Type type;
    /** Depending on the source, could be POSIX permissions, DOS attributes, or just {@code 0} */
    public final int mode;
    public final long size;
    public final @Nullable String linkTarget;

    Entry(String name, boolean isDirectory, long size) {
      this(name, isDirectory ? Type.DIR : Type.FILE, 0, null, size);
    }

    Entry(String name, Type type, int mode, @Nullable String linkTarget, long size) {
      name = name.trim().replace('\\', '/');
      int s = 0, e = name.length() - 1;
      while (s < e && name.charAt(s) == '/') s++;
      while (e >= s && name.charAt(e) == '/') e--;
      this.name = name.substring(s, e + 1);
      this.type = type;
      this.mode = mode;
      this.linkTarget = linkTarget;
      this.size = size;
    }
  }

  private @Nullable Predicate<? super Entry> myFilter = null;
  private @Nullable List<String> myPathPrefix = null;
  private boolean myOverwrite = true;
  private boolean myAllowEscapingSymlinks = true;
  private BiConsumer<? super Entry, ? super Path> myPostProcessor;

  public Decompressor filter(@Nullable Predicate<? super String> filter) {
    myFilter = filter != null ? e -> filter.test(e.type == Entry.Type.DIR ? e.name + '/' : e.name) : null;
    return this;
  }

  public Decompressor entryFilter(@Nullable Predicate<? super Entry> filter) {
    myFilter = filter;
    return this;
  }

  public Decompressor overwrite(boolean overwrite) {
    myOverwrite = overwrite;
    return this;
  }

  public Decompressor allowEscapingSymlinks(boolean allowEscapingSymlinks) {
    myAllowEscapingSymlinks = allowEscapingSymlinks;
    return this;
  }

  public Decompressor postProcessor(@Nullable Consumer<? super Path> consumer) {
    myPostProcessor = consumer != null ? (entry, path) -> consumer.accept(path) : null;
    return this;
  }

  public Decompressor postProcessor(@Nullable BiConsumer<? super Entry, ? super Path> consumer) {
    myPostProcessor = consumer;
    return this;
  }

  /**
   * Extracts only items whose path starts with the normalized prefix of {@code prefix + '/'}.
   * Paths are normalized before comparison.
   * The prefix test is applied after {@link #filter} predicate is tested.
   * Some entries may clash, so use {@link #overwrite} to control it.
   * Some items with a path that does not start from the prefix could be ignored.
   *
   * @param prefix a prefix to remove from every archive entry path
   * @return self
   */
  public Decompressor removePrefixPath(@Nullable String prefix) throws IOException {
    myPathPrefix = prefix != null ? normalizePathAndSplit(prefix) : null;
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
        if (myFilter != null && !myFilter.test(entry)) {
          continue;
        }

        if (myPathPrefix != null) {
          entry = mapPathPrefix(entry, myPathPrefix);
          if (entry == null) continue;
        }

        Path outputFile = entryFile(outputDir, entry.name);
        switch (entry.type) {
          case DIR:
            NioFiles.createDirectories(outputFile);
            break;

          case FILE:
            if (myOverwrite || !Files.exists(outputFile)) {
              InputStream inputStream = openEntryStream(entry);
              try {
                NioFiles.createDirectories(outputFile.getParent());
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
            if (entry.linkTarget == null || entry.linkTarget.isEmpty()) {
              throw new IOException("Invalid symlink entry: " + entry.name + " (empty target)");
            }

            if (!myAllowEscapingSymlinks) {
              verifySymlinkTarget(entry.name, entry.linkTarget, outputDir, outputFile);
            }

            if (myOverwrite || !Files.exists(outputFile, LinkOption.NOFOLLOW_LINKS)) {
              try {
                Path outputTarget = Paths.get(entry.linkTarget);
                NioFiles.createDirectories(outputFile.getParent());
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
          myPostProcessor.accept(entry, outputFile);
        }
      }
    }
    finally {
      closeStream();
    }
  }

  private static void verifySymlinkTarget(String entryName, String linkTarget, Path outputDir, Path outputFile) throws IOException {
    try {
      Path outputTarget = Paths.get(linkTarget);
      if (outputTarget.isAbsolute()) {
        throw new IOException("Invalid symlink (absolute path): " + entryName + " -> " + linkTarget);
      }
      Path linkTargetNormalized = outputFile.getParent().resolve(outputTarget).normalize();
      if (!linkTargetNormalized.startsWith(outputDir.normalize())) {
        throw new IOException("Invalid symlink (points outside of output directory): " + entryName + " -> " + linkTarget);
      }
    }
    catch (InvalidPathException e) {
      throw new IOException("Failed to verify symlink entry scope: " + entryName + " -> " + linkTarget, e);
    }
  }

  private @Nullable static Entry mapPathPrefix(Entry e, List<String> prefix) throws IOException {
    List<String> ourPathSplit = normalizePathAndSplit(e.name);
    if (prefix.size() >= ourPathSplit.size() || !ourPathSplit.subList(0, prefix.size()).equals(prefix)) {
      return null;
    }
    String newName = String.join("/", ourPathSplit.subList(prefix.size(), ourPathSplit.size()));
    return new Entry(newName, e.type, e.mode, e.linkTarget, e.size);
  }

  private static List<String> normalizePathAndSplit(String path) throws IOException {
    ensureValidPath(path);
    String canonicalPath = FileUtilRt.toCanonicalPath(path, '/', true);
    return FileUtilRt.splitPath(StringUtil.trimLeading(canonicalPath, '/'), '/');
  }

  private static void setAttributes(int mode, Path outputFile) throws IOException {
    if (SystemInfo.isWindows) {
      DosFileAttributeView attrs = Files.getFileAttributeView(outputFile, DosFileAttributeView.class);
      if (attrs != null) {
        if ((mode & Entry.DOS_READ_ONLY) != 0) attrs.setReadOnly(true);
        if ((mode & Entry.DOS_HIDDEN) != 0) attrs.setHidden(true);
      }
    }
    else {
      PosixFileAttributeView attrs = Files.getFileAttributeView(outputFile, PosixFileAttributeView.class);
      if (attrs != null) {
        attrs.setPermissions(PosixFilePermissionsUtil.fromUnixMode(mode));
      }
    }
  }

  //<editor-fold desc="Internal interface">
  protected Decompressor() { }

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
}
