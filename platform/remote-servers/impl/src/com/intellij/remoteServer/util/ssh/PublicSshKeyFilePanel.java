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
package com.intellij.remoteServer.util.ssh;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.remoteServer.util.TooltipUtil;
import com.intellij.ui.HyperlinkLabel;

import javax.swing.*;

/**
 * @author michael.golubev
 */
public class PublicSshKeyFilePanel {

  private TextFieldWithBrowseButton myPublicSshKeyFileField;
  private HyperlinkLabel myPublicSshKeyTooltipHyperlink;
  private JPanel myMainPanel;

  public PublicSshKeyFilePanel() {
    myPublicSshKeyFileField.addBrowseFolderListener("SSH key file", "Select public SSH key file", null,
                                                    FileChooserDescriptorFactory.createSingleLocalFileDescriptor());
  }

  public String getSshKey() {
    return myPublicSshKeyFileField.getText();
  }

  private void createUIComponents() {
    myPublicSshKeyTooltipHyperlink =
      TooltipUtil.createTooltip(
        "Specify the location of your public SSH key file (.pub). You won’t be able to use the cloud unless you register you public key there. However, if you’ve already done that, you don’t have to specify the key now.");
  }

  public JComponent getMainPanel() {
    return myMainPanel;
  }
}
