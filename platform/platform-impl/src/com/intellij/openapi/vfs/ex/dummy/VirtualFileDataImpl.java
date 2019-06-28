// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.ex.dummy;

import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.LocalTimeCounter;
import org.jetbrains.annotations.NotNull;

import java.io.*;

class VirtualFileDataImpl extends VirtualFileImpl {
  private byte[] myContents = ArrayUtilRt.EMPTY_BYTE_ARRAY;
  private long myModificationStamp = LocalTimeCounter.currentTime();

  VirtualFileDataImpl(DummyFileSystem fileSystem, VirtualFileDirectoryImpl parent, String name) {
    super(fileSystem, parent, name);
  }

  @Override
  public boolean isDirectory() {
    return false;
  }

  @Override
  public long getLength() {
    return myContents.length;
  }

  @Override
  public VirtualFile[] getChildren() {
    return null;
  }

  @Override
  public InputStream getInputStream() throws IOException {
    return VfsUtilCore.byteStreamSkippingBOM(myContents, this);
  }

  @Override
  @NotNull
  public OutputStream getOutputStream(final Object requestor, final long newModificationStamp, long newTimeStamp) throws IOException {
    return VfsUtilCore.outputStreamAddingBOM(new ByteArrayOutputStream() {
      @Override
      public void close() {
        final DummyFileSystem fs = (DummyFileSystem)getFileSystem();
        fs.fireBeforeContentsChange(requestor, VirtualFileDataImpl.this);
        final long oldModStamp = myModificationStamp;
        myContents = toByteArray();
        myModificationStamp = newModificationStamp >= 0 ? newModificationStamp : LocalTimeCounter.currentTime();
        fs.fireContentsChanged(requestor, VirtualFileDataImpl.this, oldModStamp);
      }
    },this);
  }

  @Override
  @NotNull
  public byte[] contentsToByteArray() throws IOException {
    return myContents;
  }

  @Override
  public long getModificationStamp() {
    return myModificationStamp;
  }

  public void setModificationStamp(long modificationStamp, Object requestor) {
    myModificationStamp = modificationStamp;
  }
}
