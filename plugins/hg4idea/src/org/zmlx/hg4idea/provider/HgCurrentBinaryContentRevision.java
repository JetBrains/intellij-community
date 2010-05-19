// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea.provider;

import org.zmlx.hg4idea.HgFile;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.changes.BinaryContentRevision;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;

final class HgCurrentBinaryContentRevision
  extends HgCurrentContentRevision implements BinaryContentRevision {

  private final VirtualFile virtualFile;

  HgCurrentBinaryContentRevision(HgFile hgFile,
    VcsRevisionNumber revisionNumber, VirtualFile virtualFile) {
    super(hgFile, revisionNumber, virtualFile);
    this.virtualFile = virtualFile;
  }

  @Nullable
  public byte[] getBinaryContent() throws VcsException {
    try {
      return virtualFile.contentsToByteArray();
    } catch (IOException e) {
      throw new VcsException(e);
    }
  }

}
