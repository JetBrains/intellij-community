// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.*;
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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Enumeration;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public abstract class Decompressor {
  private static final Logger LOG = Logger.getInstance(Decompressor.class);

  /**
   * The Tar decompressor automatically detects the compression of an input file/stream.
   * <p>
   * <b>NOTE</b>: requires {@code commons-compress} and {@code commons-io} libraries to be on the classpath.
   */
  public static final class Tar extends Decompressor {
    public Tar(@NotNull Path file) {
      mySource = file;
    }

    /** @deprecated use {@link #Tar(Path)} instead */
    @Deprecated
    @SuppressWarnings("IO_FILE_USAGE")
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
      while ((te = myStream.getNextEntry()) != null &&
             !((te.isFile() && !te.isLink()) // ignore hardlink
               || te.isDirectory()
               || te.isSymbolicLink())) /* skipping unsupported */;
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

    /** @deprecated use {@link #Zip(Path)} instead */
    @Deprecated
    @SuppressWarnings("IO_FILE_USAGE")
    public Zip(@NotNull File file) {
      mySource = file.toPath();
    }

    /**
     * Returns an alternative implementation that is slower but supports ZIP extensions (UNIX/DOS attributes, symlinks).
     * <p>
     * <b>NOTE</b>: requires {@code commons-compress} and {@code commons-io} libraries to be on the classpath.
     */
    public @NotNull Decompressor withZipExtensions() {
      return new ExtZip(mySource);
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

    private static final class ExtZip extends Decompressor {
      private final Path mySource;
      private org.apache.commons.compress.archivers.zip.ZipFile myZip;
      private Enumeration<? extends ZipArchiveEntry> myEntries;
      private ZipArchiveEntry myEntry;

      ExtZip(Path file) {
        mySource = file;
      }

      @Override
      @SuppressWarnings("deprecation")
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

    /** An entry name with separators converted to '/' and trimmed; handle with care */
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

  /**
   * Policy for handling symbolic links which point outside the archive.
   * <p>Example:</p>
   * {@code foo -> /opt/foo}
   * <p>or</p>
   * {@code foo -> ../foo}
   */
  public enum EscapingSymlinkPolicy {
    /**
     * Extract as is with no modification or check. Potentially can point to a completely different object
     * if the archive is transferred from some other host.
     */
    ALLOW,

    /**
     * Check during extraction and throw exception. See {@link Decompressor#verifySymlinkTarget}
     */
    DISALLOW,

    /**
     * <p>Make absolute symbolic links relative from the extraction directory.</p>
     * For example, when archive contains link to {@code /opt/foo} and archive is extracted to
     * {@code /foo/bar} then the resulting link will be {@code /foo/bar/opt/foo}
     */
    RELATIVIZE_ABSOLUTE
  }

  private @Nullable Predicate<? super Entry> myFilter = null;
  private @NotNull BiFunction<? super Entry, ? super IOException, ErrorHandlerChoice> myErrorHandler = (__, ___) -> ErrorHandlerChoice.BAIL_OUT;
  private boolean myIgnoreIOExceptions = false;
  private @Nullable List<String> myPathPrefix = null;
  private boolean myOverwrite = true;
  private EscapingSymlinkPolicy myEscapingSymlinkPolicy = EscapingSymlinkPolicy.ALLOW;
  private BiConsumer<? super Entry, ? super Path> myPostProcessor;

  public Decompressor filter(@Nullable Predicate<? super String> filter) {
    myFilter = filter != null ? e -> filter.test(e.type == Entry.Type.DIR ? e.name + '/' : e.name) : null;
    return this;
  }

  public Decompressor entryFilter(@Nullable Predicate<? super Entry> filter) {
    myFilter = filter;
    return this;
  }

  public Decompressor errorHandler(@NotNull BiFunction<? super Entry, ? super IOException, ErrorHandlerChoice> errorHandler) {
    myErrorHandler = errorHandler;
    return this;
  }

  public Decompressor overwrite(boolean overwrite) {
    myOverwrite = overwrite;
    return this;
  }

  public Decompressor escapingSymlinkPolicy(@NotNull EscapingSymlinkPolicy policy) {
    myEscapingSymlinkPolicy = policy;
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

  /** @deprecated use {@link #extract(Path)} instead */
  @Deprecated
  @SuppressWarnings("IO_FILE_USAGE")
  public final void extract(@NotNull File outputDir) throws IOException {
    extract(outputDir.toPath());
  }

  public final void extract(@NotNull Path outputDir) throws IOException {
    openStream();
    try {
      Deque<Path> extractedPaths = new ArrayDeque<>();

      // we'd like to keep a contact to invoke the filter once per entry,
      // since it was implicit and the introduction of retry breaks the contract
      boolean proceedToNext = true;

      Entry entry = null;
      while (!proceedToNext || (entry = nextEntry()) != null) {
        if (proceedToNext && myFilter != null && !myFilter.test(entry)) {
          continue;
        }

        proceedToNext = true; // will be set to false if EH returns RETRY
        try {
          Path processedEntry = processEntry(outputDir, entry);
          if (processedEntry != null) {
            extractedPaths.push(processedEntry);
          }
        }
        catch (IOException ioException) {
          if (myIgnoreIOExceptions) {
            LOG.debug("Skipped exception because "  + ErrorHandlerChoice.SKIP_ALL + " was selected earlier", ioException);
          } else {
            switch (myErrorHandler.apply(entry, ioException)) {
              case ABORT:
                while (!extractedPaths.isEmpty()) {
                  Files.delete(extractedPaths.pop());
                }
                return;
              case BAIL_OUT:
                throw ioException;
              case RETRY:
                proceedToNext = false;
                break;
              case SKIP:
                LOG.debug("Skipped exception", ioException);
                break;
              case SKIP_ALL:
                myIgnoreIOExceptions = true;
                LOG.debug("SKIP_ALL is selected", ioException);
            }
          }
        }
      }
    }
    finally {
      closeStream();
    }
  }

  /**
   * @return Path to an extracted entity
   */
  private @Nullable Path processEntry(@NotNull Path outputDir, Entry entry) throws IOException {
    if (myPathPrefix != null) {
      entry = mapPathPrefix(entry, myPathPrefix);
      if (entry == null) return null;
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

        String target = entry.linkTarget;

        switch (myEscapingSymlinkPolicy) {
          case DISALLOW: {
            verifySymlinkTarget(entry.name, entry.linkTarget, outputDir, outputFile);
            break;
          }
          case RELATIVIZE_ABSOLUTE: {
            if (OSAgnosticPathUtil.isAbsolute(target)) {
              target = FileUtil.join(outputDir.toString(), entry.linkTarget.substring(1));
            }
            break;
          }
        }

        if (myOverwrite || !Files.exists(outputFile, LinkOption.NOFOLLOW_LINKS)) {
          try {
            Path outputTarget = Paths.get(target);
            NioFiles.createDirectories(outputFile.getParent());
            Files.deleteIfExists(outputFile);
            Files.createSymbolicLink(outputFile, outputTarget);
          }
          catch (InvalidPathException e) {
            throw new IOException("Invalid symlink entry: " + entry.name + " -> " + target, e);
          }
        }
        break;
    }

    if (myPostProcessor != null) {
      myPostProcessor.accept(entry, outputFile);
    }

    return outputFile;
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

  private static @Nullable Entry mapPathPrefix(Entry e, List<String> prefix) throws IOException {
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

  /**
   * Specifies an action to be taken from the {@code com.intellij.util.io.Decompressor#errorHandler}.
   */
  public enum ErrorHandlerChoice {
    /** Extraction should be aborted and already extracted entities should be cleaned */
    ABORT,
    /** Do not handle error, just rethrow the exception */
    BAIL_OUT,
    /** Retry failed entry extraction */
    RETRY,
    /** Skip this entry from extraction */
    SKIP,
    /** Skip this entry for extraction and ignore any further IOExceptions during this archive extraction */
    SKIP_ALL
  }
}
