// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.io;

import com.intellij.jna.JnaLoader;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.BitUtil;
import com.intellij.util.system.OS;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinNT;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;
import java.util.function.Consumer;

import static java.nio.file.attribute.PosixFilePermission.*;

/**
 * A utility class that provides pieces missing from {@link Files java.nio.file.Files}.
 */
public final class NioFiles {
  /**
   * A constant returned by {@link #readAttributes(Path)} when it is certain that the given path points to a symlink
   * (or one of its NTFS relatives), but can't figure out any more details.
   */
  public static final BasicFileAttributes BROKEN_SYMLINK = new BasicFileAttributes() {
    private final FileTime ZERO = FileTime.fromMillis(0);
    @Override public FileTime lastModifiedTime() { return ZERO; }
    @Override public FileTime lastAccessTime() { return ZERO; }
    @Override public FileTime creationTime() { return ZERO; }
    @Override public boolean isRegularFile() { return false; }
    @Override public boolean isDirectory() { return false; }
    @Override public boolean isSymbolicLink() { return true; }
    @Override public boolean isOther() { return false; }
    @Override public long size() { return 0; }
    @Override public Object fileKey() { return null; }
  };

  private static final Logger LOG = Logger.getInstance(NioFiles.class);
  private static final LinkOption[] NO_FOLLOW = {LinkOption.NOFOLLOW_LINKS};

  private NioFiles() { }

  /**
   * A stream-friendly wrapper around {@link Paths#get} that turns {@link InvalidPathException} into {@code null}.
   */
  public static @Nullable Path toPath(@NotNull String path) {
    try {
      return Paths.get(path);
    }
    catch (InvalidPathException e) {
      return null;
    }
  }

  /**
   * A null-safe replacement for {@link Path#getFileName} + {@link Path#toString} combination
   * (the former returns {@code null} on root directories).
   */
  public static @NotNull @NlsSafe String getFileName(@NotNull Path path) {
    Path name = path.getFileName();
    return (name != null ? name : path).toString();
  }

  /**
   * Same as {@link Files#size(Path)}, but returns {@code -1} instead of throwing {@link IOException}.
   */
  public static long sizeIfExists(@NotNull Path path) {
    try {
      return Files.size(path);
    }
    catch (IOException e) {
      return -1;
    }
  }

  /**
   * A drop-in replacement for {@link Files#createDirectories} that doesn't stumble upon symlinks - unlike the original.
   * I.e., this method accepts "/path/.../dir_link" (where "dir_link" is a symlink to a directory), while the original fails.
   */
  public static @NotNull Path createDirectories(@NotNull Path path) throws IOException {
    try {
      tryCreateDirectory(path);
    }
    catch (FileAlreadyExistsException e) {
      throw e;
    }
    catch (IOException e) {
      Path parent = path.getParent();
      if (parent == null) {
        throw e;
      }
      createDirectories(parent);
      tryCreateDirectory(path);
    }
    return path;
  }

  private static void tryCreateDirectory(Path path) throws IOException {
    try {
      Files.createDirectory(path);
    }
    catch (FileAlreadyExistsException e) {
      if (!Files.isDirectory(path)) {
        throw e;
      }
    }
  }

  /**
   * Creates all parent directories of the given path; returns the argument.
   * Example: {@code Files.newOutputStream(NioFiles.createParentDirectories(file))}.
   */
  public static @NotNull Path createParentDirectories(@NotNull Path path) throws IOException {
    Path parent = path.getParent();
    if (parent != null) createDirectories(parent);
    return path;
  }

  /**
   * An accompaniment for {@link Files#createFile} that doesn't fret upon existing files (and symlinks to),
   * and also creates missing directories.
   */
  public static @NotNull Path createIfNotExists(@NotNull Path path) throws IOException {
    createParentDirectories(path);
    try {
      Files.createFile(path);
    }
    catch (FileAlreadyExistsException e) {
      if (!Files.isRegularFile(path)) {
        throw e;
      }
    }
    return path;
  }

