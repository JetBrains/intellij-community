/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.CommonBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileSystemUtil;
import com.intellij.openapi.util.io.FileUtil;

import java.io.*;

/**
 * @author max
 */
public class SafeFileOutputStream extends OutputStream {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.io.SafeFileOutputStream");

  private static final String EXTENSION_BAK = "___jb_bak___";
  private static final String EXTENSION_OLD = "___jb_old___";

  private final File myTargetFile;
  private final boolean myPreserveAttributes;
  private final File myBackDoorFile;
  private final OutputStream myBackDoorStream;
  private boolean failed = false;

  public SafeFileOutputStream(File target) throws FileNotFoundException {
    this(target, false);
  }

  public SafeFileOutputStream(File target, boolean preserveAttributes) throws FileNotFoundException {
    myTargetFile = target;
    myPreserveAttributes = preserveAttributes;
    myBackDoorFile = new File(myTargetFile.getParentFile(), myTargetFile.getName() + EXTENSION_BAK);
    //noinspection IOResourceOpenedButNotSafelyClosed
    myBackDoorStream = new FileOutputStream(myBackDoorFile);
  }

  @Override
  public void write(byte[] b) throws IOException {
    try {
      myBackDoorStream.write(b);
    }
    catch (IOException e) {
      LOG.warn(e);
      failed = true;
      throw e;
    }
  }

  @Override
  public void write(int b) throws IOException {
    try {
      myBackDoorStream.write(b);
    }
    catch (IOException e) {
      LOG.warn(e);
      failed = true;
      throw e;
    }
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    try {
      myBackDoorStream.write(b, off, len);
    }
    catch (IOException e) {
      LOG.warn(e);
      failed = true;
      throw e;
    }
  }

  @Override
  public void flush() throws IOException {
    try {
      myBackDoorStream.flush();
    }
    catch (IOException e) {
      LOG.warn(e);
      failed = true;
      throw e;
    }
  }

  @Override
  public void close() throws IOException {
    try {
      myBackDoorStream.close();
    }
    catch (IOException e) {
      LOG.warn(e);
      FileUtil.delete(myBackDoorFile);
      throw e;
    }

    if (failed) {
      throw new IOException(CommonBundle.message("safe.write.failed",
                                                 myTargetFile, myBackDoorFile.getName()));
    }

    final File oldFile = new File(myTargetFile.getParent(), myTargetFile.getName() + EXTENSION_OLD);
    try {
      FileUtil.rename(myTargetFile, oldFile);
    }
    catch (IOException e) {
      LOG.warn(e);
      throw new IOException(CommonBundle.message("safe.write.rename.original",
                                                 myTargetFile, myBackDoorFile.getName()));
    }

    try {
      FileUtil.rename(myBackDoorFile, myTargetFile);
    }
    catch (IOException e) {
      LOG.warn(e);
      throw new IOException(CommonBundle.message("safe.write.rename.backup",
                                                 myTargetFile, oldFile.getName(), myBackDoorFile.getName()));
    }

    if (myPreserveAttributes) {
      FileSystemUtil.clonePermissions(oldFile.getPath(), myTargetFile.getPath());
    }

    if (!FileUtil.delete(oldFile)) {
      throw new IOException(CommonBundle.message("safe.write.drop.temp", oldFile));
    }
  }
}
