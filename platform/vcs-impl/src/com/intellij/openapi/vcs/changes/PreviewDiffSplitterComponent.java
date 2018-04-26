/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes;

import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.SideBorder;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

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
    setHonorComponentsMinimumSize(false);
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
    myFirstComponent.setBorder(myDetailsOn ? IdeBorderFactory.createBorder(SideBorder.RIGHT) : null);
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
