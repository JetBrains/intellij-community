// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.diff.impl.DiffRequestProcessor;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.util.IJSwingUtilities;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.intellij.openapi.vcs.changes.actions.diff.lst.LocalChangeListDiffTool.ALLOW_EXCLUDE_FROM_COMMIT;
import static com.intellij.util.ui.JBUI.emptySize;

public class PreviewDiffSplitterComponent extends OnePixelSplitter implements ChangesViewPreview {
  @NotNull private final DiffPreviewUpdateProcessor myProcessor;
  private boolean myDiffPreviewVisible;

  public PreviewDiffSplitterComponent(@NotNull DiffPreviewUpdateProcessor processor, @NotNull String proportionKey) {
    super(proportionKey, 0.3f);
    myProcessor = processor;
  }

  @Override
  public void updatePreview(boolean fromModelRefresh) {
    if (isDiffPreviewVisible()) {
      myProcessor.refresh(fromModelRefresh);
    }
    else {
      myProcessor.clear();
    }
  }

  public boolean isDiffPreviewVisible() {
    return myDiffPreviewVisible;
  }

  @Override
  public void setDiffPreviewVisible(boolean isVisible) {
    myDiffPreviewVisible = isVisible;
    if (myDiffPreviewVisible == (getSecondComponent() == null)) {
      updateVisibility();
    }
    updatePreview(false);
  }

  @Override
  public void setAllowExcludeFromCommit(boolean value) {
    if (!(myProcessor instanceof DiffRequestProcessor)) return;

    DiffRequestProcessor diffRequestProcessor = (DiffRequestProcessor)myProcessor;

    diffRequestProcessor.putContextUserData(ALLOW_EXCLUDE_FROM_COMMIT, value);
    if (isDiffPreviewVisible()) diffRequestProcessor.updateRequest(true);
  }

  private void updateVisibility() {
    setSecondComponent(myDiffPreviewVisible ? myProcessor.getComponent() : null);
    JComponent secondComponent = getSecondComponent();
    if (secondComponent != null) {
      IJSwingUtilities.updateComponentTreeUI(secondComponent);
      secondComponent.setMinimumSize(emptySize());
    }
    validate();
    repaint();
  }
}
