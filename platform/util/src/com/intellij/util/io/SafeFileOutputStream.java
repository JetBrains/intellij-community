// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileSystemUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;

import java.io.*;

import static com.intellij.CommonBundle.message;

/**
 * Takes extra caution w.r.t. an existing content. Specifically, if the operation fails for whatever reason
 * (like not enough disk space left), the prior content shall not be overwritten.
 *
 * The class is not thread-safe - use try-with-resources or equivalent try/finally statements to handle.
 *
 * @author max
 */
public class SafeFileOutputStream extends OutputStream {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.io.SafeFileOutputStream");

  private static final boolean DO_SYNC = Registry.is("idea.io.safe.sync");

  private static final String EXTENSION_TMP = "___jb_tmp___";
  private static final String EXTENSION_OLD = "___jb_old___";

  private final File myTargetFile;
  private final boolean myPreserveAttributes;
  private final File myTempFile;
  private final FileOutputStream myOutputStream;
  private boolean myClosed = false;
  private boolean myFailed = false;

  public SafeFileOutputStream(File target) throws FileNotFoundException {
    this(target, false);
  }

  public SafeFileOutputStream(File target, boolean preserveAttributes) throws FileNotFoundException {
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
    if (myClosed) return;
    myClosed = true;

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

    File oldFile = new File(myTargetFile.getParent(), myTargetFile.getName() + EXTENSION_OLD);
    if (oldFile.exists() && !FileUtil.delete(oldFile)) {
      FileUtil.delete(myTempFile);
      throw new IOException(message("safe.write.drop.old", myTargetFile, oldFile.getName()));
    }
    try {
      FileUtil.rename(myTargetFile, oldFile);
    }
    catch (IOException e) {
      LOG.warn(e);
      throw new IOException(message("safe.write.rename.original", myTargetFile, myTempFile.getName()));
    }

    try {
      FileUtil.rename(myTempFile, myTargetFile);
    }
    catch (IOException e) {
      LOG.warn(e);
      throw new IOException(message("safe.write.rename.backup", myTargetFile, oldFile.getName(), myTempFile.getName()));
    }

    if (myPreserveAttributes) {
      FileSystemUtil.clonePermissions(oldFile.getPath(), myTargetFile.getPath());
    }

    if (!FileUtil.delete(oldFile)) {
      throw new IOException(message("safe.write.drop.temp", oldFile));
    }

    if (LOG.isTraceEnabled()) LOG.trace("<< " + myTargetFile);
  }
}