// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import java.nio.charset.StandardCharsets;

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
        mergeData.ORIGINAL = parser.getCenterVersion().getBytes(StandardCharsets.UTF_8);
        mergeData.CURRENT = parser.getLeftVersion().getBytes(StandardCharsets.UTF_8);
        mergeData.LAST = parser.getRightVersion().getBytes(StandardCharsets.UTF_8);
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
