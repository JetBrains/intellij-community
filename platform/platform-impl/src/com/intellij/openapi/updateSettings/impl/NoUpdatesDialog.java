package com.intellij.openapi.updateSettings.impl;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.ui.Messages;

import javax.swing.*;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: pti
 * Date: Jun 24, 2005
 * Time: 10:44:40 PM
 * To change this template use File | Settings | File Templates.
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
    return myUploadedPlugins == null ? CommonBundle.getOkButtonText() : IdeBundle.message("update.plugins.update.action");
  }

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
    if (hasSmthToUpdate &&
        Messages.showYesNoDialog(IdeBundle.message("message.idea.restart.required", ApplicationNamesInfo.getInstance().getProductName()),
                                 IdeBundle.message("title.plugins"), Messages.getQuestionIcon()) != 0) {
      hasSmthToUpdate = false;
    }
    return hasSmthToUpdate;
  }

  private class NoUpdatesPanel {
    private JPanel myPanel;
    private JPanel myPluginsPanel;
    private JEditorPane myEditorPane;
    private JPanel myWholePluginsPanel;
    private JLabel myNothingFoundToUpdateLabel;

    public NoUpdatesPanel() {
      initPluginsPanel(myPanel, myPluginsPanel, myWholePluginsPanel, myEditorPane);
      myNothingFoundToUpdateLabel.setVisible(myUploadedPlugins == null);
    }
  }
}
