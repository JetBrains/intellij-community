// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.packaging;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
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

public final class JavaFxEditCertificatesDialog extends DialogWrapper {

  Panel myPanel;

  JavaFxEditCertificatesDialog(JComponent parent, JavaFxArtifactProperties properties, Project project) {
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
    myPanel.myKeystore.addBrowseFolderListener(project, FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
      .withTitle(JavaFXBundle.message("javafx.certificates.dialog.choose.certificate.title"))
      .withDescription(JavaFXBundle.message("javafx.certificates.dialog.select.file.with.generated.keys")));
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

  @Override
  protected @Nullable JComponent createCenterPanel() {
    myPanel = new Panel();
    return myPanel.myWholePanel;
  }

  protected static final class Panel {
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
