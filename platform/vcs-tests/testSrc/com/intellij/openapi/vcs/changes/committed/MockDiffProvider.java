// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.diff.ItemLatestState;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;


public class MockDiffProvider implements DiffProvider {
  private final Map<VirtualFile, VcsRevisionNumber> myCurrentRevisionNumbers = new HashMap<>();
  
  public void setCurrentRevision(VirtualFile file, VcsRevisionNumber number) {
    myCurrentRevisionNumbers.put(file, number);
  }

  @Override
  @Nullable
  public VcsRevisionNumber getCurrentRevision(VirtualFile file) {
    return myCurrentRevisionNumbers.get(file);
  }

  @Override
  @Nullable
  public ItemLatestState getLastRevision(VirtualFile virtualFile) {
    throw new UnsupportedOperationException();
  }

  @Override
  @Nullable
  public ContentRevision createFileContent(VcsRevisionNumber revisionNumber, VirtualFile selectedFile) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ItemLatestState getLastRevision(FilePath filePath) {
    throw new UnsupportedOperationException();
  }

  @Override
  public VcsRevisionNumber getLatestCommittedRevision(VirtualFile vcsRoot) {
    throw new UnsupportedOperationException();
  }
}
