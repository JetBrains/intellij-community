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
package com.intellij.openapi.vcs.changes.actions.migrate;

import com.intellij.diff.DiffDialogHints;
import com.intellij.diff.DiffManager;
import com.intellij.diff.DiffRequestPanel;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diff.DiffRequest;
import com.intellij.openapi.diff.DiffTool;
import com.intellij.openapi.diff.DiffViewer;
import com.intellij.openapi.diff.MergeRequest;
import com.intellij.openapi.diff.impl.external.FrameDiffTool;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.WindowWrapper;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class MigrateDiffTool implements DiffTool {
  public static final MigrateDiffTool INSTANCE = new MigrateDiffTool();

  private MigrateDiffTool() {
  }

  @Override
  public void show(DiffRequest request) {
    com.intellij.diff.requests.DiffRequest newRequest = MigrateToNewDiffUtil.convertRequest(request);

    Runnable onOkRunnable = request.getOnOkRunnable();
    if (onOkRunnable == null) {
      WindowWrapper.Mode mode = FrameDiffTool.shouldOpenDialog(request.getHints()) ? WindowWrapper.Mode.MODAL : WindowWrapper.Mode.FRAME;
      DiffManager.getInstance().showDiff(request.getProject(), newRequest, new DiffDialogHints(mode));
    }
    else {
      DialogBuilder builder = new DialogBuilder(request.getProject());
      DiffRequestPanel diffPanel = DiffManager.getInstance().createRequestPanel(request.getProject(), builder, builder.getWindow());
      diffPanel.setRequest(newRequest);

      builder.setCenterPanel(diffPanel.getComponent());
      builder.setPreferredFocusComponent(diffPanel.getPreferredFocusedComponent());
      builder.setTitle(request.getWindowTitle());
      builder.setDimensionServiceKey(request.getGroupKey());

      builder.setOkOperation(() -> {
        builder.getDialogWrapper().close(DialogWrapper.OK_EXIT_CODE);
        onOkRunnable.run();
      });

      boolean useNonModal = request.getHints().contains(DiffTool.HINT_SHOW_NOT_MODAL_DIALOG);
      builder.showModal(!useNonModal);
    }
  }

  @Override
  public boolean canShow(DiffRequest request) {
    if (request instanceof MergeRequest) return false;
    return request.getContents().length == 2 || request.getContents().length == 3;
  }

  @Override
  public DiffViewer createComponent(String title, DiffRequest request, Window window, @NotNull Disposable parentDisposable) {
    return null;
  }
}
