// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationNamesInfo;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author pti
 */
class NoUpdatesDialog extends AbstractUpdateDialog {
  NoUpdatesDialog(boolean enableLink) {
    super(enableLink);
    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    return new NoUpdatesPanel().myPanel;
  }

  @Override
  protected String getOkButtonText() {
    return CommonBundle.getCloseButtonText();
  }

  @Override
  protected Action @NotNull [] createActions() {
    return new Action[]{getOKAction()};
  }

  private class NoUpdatesPanel {
    private JPanel myPanel;
    private JLabel myNothingToUpdateLabel;
    private JEditorPane myMessageArea;

    NoUpdatesPanel() {
      myNothingToUpdateLabel.setText(getNoUpdatesText());
      configureMessageArea(myMessageArea);
    }
  }

  static @Nls(capitalization = Nls.Capitalization.Sentence) String getNoUpdatesText() {
    String app = ApplicationNamesInfo.getInstance().getFullProductName();
    ExternalUpdateManager manager = ExternalUpdateManager.ACTUAL;
    if (manager == null) {
      return IdeBundle.message("updates.no.updates.message", app);
    }
    else if (manager == ExternalUpdateManager.TOOLBOX) {
      return IdeBundle.message("updates.no.updates.toolbox.message", app);
    }
    else if (manager == ExternalUpdateManager.SNAP) {
      return IdeBundle.message("updates.no.updates.snap.message", app);
    }
    else {
      return IdeBundle.message("updates.no.updates.unknown.message", app, manager.toolName);
    }
  }
}
