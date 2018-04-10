// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileChooser.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.fileChooser.ex.FileChooserDialogImpl;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.DialogWrapper;

public class  TogglePathShowingAction extends AnAction implements DumbAware {
  public TogglePathShowingAction() {
    setEnabledInModalContext(true);
  }

  public void update(final AnActionEvent e) {
    e.getPresentation().setText(IdeBundle.message("file.chooser.hide.path.tooltip.text"));
    DialogWrapper dialog = DialogWrapper.findInstance(e.getData(PlatformDataKeys.CONTEXT_COMPONENT));
    e.getPresentation().setEnabled(dialog instanceof FileChooserDialogImpl);
  }

  public void actionPerformed(final AnActionEvent e) {
    FileChooserDialogImpl dialog = (FileChooserDialogImpl)DialogWrapper.findInstance(e.getData(PlatformDataKeys.CONTEXT_COMPONENT));
    if (dialog != null) {
      dialog.toggleShowTextField();
    }
  }
}
