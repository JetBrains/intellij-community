/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileSystemUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;

import java.io.*;

import static com.intellij.CommonBundle.message;

/**
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
  private boolean myFailed = false;

  public SafeFileOutputStream(File target) throws FileNotFoundException {
    this(target, false);
  }

  public SafeFileOutputStream(File target, boolean preserveAttributes) throws FileNotFoundException {
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

    File oldFile = new File(myTargetFile.getParent(), myTargetFile.getName() + EXTENSION_OLD);
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
  }
}