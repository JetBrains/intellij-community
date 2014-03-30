/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.updateSettings.impl;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationInfo;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author pti
 */
class NoUpdatesDialog extends AbstractUpdateDialog {
  protected NoUpdatesDialog(boolean enableLink) {
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

  @NotNull
  @Override
  protected Action[] createActions() {
    return new Action[]{getOKAction()};
  }

  private class NoUpdatesPanel {
    private JPanel myPanel;
    private JLabel myNothingToUpdateLabel;
    private JEditorPane myMessageArea;

    public NoUpdatesPanel() {
      myNothingToUpdateLabel.setText(IdeBundle.message("updates.no.updates.message", ApplicationInfo.getInstance().getVersionName()));
      configureMessageArea(myMessageArea);
    }
  }
}
