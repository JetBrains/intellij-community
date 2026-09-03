// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.system.NativeAccess;
import com.intellij.util.system.OS;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Locale;

public final class FileSystemUtil {
  private static final Logger LOG = Logger.getInstance(FileSystemUtil.class);

  private FileSystemUtil() { }

  /** Please use NIO API instead ({@link Files}, etc.) */
  @ApiStatus.Obsolete
  public static @Nullable FileAttributes getAttributes(@NotNull String path) {
    path = normalizePath(path);
    try {
      return getAttributes(Paths.get(path));
    }
    catch (InvalidPathException e) {
      LOG.debug(e);
      return null;
    }
  }

  private static String normalizePath(@NotNull String path) {
    if (OS.CURRENT == OS.Windows && path.length() == 2 && path.charAt(1) == ':') {
      path += '\\';
    }
    return path;
  }

  /** Please use NIO API instead ({@link Files}, etc.) */
  @ApiStatus.Obsolete
  @SuppressWarnings({"IO_FILE_USAGE", "UnnecessaryFullyQualifiedName"})
  public static @Nullable FileAttributes getAttributes(@NotNull java.io.File file) {
    return getAttributes(file.toPath());
  }

  /** Please use NIO API instead ({@link Files}, etc.) */
  @ApiStatus.Obsolete
  @SuppressWarnings({"IO_FILE_USAGE", "UnnecessaryFullyQualifiedName"})
  public static long lastModified(@NotNull java.io.File file) {
    FileAttributes attributes = getAttributes(file);
    return attributes != null ? attributes.lastModified : 0;
  }

  /** Please use NIO API instead ({@link Files}, etc.) */
  @ApiStatus.Obsolete
  public static boolean isSymLink(@NotNull String path) {
    FileAttributes attributes = getAttributes(path);
    return attributes != null && attributes.isSymLink();
  }

  /** Please use NIO API instead ({@link Files}, etc.) */
  @ApiStatus.Obsolete
  @SuppressWarnings({"IO_FILE_USAGE", "UnnecessaryFullyQualifiedName"})
  public static boolean isSymLink(@NotNull java.io.File file) {
    return isSymLink(file.getAbsolutePath());
  }

  /** Please use NIO API instead ({@link Files}, etc.) */
  @ApiStatus.Obsolete
  public static @Nullable String resolveSymLink(@NotNull String path) {
    try {
      return resolveSymLink(Paths.get(path));
    }
    catch (InvalidPathException e) {
      LOG.debug(e);
      return null;
    }
  }

  /** Please use NIO API instead ({@link Files}, etc.) */
  @ApiStatus.Obsolete
  @SuppressWarnings({"IO_FILE_USAGE", "UnnecessaryFullyQualifiedName"})
  public static @Nullable String resolveSymLink(@NotNull java.io.File file) {
    return resolveSymLink(file.toPath());
  }

  private static @Nullable FileAttributes getAttributes(Path path) {
    try {
      return getAttributesNotNull(path);
    }
    catch (NoSuchFileException e) {
      LOG.trace(e.getClass().getName() + ": " + path);
      return null;
    }
    catch (IOException e) {
      LOG.debug(path.toString(), e);
      return null;
    }
  }

  private static FileAttributes getAttributesNotNull(Path path) throws IOException {
    BasicFileAttributes attributes = NioFiles.readAttributes(path);
    return attributes == NioFiles.BROKEN_SYMLINK ? FileAttributes.BROKEN_SYMLINK : FileAttributes.fromNio(path, attributes);
  }

  private static @Nullable String resolveSymLink(Path path) {
    try {
      return path.toRealPath().toString();
    }
    catch (NoSuchFileException e) {
      LOG.trace(e.getClass().getName() + ": " + path);
    }
    catch (FileSystemException e) {
      LOG.debug(path.toString(), e);
    }
    catch (IOException e) {
      LOG.warn(e);
    }
    return null;
  }

  /**
   * Detects case-sensitivity of the directory containing {@code anyChild} (or {@code anyChild} itself, if it happens to be
   * a filesystem root) – first by calling platform-specific APIs if possible, then falling back to querying its attributes
   * via different names.
   */
  @ApiStatus.Internal
  public static FileAttributes.@NotNull CaseSensitivity readParentCaseSensitivity(@NotNull Path anyChild) {
    Path parent = anyChild.getParent();
    FileAttributes.CaseSensitivity detected = readDirectoryCaseSensitivityByNativeAPI(parent != null ? parent : anyChild);
    if (detected.isKnown()) return detected;
    // native queries failed, fallback to the Java I/O:
    return readParentCaseSensitivityByJavaIO(anyChild);
  }

