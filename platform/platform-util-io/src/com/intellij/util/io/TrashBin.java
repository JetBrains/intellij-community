// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A convenience wrapper around `java.awt.Desktop#moveToTrash`.
 * Make sure the path belongs to the local file system.
 */
@ApiStatus.Experimental
public final class TrashBin {
  private TrashBin() { }

  public static boolean isSupported() {
    return Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH);
  }

  public static void moveToTrash(@NotNull Path path) throws IOException {
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
