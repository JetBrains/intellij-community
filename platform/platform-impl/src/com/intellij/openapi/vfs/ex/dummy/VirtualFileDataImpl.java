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
package com.intellij.openapi.vfs.ex.dummy;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.LocalTimeCounter;
import org.jetbrains.annotations.NotNull;

import java.io.*;

class VirtualFileDataImpl extends VirtualFileImpl {
  private byte[] myContents = ArrayUtil.EMPTY_BYTE_ARRAY;
  private long myModificationStamp = LocalTimeCounter.currentTime();

  public VirtualFileDataImpl(DummyFileSystem fileSystem, VirtualFileDirectoryImpl parent, String name) {
    super(fileSystem, parent, name);
  }

  public boolean isDirectory() {
    return false;
  }

  public long getLength() {
    return myContents.length;
  }

  public VirtualFile[] getChildren() {
    return null;
  }

  public InputStream getInputStream() throws IOException {
    return new ByteArrayInputStream(myContents);
  }

  @NotNull
  public OutputStream getOutputStream(final Object requestor, final long newModificationStamp, long newTimeStamp) throws IOException {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    return new OutputStream() {
      public void write(int b) throws IOException {
        out.write(b);
      }

      public void write(byte[] b) throws IOException {
        out.write(b);
      }

      public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
      }

      public void flush() throws IOException {
        out.flush();
      }

      public void close() throws IOException {
        out.close();
        final DummyFileSystem fs = (DummyFileSystem)getFileSystem();
        fs.fireBeforeContentsChange(requestor, VirtualFileDataImpl.this);
        final long oldModStamp = myModificationStamp;
        myContents = out.toByteArray();
        myModificationStamp = newModificationStamp >= 0 ? newModificationStamp : LocalTimeCounter.currentTime();
        fs.fireContentsChanged(requestor, VirtualFileDataImpl.this, oldModStamp);
      }
    };
  }

  @NotNull
  public byte[] contentsToByteArray() throws IOException {
    return myContents;
  }

  public long getModificationStamp() {
    return myModificationStamp;
  }

  public void setModificationStamp(long modificationStamp, Object requestor) {
    myModificationStamp = modificationStamp;
  }
}
