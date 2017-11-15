// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl.local;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileSystemUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.io.SafeFileOutputStream;

import java.io.*;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import static com.intellij.CommonBundle.message;

/**
 * @author max
 */
public class AtomicSafeFileOutputStream extends OutputStream {
  private static final Logger LOG = Logger.getInstance(AtomicSafeFileOutputStream.class);

  private static final boolean DO_SYNC = Registry.is("idea.io.safe.sync");

  private static final String EXTENSION_TMP = "___jb_tmp___";

  private final File myTargetFile;
  private final boolean myPreserveAttributes;
  private final File myTempFile;
  private final FileOutputStream myOutputStream;
  private boolean myFailed = false;


  public AtomicSafeFileOutputStream(File target, boolean preserveAttributes) throws FileNotFoundException {
    if (LOG.isTraceEnabled()) LOG.trace(">> " + target);
    myTargetFile = target;
    myPreserveAttributes = preserveAttributes;
    myTempFile = new File(myTargetFile.getPath() + EXTENSION_TMP);
    myOutputStream = new FileOutputStream(myTempFile);
  }

  @Override
  public void write(int b) throws IOException {
    try {
      myOutputStream.write(b);
    }
    catch (IOException e) {
      LOG.warn(e);
      myFailed = true;
      throw e;
    }
  }

  @Override
  public void write(byte[] b) throws IOException {
    write(b, 0, b.length);
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    try {
      myOutputStream.write(b, off, len);
    }
    catch (IOException e) {
      LOG.warn(e);
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
      LOG.warn(e);
      myFailed = true;
      throw e;
    }
  }

  @Override
  public void close() throws IOException {
    if (!myFailed && DO_SYNC) {
      try {
        myOutputStream.getFD().sync();
      }
      catch (IOException e) {
        LOG.warn(e);
        myFailed = true;
      }
    }

    try {
      myOutputStream.close();
    }
    catch (IOException e) {
      LOG.warn(e);
      myFailed = true;
    }

    if (myFailed) {
      FileUtil.delete(myTempFile);
      throw new IOException(message("safe.write.failed", myTargetFile, myTempFile.getName()));
    }


    if (myPreserveAttributes) {
      FileSystemUtil.clonePermissions(myTargetFile.getPath(), myTempFile.getPath());
    }

    try {
      Files.move(myTempFile.toPath(), myTargetFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
    catch (AtomicMoveNotSupportedException e) {
      SafeFileOutputStream.replaceFile(myTempFile, myTargetFile, myPreserveAttributes);
    }
    catch (IOException e) {
      LOG.warn(e);
      throw e;
    }

    if (LOG.isTraceEnabled()) LOG.trace("<< " + myTargetFile);
  }
}