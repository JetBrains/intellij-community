/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import java.io.*;

/**
 * @author max
 */
public class SafeFileOutputStream extends OutputStream {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.io.SafeFileOutputStream");

  private final File myTargetFile;
  private final boolean myPreserveAttributes;
  private final OutputStream myBackDoorStream;
  private boolean failed = false;

  public SafeFileOutputStream(File target) throws FileNotFoundException {
    this(target, false);
  }

  public SafeFileOutputStream(File target, boolean preserveAttributes) throws FileNotFoundException {
    myTargetFile = target;
    myPreserveAttributes = preserveAttributes;
    //noinspection IOResourceOpenedButNotSafelyClosed
    myBackDoorStream = new FileOutputStream(backdoorFile());
  }

  private File backdoorFile() {
    return new File(myTargetFile.getParentFile(), myTargetFile.getName() + "___jb_bak___");
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
      FileUtil.delete(backdoorFile());
      throw e;
    }

    final int permissions = myPreserveAttributes ? FileSystemUtil.getPermissions(myTargetFile) : -1;
    if (failed || !FileUtil.delete(myTargetFile)) {
      throw new IOException("Failed to save to " + myTargetFile + ". No data were harmed. Attempt result left at " + backdoorFile());
    }

    FileUtil.rename(backdoorFile(), myTargetFile);

    if (permissions != -1) {
      FileSystemUtil.setPermissions(myTargetFile, permissions);
    }
  }
}
