// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.*;

/**
 * <p>A somewhat weaker version of {@link SafeFileOutputStream} suitable for writing huge files whose content can't fit into memory.
 * Relies entirely on {@link StandardCopyOption#ATOMIC_MOVE} guarantees; always recreates the target file.</p>
 *
 * <p><b>The class is not thread-safe</b>; expected to be used within try-with-resources or an equivalent statement.</p>
 *
 * @see com.intellij.openapi.vfs.LargeFileWriteRequestor
 * @see SafeFileOutputStream
 */
public final class PreemptiveSafeFileOutputStream extends OutputStream {
  private static final String TEMP_EXT = ".tmp";
  private static final String BACKUP_EXT = "~";
  private static final OpenOption[] TEMP_WRITE = {StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.DSYNC};
  private static final CopyOption[] RENAME = {StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE};

  private final Path myTarget;
  private final Path myTemp;
  private final OutputStream myOutputStream;
  private boolean myFailed = false;
  private boolean myClosed = false;

  public PreemptiveSafeFileOutputStream(@NotNull Path target) throws IOException {
    myTarget = target;
    myTemp = myTarget.getFileSystem().getPath(myTarget + TEMP_EXT);
    myOutputStream = Files.newOutputStream(myTemp, TEMP_WRITE);
  }

  @Override
  public void write(int b) throws IOException {
    try {
      myOutputStream.write(b);
    }
    catch (IOException e) {
      myFailed = true;
      throw e;
    }
  }

  @Override
  public void write(byte @NotNull [] b, int off, int len) throws IOException {
    try {
      myOutputStream.write(b, off, len);
    }
    catch (IOException e) {
      myFailed = true;
      throw e;
    }
  }

  @Override
  public void flush() throws IOException {
    try {
      myOutputStream.flush();
    }
    catch (IOException e) {
      myFailed = true;
      throw e;
    }
  }

  public void abort() {
    if (myClosed) return;
    myClosed = true;

    try {
      myOutputStream.close();
    }
    catch (IOException e) {
      Logger.getInstance(getClass()).warn(e);
    }

    deleteFile(myTemp);
  }

  @Override
  public void close() throws IOException {
    if (myClosed) return;
    myClosed = true;

    try {
      myOutputStream.close();
    }
    catch (IOException e) {
      deleteFile(myTemp);
      error("Cannot write a temporary file " + myTemp.getFileName(), e);
    }

    if (myFailed) {
      deleteFile(myTemp);
      error("Cannot write a temporary file " + myTemp.getFileName(), null);
    }

    Path backup = null;
    if (Files.exists(myTarget)) {
      backup = myTarget.getFileSystem().getPath(myTarget + BACKUP_EXT);
      try {
        Files.move(myTarget, backup, RENAME);
      }
      catch (IOException e) {
        deleteFile(myTemp);
        error("Cannot create a backup file " + backup.getFileName() + ".\n" +
              "The original file is unchanged; new data are written into " + myTemp.getFileName(), e);
      }
    }

    try {
      Files.move(myTemp, myTarget, RENAME);
    }
    catch (IOException e) {
      if (backup == null) {
        error("Cannot rename a temporary file " + myTemp.getFileName(), e);
      }

      try {
        Files.move(backup, myTarget, RENAME);
      }
      catch (IOException ee) {
        error("Cannot rename a temporary file " + myTemp.getFileName() + ".\n" +
              "The original file was renamed into " + backup.getFileName() + "; new data are written into " + myTemp.getFileName(), e);
      }

      error("Cannot rename a temporary file " + myTemp.getFileName() + ".\n" +
            "The original file was restored from a backup; new data are written into " + myTemp.getFileName(), e);
    }

    if (backup != null) {
      deleteFile(backup);
    }
  }

  @Contract("_, _ -> fail")
  private void error(String text, @Nullable IOException e) throws IOException {
    throw new IOException("Cannot save " + myTarget + ".\n" + text, e);
  }

  private static void deleteFile(Path file) {
    try {
      Files.delete(file);
    }
    catch (IOException e) {
      Logger.getInstance(PreemptiveSafeFileOutputStream.class).warn("cannot delete " + file, e);
    }
  }
}
