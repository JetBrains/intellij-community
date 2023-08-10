// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.compiler;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.ui.components.JBCheckBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.groovy.GreclipseSettings;
import org.jetbrains.plugins.groovy.GroovyBundle;

import javax.swing.*;
import java.util.Objects;

public class GreclipseConfigurable implements Configurable {
  private final GreclipseSettings mySettings;
  private TextFieldWithBrowseButton myJarPath;
  private RawCommandLineEditor myCmdLineParams;
  private JBCheckBox myGenerateDebugInfo;
  private JPanel myPanel;

  public GreclipseConfigurable(GreclipseSettings settings) {
    mySettings = settings;

    FileChooserDescriptor descriptor = new FileChooserDescriptor(false, false, true, true, false, false);
    myJarPath.addBrowseFolderListener(null, GroovyBundle.message("configurable.greclipse.path.chooser.description"), null, descriptor);
  }

  @Override
  public String getDisplayName() {
    return null;
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    return myPanel;
  }

  @Override
  public boolean isModified() {
    return !Objects.equals(getExternalizableJarPath(), mySettings.greclipsePath) ||
           !Objects.equals(myCmdLineParams.getText(), mySettings.cmdLineParams) ||
           !Comparing.equal(myGenerateDebugInfo.isSelected(), mySettings.debugInfo);
  }

  @Override
  public void apply() throws ConfigurationException {
    mySettings.greclipsePath = getExternalizableJarPath();
    mySettings.cmdLineParams = myCmdLineParams.getText();
    mySettings.debugInfo = myGenerateDebugInfo.isSelected();
  }

  @NotNull
  private String getExternalizableJarPath() {
    return FileUtil.toSystemIndependentName(myJarPath.getText());
  }

  @Override
  public void reset() {
    myJarPath.setText(FileUtil.toSystemDependentName(mySettings.greclipsePath));
    myCmdLineParams.setText(mySettings.cmdLineParams);
    myGenerateDebugInfo.setSelected(mySettings.debugInfo);
  }
}
