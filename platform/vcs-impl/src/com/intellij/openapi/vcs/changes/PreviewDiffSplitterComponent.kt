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
  @NotNull private final DiffPreviewUpdateProcessor myUpdatePreviewProcessor;
  private boolean myPreviewVisible;

  public PreviewDiffSplitterComponent(@NotNull DiffPreviewUpdateProcessor updatePreviewProcessor, @NotNull String proportionKey) {
    super(proportionKey, 0.3f);
    myUpdatePreviewProcessor = updatePreviewProcessor;
  }

  @Override
  public void updatePreview(boolean fromModelRefresh) {
    if (isPreviewVisible()) {
      myUpdatePreviewProcessor.refresh(fromModelRefresh);
    }
    else {
      myUpdatePreviewProcessor.clear();
    }
  }

  public boolean isPreviewVisible() {
    return myPreviewVisible;
  }

  @Override
  public void setPreviewVisible(boolean isPreviewVisible) {
    myPreviewVisible = isPreviewVisible;
    if (myPreviewVisible == (getSecondComponent() == null)) {
      updateVisibility();
    }
    updatePreview(false);
  }

  @Override
  public void setAllowExcludeFromCommit(boolean value) {
    if (!(myUpdatePreviewProcessor instanceof DiffRequestProcessor)) return;

    DiffRequestProcessor diffProcessor = (DiffRequestProcessor)myUpdatePreviewProcessor;

    diffProcessor.putContextUserData(ALLOW_EXCLUDE_FROM_COMMIT, value);
    if (isPreviewVisible()) diffProcessor.updateRequest(true);
  }

  private void updateVisibility() {
    setSecondComponent(myPreviewVisible ? myUpdatePreviewProcessor.getComponent() : null);
    JComponent secondComponent = getSecondComponent();
    if (secondComponent != null) {
      IJSwingUtilities.updateComponentTreeUI(secondComponent);
      secondComponent.setMinimumSize(emptySize());
    }
    validate();
    repaint();
  }
}
