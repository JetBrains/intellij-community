/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author yole
 */
public class VcsCurrentRevisionProxy implements ContentRevision {
  private final DiffProvider myDiffProvider;
  private final VirtualFile myFile;
  private ContentRevision myVcsRevision;

  public VcsCurrentRevisionProxy(final DiffProvider diffProvider, final VirtualFile file) {
    myDiffProvider = diffProvider;
    myFile = file;
  }

  @Nullable
  public String getContent() throws VcsException {
    return getVcsRevision().getContent();
  }

  @NotNull
  public FilePath getFile() {
    return new FilePathImpl(myFile);
  }

  @NotNull
  public VcsRevisionNumber getRevisionNumber() {
    try {
      return getVcsRevision().getRevisionNumber();
    }
    catch(VcsException ex) {
      return VcsRevisionNumber.NULL;
    }
  }

  private ContentRevision getVcsRevision() throws VcsException {
    if (myVcsRevision == null) {
      final VcsRevisionNumber currentRevision = myDiffProvider.getCurrentRevision(myFile);
      if (currentRevision == null) {
        throw new VcsException("Failed to fetch current revision");
      }
      myVcsRevision = myDiffProvider.createFileContent(currentRevision, myFile);
      if (myVcsRevision == null) {
        throw new VcsException("Failed to create content for current revision");
      }
    }
    return myVcsRevision;
  }
}
