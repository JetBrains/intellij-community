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
package com.intellij.openapi.diff.impl.mergeTool;

import com.intellij.openapi.diff.*;
import com.intellij.openapi.ui.FrameWrapper;
import com.intellij.openapi.diff.impl.incrementalMerge.ui.MergePanel2;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;

public class MergeTool implements DiffTool {
  public void show(DiffRequest data) {
    if (data instanceof MergeRequestImpl) {
      showDialog((MergeRequestImpl)data);
      return;
    }
    FrameWrapper frameWrapper = new FrameWrapper(data.getGroupKey());
    DiffViewer mergePanel = createMergeComponent(data, null, frameWrapper);
    frameWrapper.setComponent(mergePanel.getComponent());
    frameWrapper.setPreferredFocusedComponent(mergePanel.getPreferredFocusedComponent());
    frameWrapper.closeOnEsc();
    frameWrapper.setTitle(data.getWindowTitle());
    frameWrapper.setProject(data.getProject());
    frameWrapper.show();
  }

  private static MergePanel2 createMergeComponent(DiffRequest data, DialogBuilder builder, Disposable parent) {
    MergePanel2 mergePanel = new MergePanel2(builder, parent);
    mergePanel.setDiffRequest(data);
    return mergePanel;
  }

  private static void showDialog(MergeRequestImpl data) {
    DialogBuilder builder = new DialogBuilder(data.getProject());
    builder.setDimensionServiceKey(data.getGroupKey());
    builder.setTitle(data.getWindowTitle());
    Disposable parent = Disposer.newDisposable();
    builder.addDisposable(parent);
    MergePanel2 mergePanel = createMergeComponent(data, builder, parent);
    builder.setCenterPanel(mergePanel.getComponent());
    builder.setPreferedFocusComponent(mergePanel.getPreferredFocusedComponent());
    builder.setHelpId(data.getHelpId());
    int result = builder.show();
    MergeRequestImpl lastData = mergePanel.getMergeRequest();
    if (lastData != null) {
      lastData.setResult(result);
    }
  }

  public boolean canShow(DiffRequest data) {
    DiffContent[] contents = data.getContents();
    if (contents.length != 3) return false;
    for (DiffContent content : contents) {
      if (content.getDocument() == null) return false;
    }
    return true;
  }
}
