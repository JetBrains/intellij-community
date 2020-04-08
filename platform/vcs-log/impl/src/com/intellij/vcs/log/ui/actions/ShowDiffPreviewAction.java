// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.actions;

import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.impl.CommonUiProperties;
import com.intellij.vcs.log.impl.VcsLogUiProperties;

public class ShowDiffPreviewAction extends BooleanPropertyToggleAction {

  public ShowDiffPreviewAction() {
    super(VcsLogBundle.messagePointer("vcs.log.action.show.diff.preview"),
          VcsLogBundle.messagePointer("vcs.log.action.description.show.diff.preview"), null);
  }

  @Override
  protected VcsLogUiProperties.VcsLogUiProperty<Boolean> getProperty() {
    return CommonUiProperties.SHOW_DIFF_PREVIEW;
  }
}
