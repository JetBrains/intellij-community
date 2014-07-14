/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.diff.actions;

import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.ex.DiffPanelEx;
import com.intellij.openapi.diff.impl.processing.HighlightMode;
import org.jetbrains.annotations.NotNull;

public class HighlightModeAction extends DiffPanelComboBoxAction<HighlightMode> {
  private static final HighlightMode[] ourActionOrder = new HighlightMode[]{
    HighlightMode.BY_WORD,
    HighlightMode.BY_LINE,
    HighlightMode.NO_HIGHLIGHTING
  };

  public HighlightModeAction() {
    super(ourActionOrder);
    addAction(HighlightMode.BY_WORD, new HighlightingModeAction(DiffBundle.message("diff.acton.highlight.mode.action.by.word"), HighlightMode.BY_WORD));
    addAction(HighlightMode.BY_LINE, new HighlightingModeAction(DiffBundle.message("diff.acton.highlight.mode.action.by.line"), HighlightMode.BY_LINE));
    addAction(HighlightMode.NO_HIGHLIGHTING, new HighlightingModeAction(DiffBundle.message("diff.acton.highlight.mode.action.no.highlighting"), HighlightMode.NO_HIGHLIGHTING));
  }

  @NotNull
  @Override
  protected String getActionName() {
    return DiffBundle.message("diff.acton.highlight.mode.action.name");
  }

  @NotNull
  @Override
  protected HighlightMode getCurrentOption(@NotNull DiffPanelEx diffPanel) {
    return diffPanel.getHighlightMode();
  }

  private static class HighlightingModeAction extends DiffPanelAction {
    private final HighlightMode myHighlightMode;

    public HighlightingModeAction(String text, HighlightMode highlightMode) {
      super(text);
      myHighlightMode = highlightMode;
    }

    @Override
    protected void perform(@NotNull DiffPanelEx diffPanel) {
      diffPanel.setHighlightMode(myHighlightMode);
    }
  }
}
