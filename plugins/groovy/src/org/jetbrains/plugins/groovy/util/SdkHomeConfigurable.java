// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.util;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.NlsContexts.ConfigurableName;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;

import javax.swing.*;
import java.awt.*;

/**
 * @author peter
 */
public abstract class SdkHomeConfigurable implements SearchableConfigurable {
  private JPanel myPanel;
  private TextFieldWithBrowseButton myPathField;
  protected final Project myProject;
  protected final @NlsSafe String myFrameworkName;

  public SdkHomeConfigurable(Project project, final @NlsSafe String frameworkName) {
    myProject = project;
    myFrameworkName = frameworkName;
  }

  @Override
  public @ConfigurableName String getDisplayName() {
    return myFrameworkName;
  }

  @Override
  public JComponent createComponent() {
    myPanel = new JPanel(new BorderLayout(10, 5));
    final JPanel contentPanel = new JPanel(new BorderLayout(4, 0));
    myPanel.add(contentPanel, BorderLayout.NORTH);
    contentPanel.add(new JLabel(GroovyBundle.message("framework.0.home.label", myFrameworkName)), BorderLayout.WEST);
    myPathField = new TextFieldWithBrowseButton();
    contentPanel.add(myPathField);
    myPathField
      .addBrowseFolderListener(GroovyBundle.message("select.framework.0.home.title", myFrameworkName), "", myProject, new FileChooserDescriptor(false, true, false, false, false, false) {
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
    return !(myPathField.getText().equals(StringUtil.notNullize(getStateText())));
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

  private @NlsSafe @Nullable String getStateText() {
    final SdkHomeBean state = getFrameworkSettings().getState();
    if (state == null) {
      return null;
    }
    String sdkHome = state.getSdkHome();
    if (sdkHome == null) {
      return null;
    }
    return FileUtil.toSystemDependentName(sdkHome);
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

  @Override
  public abstract @NonNls @NotNull String getHelpTopic();
}
