/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.ide.plugins.PluginManagerConfigurable;
import com.intellij.openapi.application.ApplicationInfo;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

/**
 * @author pti
 */
class NoUpdatesDialog extends AbstractUpdateDialog {
  protected NoUpdatesDialog(final boolean canBeParent, final List<PluginDownloader> updatePlugins, boolean enableLink) {
    super(canBeParent, enableLink, updatePlugins);
    setTitle(IdeBundle.message("updates.info.dialog.title"));
    init();
  }

  protected JComponent createCenterPanel() {
    return new NoUpdatesPanel().myPanel;
  }

  @Override
  protected String getOkButtonText() {
    return myUploadedPlugins == null ? CommonBundle.getCloseButtonText() : IdeBundle.message("update.plugins.update.action");
  }

  @NotNull
  protected Action[] createActions() {
    final Action cancelAction = getCancelAction();
    if (myUploadedPlugins != null) {
      return new Action[] {getOKAction(), cancelAction};
    }
    return new Action[] {getOKAction()};
  }

  @Override
  protected boolean doDownloadAndPrepare() {
    boolean hasSmthToUpdate = super.doDownloadAndPrepare();
    if (hasSmthToUpdate && isShowConfirmation() && PluginManagerConfigurable.showRestartIDEADialog() != 0) {
      hasSmthToUpdate = false;
    }
    return hasSmthToUpdate;
  }
  
  private class NoUpdatesPanel {
    private JPanel myPanel;
    private JPanel myPluginsPanel;
    private JEditorPane myEditorPane;
    private JLabel myNothingFoundToUpdateLabel;
    private JLabel myPluginsToUpdateLabel;

    public NoUpdatesPanel() {
      initPluginsPanel(myPanel, myPluginsPanel, myEditorPane);
      myPluginsToUpdateLabel.setVisible(myUploadedPlugins != null);
      myNothingFoundToUpdateLabel.setVisible(myUploadedPlugins == null);
      myNothingFoundToUpdateLabel.setText("You already have the latest version of " +
                                          ApplicationInfo.getInstance().getVersionName()+ " installed.");
    }
  }
}
