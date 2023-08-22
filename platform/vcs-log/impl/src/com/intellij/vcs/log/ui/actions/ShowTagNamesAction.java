// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.actions;

import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.impl.CommonUiProperties;
import com.intellij.vcs.log.impl.VcsLogUiProperties;

public class ShowTagNamesAction extends BooleanPropertyToggleAction {

  public ShowTagNamesAction() {
    super(VcsLogBundle.messagePointer("vcs.log.action.show.tag.names"),
          VcsLogBundle.messagePointer("vcs.log.action.description.show.tag.names"), null);
  }

  @Override
  protected VcsLogUiProperties.VcsLogUiProperty<Boolean> getProperty() {
    return CommonUiProperties.SHOW_TAG_NAMES;
  }
}

