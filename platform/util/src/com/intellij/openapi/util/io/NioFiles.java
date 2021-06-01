// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util.io;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import java.util.function.Consumer;

import static java.nio.file.attribute.PosixFilePermission.*;

/**
 * A utility class providing pieces missing from {@link Files java.nio.file.Files}.
 */
public final class NioFiles {
  private NioFiles() { }

  /**
   * A drop-in replacement for {@link Files#createDirectories} that doesn't stumble upon symlinks - unlike the original.
   * I.e. this method accepts "/path/.../dir_link" (where "dir_link" is a symlink to a directory), while the original fails.
   */
  public static @NotNull Path createDirectories(@NotNull Path path) throws IOException {
    if (!Files.isDirectory(path)) {
      if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
        throw new FileAlreadyExistsException(path.toString(), null, "already exists");
      }
      else {
        Path parent = path.getParent();
        if (parent != null) {
          createDirectories(parent);
        }
        Files.createDirectory(path);
      }
    }
    return path;
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
   * On POSIX file systems, sets "owner-exec" permission (if not already set); on others, does nothing.
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
   * See {@link #deleteRecursively(Path, Consumer)}.
   */
  public static void deleteRecursively(@NotNull Path fileOrDirectory) throws IOException {
    FileUtilRt.deleteRecursivelyNIO(fileOrDirectory, null);
  }

  /**
   * <p>Recursively deletes the given directory or file, if it exists. Does not follow symlinks or junctions
   * (i.e. deletes just links, not targets). Invokes the callback before deleting a file or a directory
   * (the latter - after deleting it's content). Fails fast (throws an exception right after meeting a problematic file and
   * does not tries to delete the rest first).</p>
   *
   * <p>Implementation detail: the method tries to delete a file up to 10 times with 10 ms pause between attempts -
   * usually it's enough to overcome intermittent file lock on Windows.</p>
   */
  public static void deleteRecursively(@NotNull Path fileOrDirectory, @NotNull Consumer<Path> callback) throws IOException {
    FileUtilRt.deleteRecursivelyNIO(fileOrDirectory, o -> callback.accept((Path)o));
  }
}
