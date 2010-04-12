/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.util;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;

import javax.swing.*;
import java.awt.*;

/**
 * @author peter
 */
public abstract class SdkHomeConfigurable implements SearchableConfigurable {
  private JPanel myPanel;
  private TextFieldWithBrowseButton myPathField;
  protected final Project myProject;
  protected final String myFrameworkName;

  public SdkHomeConfigurable(Project project, final String frameworkName) {
    myProject = project;
    myFrameworkName = frameworkName;
  }

  @Nls
  public String getDisplayName() {
    return myFrameworkName;
  }

  public JComponent createComponent() {
    myPanel = new JPanel(new BorderLayout());
    final JPanel contentPanel = new JPanel(new BorderLayout());
    myPanel.add(contentPanel, BorderLayout.NORTH);
    contentPanel.add(new JLabel(myFrameworkName + " home:"), BorderLayout.WEST);
    myPathField = new TextFieldWithBrowseButton();
    contentPanel.add(myPathField);
    myPathField
      .addBrowseFolderListener("Select " + myFrameworkName + " home", "", myProject, new FileChooserDescriptor(false, true, false, false, false, false) {
        @Override
        public boolean isFileSelectable(VirtualFile file) {
          return isSdkHome(file);
        }
      });
    return myPanel;
  }

  protected abstract boolean isSdkHome(VirtualFile file);

  public boolean isModified() {
    return !myPathField.getText().equals(getStateText());
  }

  public void apply() throws ConfigurationException {
    final SdkHomeBean state = new SdkHomeBean();
    state.SDK_HOME = FileUtil.toSystemIndependentName(myPathField.getText());
    getFrameworkSettings().loadState(state);
  }

  protected abstract SdkHomeSettings getFrameworkSettings();

  public void reset() {
    myPathField.setText(getStateText());
  }

  private String getStateText() {
    final SdkHomeBean state = getFrameworkSettings().getState();
    final String stateText = state == null ? "" : state.SDK_HOME;
    return FileUtil.toSystemDependentName(StringUtil.notNullize(stateText));
  }

  public void disposeUIResources() {
    myPathField = null;
    myPanel = null;
  }

  public static class SdkHomeBean {
    public String SDK_HOME;
  }

  public Runnable enableSearch(String option) {
    return null;
  }

  public String getId() {
    return getHelpTopic();
  }
}
