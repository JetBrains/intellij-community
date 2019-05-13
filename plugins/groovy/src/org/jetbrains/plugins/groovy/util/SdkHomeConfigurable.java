// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.jetbrains.annotations.NotNull;

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

  @Override
  @Nls
  public String getDisplayName() {
    return myFrameworkName;
  }

  @Override
  public JComponent createComponent() {
    myPanel = new JPanel(new BorderLayout(10, 5));
    final JPanel contentPanel = new JPanel(new BorderLayout(4, 0));
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

  @Override
  public boolean isModified() {
    return !myPathField.getText().equals(getStateText());
  }

  @Override
  public void apply() throws ConfigurationException {
    final SdkHomeBean state = new SdkHomeBean();
    state.setSdkHome(FileUtil.toSystemIndependentName(myPathField.getText()));
    getFrameworkSettings().loadState(state);
  }

  protected abstract SdkHomeSettings getFrameworkSettings();

  @Override
  public void reset() {
    myPathField.setText(getStateText());
  }

  private String getStateText() {
    final SdkHomeBean state = getFrameworkSettings().getState();
    final String stateText = state == null ? "" : state.getSdkHome();
    return FileUtil.toSystemDependentName(StringUtil.notNullize(stateText));
  }

  @Override
  public void disposeUIResources() {
    myPathField = null;
    myPanel = null;
  }

  @Override
  @NotNull
  public String getId() {
    return getHelpTopic();
  }
}
