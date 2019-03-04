// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.SideBorder;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.intellij.util.ui.JBUI.emptySize;

public class PreviewDiffSplitterComponent extends JBSplitter {
  @NotNull private final JComponent myFirstComponent;
  @NotNull private final DiffPreviewUpdateProcessor myProcessor;
  private boolean myDetailsOn;

  public PreviewDiffSplitterComponent(@NotNull JComponent firstComponent,
                                      @NotNull DiffPreviewUpdateProcessor processor,
                                      @NotNull String splitterDimensionKey, boolean detailsOn) {
    super(splitterDimensionKey, 0.5f);
    myFirstComponent = firstComponent;
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

  private void updateVisibility() {
    setSecondComponent(myDetailsOn ? myProcessor.getComponent() : null);
    myFirstComponent.setBorder(myDetailsOn ? IdeBorderFactory.createBorder(SideBorder.RIGHT|SideBorder.LEFT) :
                                             IdeBorderFactory.createBorder(SideBorder.LEFT));
    JComponent secondComponent = getSecondComponent();
    if (secondComponent != null) {
      secondComponent.setMinimumSize(emptySize());
    }
    revalidate();
    repaint();
  }


  public boolean isDetailsOn() {
    return myDetailsOn;
  }

  public void setDetailsOn(boolean detailsOn) {
    myDetailsOn = detailsOn;
    updatePreview(false);
    if (myDetailsOn == (getSecondComponent() == null)) {
      updateVisibility();
    }
  }
}
