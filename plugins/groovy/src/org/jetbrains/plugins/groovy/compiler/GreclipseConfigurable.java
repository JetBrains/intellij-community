/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.compiler;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.ui.components.JBCheckBox;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.groovy.GreclipseSettings;

import javax.swing.*;

/**
 * @author peter
 */
public class GreclipseConfigurable implements Configurable {
  private final GreclipseSettings mySettings;
  private TextFieldWithBrowseButton myJarPath;
  private RawCommandLineEditor myCmdLineParams;
  private JBCheckBox myGenerateDebugInfo;
  private JPanel myPanel;

  public GreclipseConfigurable(GreclipseSettings settings) {
    mySettings = settings;

    FileChooserDescriptor descriptor = new FileChooserDescriptor(false, false, true, true, false, false);
    myJarPath.addBrowseFolderListener(null, "Select path to groovy-eclipse-batch-*.jar with version matching your Groovy distribution", null, descriptor);
  }

  @Nls
  @Override
  public String getDisplayName() {
    return null;
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return null;
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    return myPanel;
  }

  @Override
  public boolean isModified() {
    return !Comparing.equal(getExternalizableJarPath(), mySettings.greclipsePath) ||
           !Comparing.equal(myCmdLineParams.getText(), mySettings.cmdLineParams) ||
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

  @Override
  public void disposeUIResources() {
  }
}
