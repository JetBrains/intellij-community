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
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.BeforeAfter;

import java.util.List;

/**
 * @author irengrig
 *         Date: 7/6/11
 *         Time: 8:46 PM
 */
public class FragmentedContent {
  private final Document myBefore;
  private final Document myAfter;
  private final List<BeforeAfter<TextRange>> myRanges;

  private final boolean myOneSide;
  private final boolean myIsAddition;

  private final VirtualFile myFileBefore;
  private final VirtualFile myFileAfter;
  private final FileType myFileTypeBefore;
  private final FileType myFileTypeAfter;

  public FragmentedContent(Document before, Document after, List<BeforeAfter<TextRange>> ranges, Change change) {
    myBefore = before;
    myAfter = after;
    myRanges = ranges;

    final FileStatus fs = change.getFileStatus();
    myIsAddition = FileStatus.ADDED.equals(fs);
    myOneSide = FileStatus.ADDED.equals(fs) || FileStatus.DELETED.equals(fs);

    if (change.getBeforeRevision() != null) {
      myFileBefore = change.getBeforeRevision().getFile().getVirtualFile();
      myFileTypeBefore = change.getBeforeRevision().getFile().getFileType();
    }
    else {
      myFileBefore = null;
      myFileTypeBefore = null;
    }

    if (change.getAfterRevision() != null) {
      myFileAfter = change.getAfterRevision().getFile().getVirtualFile();
      myFileTypeAfter = change.getAfterRevision().getFile().getFileType();
    }
    else {
      myFileAfter = null;
      myFileTypeAfter = null;
    }
  }

  public Document getBefore() {
    return myBefore;
  }

  public Document getAfter() {
    return myAfter;
  }

  public List<BeforeAfter<TextRange>> getRanges() {
    return myRanges;
  }

  public int getSize() {
    return myRanges.size();
  }

  public boolean isOneSide() {
    return myOneSide;
  }

  public boolean isAddition() {
    return myIsAddition;
  }

  public VirtualFile getFileBefore() {
    return myFileBefore;
  }

  public VirtualFile getFileAfter() {
    return myFileAfter;
  }

  public FileType getFileTypeBefore() {
    return myFileTypeBefore;
  }

  public FileType getFileTypeAfter() {
    return myFileTypeAfter;
  }
}
