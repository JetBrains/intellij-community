/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffRequestFactory;
import com.intellij.openapi.diff.MergeRequest;
import com.intellij.openapi.diff.SimpleDiffRequest;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.merge.MergeData;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;

import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 2/14/12
 * Time: 4:09 PM
 */
public class ConflictedDiffRequestPresentable implements DiffRequestPresentable {
  private final static Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.actions.ConflictedDiffRequestPresentable");
  private final Project myProject;
  private final VirtualFile myFile;
  private final Change myChange;

  public ConflictedDiffRequestPresentable(final Project project, VirtualFile file, final Change change) {
    myProject = project;
    myFile = file;
    myChange = change;
  }

  @Override
  public MyResult step(DiffChainContext context) {
    if (myChange.getAfterRevision() == null) return createErrorResult();
    FileType type = myChange.getVirtualFile() != null ? myChange.getVirtualFile().getFileType() : null;
    if (myFile.getFileType().isBinary()) {
      final boolean nowItIsText = ChangeDiffRequestPresentable.checkAssociate(myProject, myFile.getName(), context);
      if (! nowItIsText) {
        return createErrorResult();
      }
    }
    final AbstractVcs vcs = ChangesUtil.getVcsForChange(myChange, myProject);
    if (vcs == null || vcs.getMergeProvider() == null) {
      return createErrorResult();
    }
    try {
      final MergeData mergeData = vcs.getMergeProvider().loadRevisions(myFile);
      final Charset charset = myFile.getCharset();
      final MergeRequest request = DiffRequestFactory.getInstance()
        .create3WayDiffRequest(CharsetToolkit.bytesToString(mergeData.CURRENT, charset),
                               CharsetToolkit.bytesToString(mergeData.LAST, charset),
                               CharsetToolkit.bytesToString(mergeData.ORIGINAL, charset),
                               type, myProject, null, null);
      request.setWindowTitle(FileUtil.toSystemDependentName(myFile.getPresentableUrl()));
      // todo titles?
      VcsRevisionNumber lastRevisionNumber = mergeData.LAST_REVISION_NUMBER;
      request.setVersionTitles(new String[]{myChange.getAfterRevision().getRevisionNumber().asString(),
        "Base Version", lastRevisionNumber != null ? lastRevisionNumber.asString() : ""});
      return new MyResult(request, DiffPresentationReturnValue.useRequest);
    }
    catch (VcsException e) {
      LOG.info(e);
      return createErrorResult();
    }
  }

  private MyResult createErrorResult() {
    final SimpleDiffRequest request = new SimpleDiffRequest(myProject, null);
    return new MyResult(request, DiffPresentationReturnValue.removeFromList);
  }

  @Override
  public void haveStuff() throws VcsException {
  }

  @Override
  public List<? extends AnAction> createActions(DiffExtendUIFactory uiFactory) {
    return Collections.emptyList();
  }

  @Override
  public String getPathPresentation() {
    return myFile.getPath();
  }
}
