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
package org.jetbrains.plugins.javaFX.packaging;

import com.intellij.ide.util.BrowseFilesListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.JavaFXBundle;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class JavaFxEditCertificatesDialog extends DialogWrapper {

  Panel myPanel;

  protected JavaFxEditCertificatesDialog(JComponent parent, JavaFxArtifactProperties properties, Project project) {
    super(parent, true);
    setTitle(JavaFXBundle.message("javafx.certificates.dialog.choose.certificate.title"));
    init();
    final ActionListener actionListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        UIUtil.setEnabled(myPanel.myKeysPanel, !myPanel.mySelfSignedRadioButton.isSelected(), true);
      }
    };
    myPanel.mySelfSignedRadioButton.addActionListener(actionListener);
    myPanel.mySignedByKeyRadioButton.addActionListener(actionListener);
    final boolean selfSigning = properties.isSelfSigning();
    UIUtil.setEnabled(myPanel.myKeysPanel, !selfSigning, true);
    myPanel.mySelfSignedRadioButton.setSelected(selfSigning);
    myPanel.mySignedByKeyRadioButton.setSelected(!selfSigning);

    myPanel.myAliasTF.setText(properties.getAlias());
    myPanel.myKeystore.setText(properties.getKeystore());
    final String keypass = properties.getKeypass();
    myPanel.myKeypassTF.setText(keypass != null ? new String(Base64.getDecoder().decode(keypass), StandardCharsets.UTF_8) : "");
    final String storepass = properties.getStorepass();
    myPanel.myStorePassTF.setText(storepass != null ? new String(Base64.getDecoder().decode(storepass), StandardCharsets.UTF_8) : "");
    myPanel.myKeystore.addBrowseFolderListener(JavaFXBundle.message("javafx.certificates.dialog.choose.certificate.title"), JavaFXBundle.message("javafx.certificates.dialog.select.file.with.generated.keys"), project, BrowseFilesListener.SINGLE_FILE_DESCRIPTOR);
  }

  @Override
  protected void doOKAction() {
    if (myPanel.mySignedByKeyRadioButton.isSelected()) {
      if (StringUtil.isEmptyOrSpaces(myPanel.myAliasTF.getText())) {
        Messages.showErrorDialog(myPanel.myWholePanel, JavaFXBundle.message("javafx.certificates.dialog.alias.should.be.non.empty.error"));
        return;
      }
      final String keystore = myPanel.myKeystore.getText();
      if (StringUtil.isEmptyOrSpaces(keystore)) {
        Messages.showErrorDialog(myPanel.myWholePanel, JavaFXBundle.message("javafx.certificates.dialog.path.to.keystore.file.error"));
        return;
      }
      if (!new File(keystore).isFile()) {
        Messages.showErrorDialog(myPanel.myWholePanel, JavaFXBundle.message("javafx.certificates.dialog.keystore.file.should.exist.error"));
        return;
      }
      if (StringUtil.isEmptyOrSpaces(String.valueOf(myPanel.myKeypassTF.getPassword())) || 
          StringUtil.isEmptyOrSpaces(String.valueOf(myPanel.myStorePassTF.getPassword()))) {
        Messages.showErrorDialog(myPanel.myWholePanel, JavaFXBundle.message("javafx.certificates.dialog.passwords.should.be.set.error"));
        return;
      }
    }
    super.doOKAction();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    myPanel = new Panel();
    return myPanel.myWholePanel;
  }

  protected static class Panel {
    JRadioButton mySelfSignedRadioButton;
    JRadioButton mySignedByKeyRadioButton;
    JPasswordField myStorePassTF;
    JPasswordField myKeypassTF;
    JTextField myAliasTF;
    TextFieldWithBrowseButton myKeystore;
    JPanel myWholePanel;
    JPanel myKeysPanel;
  }
}
