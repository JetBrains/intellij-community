// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.ex;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;

public abstract class TextFieldAction extends LinkLabel implements LinkListener {
  public TextFieldAction() {
    super("", null);
    setListener(this, null);
    update();
  }

  @Override
  protected void onSetActive(final boolean active) {
    final String tooltip = KeymapUtil
      .createTooltipText(ActionsBundle.message("action.FileChooser.TogglePathShowing.text"),
                         ActionManager.getInstance().getAction("FileChooser.TogglePathBar"));
    setToolTipText(tooltip);
  }

  @Override
  protected String getStatusBarText() {
    return ActionsBundle.message("action.FileChooser.TogglePathShowing.text");
  }

  public void update() {
    setVisible(true);
    setText(PropertiesComponent.getInstance().getBoolean(FileChooserDialogImpl.FILE_CHOOSER_SHOW_PATH_PROPERTY, true) ? IdeBundle.message("file.chooser.hide.path") : IdeBundle.message("file.chooser.show.path"));
  }



}