  @VisibleForTesting
  @ApiStatus.Internal
  public static FileAttributes.@NotNull CaseSensitivity readParentCaseSensitivityByJavaIO(@NotNull Path anyChild) {
    Path parent = anyChild.getParent();
    if (parent == null) {
      return readDirectoryCaseSensitivityComparingTwoChildren(anyChild);
    }

    // try to query this path by different-case strings and deduce case sensitivity from the answers
    String originalName = anyChild.getFileName().toString();
    String toggledCaseName = toggleCase(originalName);
    if (!toggledCaseName.equals(originalName)) {
      return readParentCaseSensitivityComparingToSibling(anyChild, anyChild.resolveSibling(toggledCaseName));
    }

    // we have a bad case of non-alphabetic file name
    return readDirectoryCaseSensitivityComparingTwoChildren(parent);
  }

  private static FileAttributes.CaseSensitivity readDirectoryCaseSensitivityComparingTwoChildren(Path parent) {
    Path probe = findCaseToggleableChild(parent);
    return (
      probe == null
      ? FileAttributes.CaseSensitivity.UNKNOWN
      : readParentCaseSensitivityComparingToSibling(probe, probe.resolveSibling(toggleCase(probe.getFileName().toString())))
    );
  }

  private static FileAttributes.CaseSensitivity readParentCaseSensitivityComparingToSibling(Path anyChild, Path toggledCasePath) {
    try {
      FileAttributes toggledCaseAttributes;

      try {
        toggledCaseAttributes = getAttributesNotNull(toggledCasePath);
      }
      catch (NoSuchFileException e) {
        if (!Files.exists(anyChild)) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("readParentCaseSensitivityByJavaIO(" + anyChild + "): does not exist");
          }
          return FileAttributes.CaseSensitivity.UNKNOWN;
        }
        LOG.trace(e.getClass().getName() + ": " + toggledCasePath);
        toggledCaseAttributes = null;
      }
      catch (IOException e) {
        LOG.debug(toggledCasePath.toString(), e);
        toggledCaseAttributes = null;
      }
      catch (InvalidPathException e) {
        LOG.debug(e);
        toggledCaseAttributes = null;
      }

      if (toggledCaseAttributes == null) {
        // couldn't find this file by other-cased name, so deduce FS is sensitive
        return FileAttributes.CaseSensitivity.SENSITIVE;
      }
      // if a changed-case file is found, there is a slim chance that the FS is still case-sensitive,
      // but there are two files with a different case
      Path altCanonicalFile = toggledCasePath.toRealPath(LinkOption.NOFOLLOW_LINKS);
      String altCanonicalName = altCanonicalFile.getFileName().toString();
      if (
        altCanonicalName.equals(anyChild.getFileName().toString()) ||
        altCanonicalName.equals(anyChild.toRealPath(LinkOption.NOFOLLOW_LINKS).getFileName().toString())
      ) {
        // nah, these two are really the same file
        return FileAttributes.CaseSensitivity.INSENSITIVE;
      }
    }
    catch (IOException e) {
      LOG.debug("readParentCaseSensitivityByJavaIO(" + anyChild + ")", e);
      return FileAttributes.CaseSensitivity.UNKNOWN;
    }

    // it's a different file indeed; tough luck
    return FileAttributes.CaseSensitivity.SENSITIVE;
  }

  @VisibleForTesting
  @ApiStatus.Internal
  public static FileAttributes.@NotNull CaseSensitivity readDirectoryCaseSensitivityByNativeAPI(@NotNull Path directory) {
    return NativeAccess.getInstance().getDirectoryCaseSensitivity(directory);
  }

  private static String toggleCase(String name) {
    String altName = name.toUpperCase(Locale.getDefault());
    if (altName.equals(name)) altName = name.toLowerCase(Locale.getDefault());
    return altName;
  }

  /**
   * @return {@code true} when the {@code name} contains case-toggleable characters (for which toLowerCase() != toUpperCase()).
   * E.g. "Child.txt" is case-toggleable because "CHILD.TXT" != "child.txt", but "122.45" is not.
   */
  @ApiStatus.Internal
  public static boolean isCaseToggleable(@NotNull String name) {
    return !toggleCase(name).equals(name);
  }

  // returns a child whose name can be used for querying by different-case names (e.g. "child.txt" vs. "CHILD.TXT")
  // or `null` if there are none (e.g., there's only one child "123.456")
  private static @Nullable Path findCaseToggleableChild(Path dir) {
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
      for (Path child : stream) {
        if (!Files.exists(child)) continue;  // skipping degenerate files (WSL symlinks, etc.)
        String name = child.getFileName().toString();
        if (!name.toLowerCase(Locale.getDefault()).equals(name.toUpperCase(Locale.getDefault()))) {
          return child;
        }
      }
    }
    catch (Exception ignored) { }
    if (LOG.isDebugEnabled()) {
      List<Path> list = null;
      try {
        list = NioFiles.list(dir);
      }
      catch (Exception ignored) { }
      if (list == null) {
        LOG.debug("findCaseToggleableChild(" + dir + "): dir.list() failed");
      }
      else {
        LOG.debug("findCaseToggleableChild(" + dir + "): no toggleable child among " + list.size() + " siblings");
        if (LOG.isTraceEnabled()) LOG.trace("findCaseToggleableChild(" + dir + "): " + list);
      }
    }
    return null;
  }
}
