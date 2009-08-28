package com.intellij.openapi.diff.impl.mergeTool;

import com.intellij.openapi.diff.*;
import com.intellij.openapi.diff.impl.FrameWrapper;
import com.intellij.openapi.diff.impl.incrementalMerge.ui.MergePanel2;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.Disposable;

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
    Disposable parent = new Disposable() {
      public void dispose() {
      }
    };
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
