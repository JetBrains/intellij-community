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

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 02.11.2006
 * Time: 22:07:51
 */
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.ui.RollbackProgressModifier;
import com.intellij.openapi.vcs.rollback.RollbackEnvironment;
import com.intellij.openapi.vfs.LocalFileSystem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RollbackDeletionAction extends AbstractMissingFilesAction {
  protected List<VcsException> processFiles(final AbstractVcs vcs, final List<FilePath> files) {
    RollbackEnvironment environment = vcs.getRollbackEnvironment();
    if (environment == null) return Collections.emptyList();
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      indicator.setText(vcs.getDisplayName() + ": performing rollback...");
    }
    final List<VcsException> result = new ArrayList<VcsException>(0);
    try {
      environment.rollbackMissingFileDeletion(files, result, new RollbackProgressModifier(files.size(), indicator));
    }
    catch (ProcessCanceledException e) {
      // for files refresh
    }
    LocalFileSystem.getInstance().refreshIoFiles(ChangesUtil.filePathsToFiles(files));
    return result;
  }

  protected String getName() {
    return VcsBundle.message("changes.action.rollback.text");
  }

  protected boolean synchronously() {
    return false;
  }
}
