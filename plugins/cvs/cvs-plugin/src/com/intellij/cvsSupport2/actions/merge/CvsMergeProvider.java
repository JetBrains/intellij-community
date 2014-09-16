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
package com.intellij.cvsSupport2.actions.merge;

import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.merge.MergeData;
import com.intellij.openapi.vcs.merge.MergeProvider;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.netbeans.lib.cvsclient.admin.Entry;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * @author lesya
 */
public class CvsMergeProvider implements MergeProvider {

  @Override
  @NotNull
  public MergeData loadRevisions(@NotNull VirtualFile file) throws VcsException {
    try {
      final Entry entry = CvsEntriesManager.getInstance().getEntryFor(file);
      final File cvsFile = CvsVfsUtil.getFileFor(file);
      if (entry != null && entry.isResultOfMerge() && entry.isBinary()) {
        final String originalRevision = CvsUtil.getOriginalRevisionForFile(file);
        final MergeData mergeData = new MergeData();
        mergeData.CURRENT = CvsUtil.getStoredContentForFile(file, originalRevision);
        mergeData.LAST = FileUtil.loadFileBytes(cvsFile);
        return mergeData;
      }
      final BufferedInputStream input = new BufferedInputStream(new FileInputStream(cvsFile));
      try {
        final CvsConflictsParser parser = CvsConflictsParser.createOn(input);
        final MergeData mergeData = new MergeData();
        mergeData.ORIGINAL = parser.getCenterVersion().getBytes();
        mergeData.CURRENT = parser.getLeftVersion().getBytes();
        mergeData.LAST = parser.getRightVersion().getBytes();
        return mergeData;
      }
      finally {
        input.close();
      }
    }
    catch (IOException e) {
      throw new VcsException(e);
    }
  }

  @Override
  public void conflictResolvedForFile(@NotNull VirtualFile file) {
    CvsUtil.resolveConflict(file);
    CvsEntriesManager.getInstance().clearCachedEntriesFor(file.getParent());
  }

  @Override
  public boolean isBinary(@NotNull VirtualFile file) {
    return false;
  }
}
