// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.diff.impl.DiffRequestProcessor;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.util.IJSwingUtilities;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.intellij.openapi.vcs.changes.actions.diff.lst.LocalChangeListDiffTool.ALLOW_EXCLUDE_FROM_COMMIT;
import static com.intellij.util.ui.JBUI.emptySize;

public class PreviewDiffSplitterComponent extends OnePixelSplitter {
  @NotNull private final DiffPreviewUpdateProcessor myProcessor;
  private boolean myDetailsOn;

  public PreviewDiffSplitterComponent(@NotNull JComponent firstComponent,
                                      @NotNull DiffPreviewUpdateProcessor processor,
                                      @NotNull String splitterDimensionKey, boolean detailsOn) {
    super(splitterDimensionKey, 0.3f);
    myProcessor = processor;
    setFirstComponent(firstComponent);
    setDetailsOn(detailsOn);
  }

  public void updatePreview(boolean fromModelRefresh) {
    if (isDetailsOn()) {
      myProcessor.refresh(fromModelRefresh);
    }
    else {
      myProcessor.clear();
    }
  }

  void setAllowExcludeFromCommit(boolean value) {
    if (!(myProcessor instanceof DiffRequestProcessor)) return;

    DiffRequestProcessor diffRequestProcessor = (DiffRequestProcessor)myProcessor;

    diffRequestProcessor.putContextUserData(ALLOW_EXCLUDE_FROM_COMMIT, value);
    if (isDetailsOn()) diffRequestProcessor.updateRequest(true);
  }

  private void updateVisibility() {
    setSecondComponent(myDetailsOn ? myProcessor.getComponent() : null);
    JComponent secondComponent = getSecondComponent();
    if (secondComponent != null) {
      IJSwingUtilities.updateComponentTreeUI(secondComponent);
      secondComponent.setMinimumSize(emptySize());
    }
    validate();
    repaint();
  }


  public boolean isDetailsOn() {
    return myDetailsOn;
  }

  public void setDetailsOn(boolean detailsOn) {
    myDetailsOn = detailsOn;
    if (myDetailsOn == (getSecondComponent() == null)) {
      updateVisibility();
    }
    updatePreview(false);
  }
}
