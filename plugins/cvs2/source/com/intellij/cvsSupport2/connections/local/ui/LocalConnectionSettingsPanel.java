package com.intellij.cvsSupport2.connections.local.ui;

import com.intellij.cvsSupport2.config.LocalSettings;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;

import javax.swing.*;
import java.awt.*;

/**
 * author: lesya
 */
public class LocalConnectionSettingsPanel {
  private TextFieldWithBrowseButton myPathToCvsClient;
  private JPanel myPanel;
  private JTextField myServerCommand;

  public LocalConnectionSettingsPanel() {
    myPathToCvsClient.addBrowseFolderListener(com.intellij.CvsBundle.message("dialog.title.select.path.to.cvs.client"),
                                              com.intellij.CvsBundle.message("dialog.description.select.path.to.cvs.client"), null,
                                              new FileChooserDescriptor(true, false, false, false, false, false));
  }

  public void updateFrom(LocalSettings local_configuration) {
    myPathToCvsClient.setText(local_configuration.PATH_TO_CVS_CLIENT);
    myServerCommand.setText(local_configuration.SERVER_COMMAND);
  }

  public boolean equalsTo(LocalSettings local_configuration) {
    return
      myPathToCvsClient.getText().equals(local_configuration.PATH_TO_CVS_CLIENT)
      && myServerCommand.getText().equals(local_configuration.SERVER_COMMAND);
  }

  public void saveTo(LocalSettings local_configuration) {
    local_configuration.PATH_TO_CVS_CLIENT = myPathToCvsClient.getText().trim();
    local_configuration.SERVER_COMMAND = myServerCommand.getText().trim();
  }

  public Component getPanel() {
    return myPanel;
  }
}