  /**
   * Renames a file, with an intermediate step if the existing and the new names differ only in case.
   */
  public static @NotNull Path rename(@NotNull Path from, @NotNull String newName) throws IOException {
    if (newName.equalsIgnoreCase(from.getFileName().toString())) {
      Path intermediate = Files.createTempFile(from.getParent(), newName, ".tmp");
      from = Files.move(from, intermediate, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
    return Files.move(from, from.resolveSibling(newName), StandardCopyOption.ATOMIC_MOVE);
  }

  /**
   * Like {@link Files#isWritable}, but interprets {@link SecurityException} as a negative result.
   */
  public static boolean isWritable(@NotNull Path path) {
    try {
      return Files.isWritable(path);
    }
    catch (SecurityException e) {
      return false;
    }
  }

  /**
   * On DOS-like file systems, sets the RO attribute to the corresponding value.
   * On POSIX file systems, deletes all write permissions when {@code value} is {@code true} or
   * adds the "owner-write" one otherwise.
   */
  public static void setReadOnly(@NotNull Path path, boolean value) throws IOException {
    PosixFileAttributeView posixView;
    DosFileAttributeView dosView;

    if ((posixView = Files.getFileAttributeView(path, PosixFileAttributeView.class)) != null) {
      Set<PosixFilePermission> permissions = posixView.readAttributes().permissions();
      @SuppressWarnings("SlowAbstractSetRemoveAll") boolean modified =
        value ? permissions.removeAll(Arrays.asList(OWNER_WRITE, GROUP_WRITE, OTHERS_WRITE)) : permissions.add(OWNER_WRITE);
      if (modified) {
        posixView.setPermissions(permissions);
      }
    }
    else if ((dosView = Files.getFileAttributeView(path, DosFileAttributeView.class)) != null) {
      dosView.setReadOnly(value);
    }
    else {
      throw new IOException("Not supported: " + path.getFileSystem());
    }
  }

  /**
   * On POSIX file systems, the method sets "owner-exec" permission (if not yet set); on others, it does nothing.
   */
  public static void setExecutable(@NotNull Path file) throws IOException {
    PosixFileAttributeView view = Files.getFileAttributeView(file, PosixFileAttributeView.class);
    if (view != null) {
      Set<PosixFilePermission> permissions = view.readAttributes().permissions();
      if (permissions.add(OWNER_EXECUTE)) {
        view.setPermissions(permissions);
      }
    }
  }

  /**
   * A convenience wrapper around {@link Files#newDirectoryStream(Path)} that returns all entries of the given directory,
   * ignores exceptions (returns an empty list), and doesn't forget to close the directory stream.
   */
  public static @NotNull List<Path> list(@NotNull Path directory) {
    try {
      List<Path> files = new ArrayList<>();
      try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
        for (Path path : stream) files.add(path);
      }
      return files;
    }
    catch (IOException e) {
      return Collections.emptyList();
    }
  }

  /**
   * A file attributes reading routine, tolerant to exceptions caused by broken symlinks (in particular, the ones exported by WSL).
   *
   * @see #BROKEN_SYMLINK
   */
  public static @NotNull BasicFileAttributes readAttributes(@NotNull Path path) throws IOException, SecurityException {
    try {
      return Files.readAttributes(path, BasicFileAttributes.class, NO_FOLLOW);
    }
    catch (NoSuchFileException | AccessDeniedException e) { throw e; }
    catch (FileSystemException e) {
      if (OS.CURRENT == OS.Windows && JnaLoader.isLoaded() && isNtfsReparsePoint(path)) {
        LOG.debug(e);
        return BROKEN_SYMLINK;
      }
      throw e;
    }
  }

  private static boolean isNtfsReparsePoint(Path path) {
    int attrs = Kernel32.INSTANCE.GetFileAttributes(path.toString());
    return attrs != WinBase.INVALID_FILE_ATTRIBUTES && BitUtil.isSet(attrs, WinNT.FILE_ATTRIBUTE_REPARSE_POINT);
  }

  /**
   * See {@link #deleteRecursively(Path, Consumer)}.
   */
  public static void deleteRecursively(@NotNull Path fileOrDirectory) throws IOException {
    FileUtilRt.deleteRecursively(fileOrDirectory, null);
  }

  /**
   * <p>Recursively deletes the given directory or file if it exists.
   * Does not follow symlinks or junctions (i.e., deletes just links, not targets).
   * Invokes the callback before deleting a file or a directory (the latter - after deleting its content).
   * Fails fast (throws an exception right after meeting a problematic file and does not try to delete the rest first).</p>
   *
   * <p>Implementation detail: the method tries to delete a file up to 10 times with 10 ms pause between attempts -
   * usually it's enough to overcome intermittent file lock on Windows.</p>
   */
  public static void deleteRecursively(@NotNull Path fileOrDirectory, @NotNull Consumer<? super Path> callback) throws IOException {
    FileUtilRt.deleteRecursively(fileOrDirectory, callback::accept);
  }

  /**
   * See {@link #deleteQuietly(Path, Consumer)}.
   */
  @ApiStatus.Experimental
  public static void deleteQuietly(@Nullable Path file) {
    deleteQuietly(file, null);
  }

  /**
   * Deletes a single file or an empty directory without throwing exceptions.
   * Handy for using in {@code finally} blocks for cleaning up temporary artifacts.
   */
  @ApiStatus.Experimental
  public static void deleteQuietly(@Nullable Path file, @Nullable Consumer<IOException> handler) {
    try {
      if (file != null) {
        Files.deleteIfExists(file);
      }
    }
    catch (IOException e) {
      if (handler != null) handler.accept(e);
    }
  }

  /**
   * See {@link #copyRecursively(Path, Path, Consumer)}.
   */
  @ApiStatus.Experimental
  public static void copyRecursively(@NotNull Path from, @NotNull Path to) throws IOException {
    copyRecursively(from, to, null);
  }

  /**
   * <p>Recursively copies the given directory or file; for files, copies attributes.
   * Does not follow symlinks (i.e., copies just links, not targets).
   * Merges with an existing directory structure under {@code to} (if any), but does not overwrite existing files.
   * Invokes the callback before copying a file or a directory.
   * Fails fast (throws an exception right after meeting a problematic file or directory); does not try to delete an incomplete copy.</p>
   */
  @ApiStatus.Experimental
  public static void copyRecursively(@NotNull Path from, @NotNull Path to, @Nullable Consumer<? super Path> callback) throws IOException {
    Files.walkFileTree(from, new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        if (callback != null) callback.accept(dir);
        Path copy = dir == from ? to : to.resolve(from.relativize(dir));
        createDirectories(copy);
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        if (callback != null) callback.accept(file);
        Path copy = file == from ? to : to.resolve(from.relativize(file));
        Files.copy(file, copy, LinkOption.NOFOLLOW_LINKS, StandardCopyOption.COPY_ATTRIBUTES);
        return FileVisitResult.CONTINUE;
      }
    });
  }

  /**
   * A handy stub for building tree stats collecting visitors (e.g., for estimating the number of files before deletion).
   * It ignores exceptions and skips symlinks and NTFS reparse points.
   */
  public abstract static class StatsCollectingVisitor extends SimpleFileVisitor<Path> {
    protected abstract void countDirectory(Path dir, BasicFileAttributes attrs);
    protected abstract void countFile(Path file, BasicFileAttributes attrs);

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
      countDirectory(dir, attrs);
      if (attrs.isSymbolicLink() || OS.CURRENT == OS.Windows && attrs.isOther() /*probably an NTFS reparse point*/) {
        return FileVisitResult.SKIP_SUBTREE;
      }
      else {
        return FileVisitResult.CONTINUE;
      }
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
      countFile(file, attrs);
      return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
      return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
      return FileVisitResult.CONTINUE;
    }
  }
}
