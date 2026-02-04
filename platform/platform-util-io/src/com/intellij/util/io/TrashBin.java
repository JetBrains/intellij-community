// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.util.SystemProperties;
import com.intellij.util.system.OS;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import static com.intellij.openapi.util.NullableLazyValue.volatileLazyNullable;
import static java.util.Objects.requireNonNullElseGet;

/**
 * A convenience wrapper around `java.awt.Desktop#moveToTrash`.
 * Make sure the path belongs to the local file system.
 */
@ApiStatus.Experimental
public final class TrashBin {
  private TrashBin() { }

  private sealed interface Trash {
    void moveToTrash(@NotNull Path path) throws IOException;
  }

  private static final NullableLazyValue<Trash> TRASH = volatileLazyNullable(() -> {
    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH)) {
      return new DesktopTrashImpl();
    }
    else if (OS.isGenericUnix()) {
      var dataDir = requireNonNullElseGet(System.getenv("XDG_DATA_HOME"), () -> SystemProperties.getUserHome() + "/.local/share");
      var filesDir = Path.of(dataDir, "Trash/files");
      var infoDir = Path.of(dataDir, "Trash/info");
      if (Files.isDirectory(filesDir) && Files.isDirectory(infoDir)) {
        return new XdgTrashImpl(filesDir, infoDir);
      }
    }
    return null;
  });

  public static boolean isSupported() {
    return TRASH.getValue() != null;
  }

  public static boolean canMoveToTrash(@NotNull Path path) {
    try {
      return Objects.equals(Files.getFileStore(path), Files.getFileStore(Path.of(SystemProperties.getUserHome())));
    }
    catch (IOException ignored) { }
    return false;
  }

  public static boolean canMoveToTrash(@NotNull VirtualFile file) {
    final Path path = file.getFileSystem().getNioPath(file);
    return file.isInLocalFileSystem() && path != null && canMoveToTrash(path);
  }

  public static void moveToTrash(@NotNull Path path) throws IOException {
    Objects.requireNonNull(TRASH.getValue()).moveToTrash(path);
  }

  private static final class DesktopTrashImpl implements Trash {
    @Override
    public void moveToTrash(@NotNull Path path) throws IOException {
      @SuppressWarnings("IO_FILE_USAGE") var ioFile = path.toFile();

      var trashed = false;
      try {
        trashed = Desktop.getDesktop().moveToTrash(ioFile);
      }
      catch (IllegalArgumentException ignored) { }

      if (!trashed && Files.exists(path)) {
        throw new FileSystemException(path.toString(), null, IdeUtilIoBundle.message("error.message.cannot.trash"));
      }
    }
  }

  private static final class XdgTrashImpl implements Trash {
    @SuppressWarnings("SpellCheckingInspection") private static final String DOT_TRASH_INFO = ".trashinfo";

    private final Path filesDir, infoDir;

    private XdgTrashImpl(Path filesDir, Path infoDir) {
      this.filesDir = filesDir;
      this.infoDir = infoDir;
    }

    @Override
    public void moveToTrash(@NotNull Path path) throws IOException {
      path = path.toAbsolutePath();
      if (!Files.exists(path)) {
        return;
      }

      // replicating Nautilus' behavior
      var originalName = path.getFileName().toString();
      var trashFile = filesDir.resolve(originalName);
      var trashInfo = infoDir.resolve(originalName + DOT_TRASH_INFO);
      int counter = 2;
      while (Files.exists(trashFile) || Files.exists(trashInfo)) {
        trashFile = filesDir.resolve(originalName + '.' + counter);
        trashInfo = infoDir.resolve(originalName + '.' + counter + DOT_TRASH_INFO);
        counter++;
      }

      var ts = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
      var infoContent = "[Trash Info]\nPath=" + path + "\nDeletionDate=" + ts + "\n";

      try {
        Files.writeString(trashInfo, infoContent, StandardOpenOption.CREATE_NEW);
        try {
          Files.move(path, trashFile, StandardCopyOption.ATOMIC_MOVE);
        }
        catch (IOException e) {
          try {
            Files.deleteIfExists(trashInfo);
          }
          catch (IOException ee) {
            e.addSuppressed(ee);
          }
          throw e;
        }
      }
      catch (IOException e) {
        var fse = new FileSystemException(path.toString(), null, IdeUtilIoBundle.message("error.message.cannot.trash"));
        fse.initCause(e);
        throw fse;
      }
    }
  }
}
