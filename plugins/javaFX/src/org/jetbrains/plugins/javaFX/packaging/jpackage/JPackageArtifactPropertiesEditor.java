// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.javaFX.packaging.jpackage;

import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.packaging.ui.ArtifactPropertiesEditor;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;

/**
 * @author Bas Leijdekkers
 */
public class JPackageArtifactPropertiesEditor extends ArtifactPropertiesEditor {

  private final JPackageArtifactProperties myProperties;

  private TextFieldWithBrowseButton myMainClass;
  private JTextField myVersion;
  private JTextField myCopyright;
  private JTextField myVendor;
  private JBTextArea myDescription;
  private JCheckBox myVerbose;

  public JPackageArtifactPropertiesEditor(JPackageArtifactProperties properties) {
    myProperties = properties;
  }

  @Nls
  @Override
  public String getTabName() {
    return "Packaging";
  }

  @Override
  public @Nullable JComponent createComponent() {
    myMainClass = new TextFieldWithBrowseButton();
    myMainClass.setText(myProperties.mainClass);
    myVersion = new JTextField(myProperties.version);
    myCopyright = new JTextField(myProperties.copyright);
    myVendor = new JTextField(myProperties.vendor);
    myDescription = new JBTextArea(myProperties.description, 5, 50);
    myDescription.setMinimumSize(new Dimension(100, 50));
    myVerbose = new JCheckBox("Verbose", myProperties.verbose);

    final FormBuilder builder = new FormBuilder();
    builder.addLabeledComponent("Main class", myMainClass);
    builder.addLabeledComponent("Version", myVersion);
    builder.addLabeledComponent("Copyright", myCopyright);
    builder.addLabeledComponent("Vendor", myVendor);
    builder.addLabeledComponent("Description", myDescription);
    builder.addComponent(myVerbose);
    return builder.getPanel();
  }

  @Override
  public boolean isModified() {
    if (isModified(myProperties.mainClass, myMainClass.getTextField())) return true;
    if (isModified(myProperties.version, myVersion)) return true;
    if (isModified(myProperties.copyright, myCopyright)) return true;
    if (isModified(myProperties.vendor, myVendor)) return true;
    if (isModified(myProperties.description, myDescription)) return true;
    if (myProperties.verbose != myVerbose.isSelected()) return true;
    return false;
  }

  private static boolean isModified(final String title, JTextComponent tf) {
    return !Comparing.strEqual(title, tf.getText().trim());
  }

  @Override
  public void apply() {
    myProperties.mainClass = myMainClass.getText();
    myProperties.version = myVersion.getText();
    myProperties.copyright = myCopyright.getText();
    myProperties.vendor = myVendor.getText();
    myProperties.description = myDescription.getText();
    myProperties.verbose = myVerbose.isSelected();
  }
}
