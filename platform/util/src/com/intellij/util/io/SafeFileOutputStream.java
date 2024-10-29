// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.UtilBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.OSAgnosticPathUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.nio.file.attribute.DosFileAttributeView;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * <p>Attempts to prevent data loss if OS crash happens during write.</p>
 *
 * <p>The class creates a backup copy before overwriting the target file, and issues {@code fsync()} afterwards. The behavior is based
 * on an assumption that after a crash either a target remains unmodified (i.e. unfinished write doesn't reach the disc),
 * or a backup file exists along with a partially overwritten target file.</p>
 *
 * <p><b>The class is not thread-safe</b>; expected to be used within try-with-resources or an equivalent statement.</p>
 *
 * @see com.intellij.openapi.vfs.SafeWriteRequestor
 * @see PreemptiveSafeFileOutputStream
 */
public final class SafeFileOutputStream extends OutputStream {
  private static final String DEFAULT_BACKUP_EXT = "~";
  private static final CopyOption[] BACKUP_COPY = {StandardCopyOption.REPLACE_EXISTING};
  private static final OpenOption[] MAIN_WRITE = {StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.SYNC};
  private static final OpenOption[] BACKUP_READ = {StandardOpenOption.DELETE_ON_CLOSE};

  private final Path myTarget;
  private final String myBackupExt;
  private final @Nullable Future<Path> myBackupFuture;
  private final BufferExposingByteArrayOutputStream myBuffer;
  private boolean myClosed = false;
  private boolean myFailed = false;

  public SafeFileOutputStream(@NotNull File target) {
    this(target.toPath(), DEFAULT_BACKUP_EXT);
  }

  public SafeFileOutputStream(@NotNull File target, @NotNull String backupExt) {
    this(target.toPath(), backupExt);
  }

  public SafeFileOutputStream(@NotNull Path target) {
    this(target, DEFAULT_BACKUP_EXT);
  }

  public SafeFileOutputStream(@NotNull Path target, @NotNull String backupExt) {
    myTarget = target;
    myBackupExt = backupExt;
    myBackupFuture = Files.exists(target) ? AppExecutorUtil.getAppExecutorService().submit(this::backup) : null;
    myBuffer = new BufferExposingByteArrayOutputStream();
  }

  private Path backup() throws IOException {
    Path backup = myTarget.getFileSystem().getPath(myTarget + myBackupExt);
    Files.copy(myTarget, backup, BACKUP_COPY);
    if (OSAgnosticPathUtil.isAbsoluteDosPath(backup.toAbsolutePath().toString())) {
      DosFileAttributeView dosView = Files.getFileAttributeView(backup, DosFileAttributeView.class);
      if (dosView != null && dosView.readAttributes().isReadOnly()) {
        dosView.setReadOnly(false);
      }
    }
    return backup;
  }

  @Override
  public void write(int b) throws IOException {
    if (myFailed) return;
    try {
      myBuffer.write(b);
    }
    catch (OutOfMemoryError err) {
      myFailed = true;
      throw suppressOOM(err);
    }
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    if (myFailed) return;
    try {
      myBuffer.write(b, off, len);
    }
    catch (OutOfMemoryError err) {
      myFailed = true;
      throw suppressOOM(err);
    }
  }

  private IOException suppressOOM(OutOfMemoryError oom) {
    IOException e = new IOException(UtilBundle.message("safe.write.oom", myTarget));
    e.addSuppressed(oom);
    return e;
  }

  public void abort() throws IOException {
    myClosed = true;
    deleteBackup(waitForBackup());
  }

  @Override
  public void close() throws IOException {
    if (myClosed) return;
    myClosed = true;

    if (myFailed) {
      abort();
      return;
    }

    @Nullable Path backup = waitForBackup();
    OutputStream sink = openFile();
    try {
      writeData(sink);
      deleteBackup(backup);
    }
    catch (IOException e) {
      restoreFromBackup(backup, e);
    }
  }

  private @Nullable Path waitForBackup() throws IOException {
    if (myBackupFuture == null) return null;
    try {
      return myBackupFuture.get();
    }
    catch (InterruptedException | CancellationException e) {
      throw new IllegalStateException(e);
    }
    catch (ExecutionException e) {
      throw new IOException(UtilBundle.message("safe.write.backup", myTarget, myTarget.getFileName() + myBackupExt), e.getCause());
    }
  }

  private OutputStream openFile() throws IOException {
    try {
      return Files.newOutputStream(myTarget, MAIN_WRITE);
    }
    catch (IOException e) {
      throw new IOException(UtilBundle.message("safe.write.open", myTarget), e);
    }
  }

  private void writeData(OutputStream sink) throws IOException {
    try (OutputStream out = sink) {
      out.write(myBuffer.getInternalBuffer(), 0, myBuffer.size());
    }
  }

  private static void deleteBackup(Path backup) {
    if (backup != null) {
      try {
        Files.delete(backup);
      }
      catch (IOException e) {
        Logger.getInstance(SafeFileOutputStream.class).warn("cannot delete a backup file " + backup, e);
      }
    }
  }

  private void restoreFromBackup(@Nullable Path backup, IOException e) throws IOException {
    if (backup == null) {
      throw new IOException(UtilBundle.message("safe.write.junk", myTarget), e);
    }

    boolean restored = true;
    try (InputStream in = Files.newInputStream(backup, BACKUP_READ); OutputStream out = Files.newOutputStream(myTarget, MAIN_WRITE)) {
      FileUtil.copy(in, out);
    }
    catch (IOException ex) {
      restored = false;
      e.addSuppressed(ex);
    }
    if (restored) {
      throw new IOException(UtilBundle.message("safe.write.restored", myTarget), e);
    }
    else {
      throw new IOException(UtilBundle.message("safe.write.junk.backup", myTarget, backup.getFileName()), e);
    }
  }
}
