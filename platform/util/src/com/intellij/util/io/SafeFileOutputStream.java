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
  private final File myBackupFile;
  private final OutputStream myBackupStream;
  private boolean myFailed = false;

  public SafeFileOutputStream(File target) throws FileNotFoundException {
    this(target, false);
  }

  public SafeFileOutputStream(File target, boolean preserveAttributes) throws FileNotFoundException {
    myTargetFile = target;
    myPreserveAttributes = preserveAttributes;
    myBackupFile = new File(myTargetFile.getParentFile(), myTargetFile.getName() + EXTENSION_BAK);
    //noinspection IOResourceOpenedButNotSafelyClosed
    myBackupStream = new FileOutputStream(myBackupFile);
  }

  @Override
  public void write(byte[] b) throws IOException {
    try {
      myBackupStream.write(b);
    }
    catch (IOException e) {
      LOG.warn(e);
      myFailed = true;
      throw e;
    }
  }

  @Override
  public void write(int b) throws IOException {
    try {
      myBackupStream.write(b);
    }
    catch (IOException e) {
      LOG.warn(e);
      myFailed = true;
      throw e;
    }
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    try {
      myBackupStream.write(b, off, len);
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
      myBackupStream.flush();
    }
    catch (IOException e) {
      LOG.warn(e);
      myFailed = true;
      throw e;
    }
  }

  @Override
  public void close() throws IOException {
    try {
      myBackupStream.close();
    }
    catch (IOException e) {
      LOG.warn(e);
      FileUtil.delete(myBackupFile);
      throw e;
    }

    if (myFailed) {
      throw new IOException(CommonBundle.message("safe.write.failed", myTargetFile, myBackupFile.getName()));
    }

    final File oldFile = new File(myTargetFile.getParent(), myTargetFile.getName() + EXTENSION_OLD);
    try {
      FileUtil.rename(myTargetFile, oldFile);
    }
    catch (IOException e) {
      LOG.warn(e);
      throw new IOException(CommonBundle.message("safe.write.rename.original", myTargetFile, myBackupFile.getName()));
    }

    try {
      FileUtil.rename(myBackupFile, myTargetFile);
    }
    catch (IOException e) {
      LOG.warn(e);
      throw new IOException(CommonBundle.message("safe.write.rename.backup", myTargetFile, oldFile.getName(), myBackupFile.getName()));
    }

    if (myPreserveAttributes) {
      FileSystemUtil.clonePermissions(oldFile.getPath(), myTargetFile.getPath());
    }

    if (!FileUtil.delete(oldFile)) {
      throw new IOException(CommonBundle.message("safe.write.drop.temp", oldFile));
    }
  }
}
