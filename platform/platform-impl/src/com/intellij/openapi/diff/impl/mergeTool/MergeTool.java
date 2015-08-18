/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.mergeTool;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.DiffRequest;
import com.intellij.openapi.diff.DiffTool;
import com.intellij.openapi.diff.DiffViewer;
import com.intellij.openapi.diff.impl.incrementalMerge.ui.MergePanel2;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.FrameWrapper;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class MergeTool implements DiffTool {
  public static final Logger LOG = Logger.getInstance(MergeTool.class);

  public void show(DiffRequest data) {
    if (data instanceof MergeRequestImpl) {
      showDialog((MergeRequestImpl)data);
      return;
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug(new Throwable("MergeTool - frame"));
    }
    FrameWrapper frameWrapper = new FrameWrapper(data.getProject(), data.getGroupKey());
    DiffViewer mergePanel = createMergeComponent(data, null, frameWrapper);
    frameWrapper.setComponent(mergePanel.getComponent());
    frameWrapper.setPreferredFocusedComponent(mergePanel.getPreferredFocusedComponent());
    frameWrapper.closeOnEsc();
    frameWrapper.setTitle(data.getWindowTitle());
    frameWrapper.setProject(data.getProject());
    frameWrapper.show();
  }

  private static MergePanel2 createMergeComponent(DiffRequest data, DialogBuilder builder, @NotNull Disposable parent) {
    MergePanel2 mergePanel = new MergePanel2(builder, parent);
    mergePanel.setDiffRequest(data);
    return mergePanel;
  }

  private static void showDialog(MergeRequestImpl data) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("MergeTool - dialog");
    }
    DialogBuilder builder = new DialogBuilder(data.getProject());
    builder.setDimensionServiceKey(data.getGroupKey());
    builder.setTitle(data.getWindowTitle());
    Disposable parent = Disposer.newDisposable();
    builder.addDisposable(parent);
    MergePanel2 mergePanel = createMergeComponent(data, builder, parent);
    builder.setCenterPanel(mergePanel.getComponent());
    builder.setPreferredFocusComponent(mergePanel.getPreferredFocusedComponent());
    builder.setHelpId(data.getHelpId());
    int result = builder.show();
    MergeRequestImpl lastData = mergePanel.getMergeRequest();
    if (lastData != null) {
      lastData.setResult(result);
    }
  }

  public boolean canShow(DiffRequest data) {
    return canShowRequest(data);
  }

  public static boolean canShowRequest(DiffRequest data) {
    DiffContent[] contents = data.getContents();
    if (contents.length != 3) return false;
    for (DiffContent content : contents) {
      if (content.getDocument() == null) return false;
    }
    return true;
  }

  @Override
  public DiffViewer createComponent(String title, DiffRequest request, Window window, @NotNull Disposable parentDisposable) {
    if (LOG.isDebugEnabled()) {
      LOG.debug(new Throwable("MergeTool - component: " + request.getContents()[1].getDocument().isWritable()));
    }
    return createMergeComponent(request, null, parentDisposable);
  }
}
