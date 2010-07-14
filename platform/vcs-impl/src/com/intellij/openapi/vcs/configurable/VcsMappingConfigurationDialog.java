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

package com.intellij.openapi.vcs.configurable;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vcs.impl.VcsDescriptor;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.Map;

/**
 * @author yole
 */
public class VcsMappingConfigurationDialog extends DialogWrapper {
  private final Project myProject;
  private JComboBox myVCSComboBox;
  private TextFieldWithBrowseButton myDirectoryTextField;
  private JPanel myPanel;
  private JPanel myVcsConfigurablePlaceholder;
  private UnnamedConfigurable myVcsConfigurable;
  private VcsDirectoryMapping myMappingCopy;
  private JComponent myVcsConfigurableComponent;
  private ProjectLevelVcsManager myVcsManager;
  private final Map<String, VcsDescriptor> myVcses;

  public VcsMappingConfigurationDialog(final Project project, final String title) {
    super(project, false);
    myProject = project;
    myVcsManager = ProjectLevelVcsManager.getInstance(myProject);
    final VcsDescriptor[] vcsDescriptors = myVcsManager.getAllVcss();
    myVcses = new HashMap<String, VcsDescriptor>();
    for (VcsDescriptor vcsDescriptor : vcsDescriptors) {
      myVcses.put(vcsDescriptor.getName(), vcsDescriptor);
    }
    myVCSComboBox.setModel(VcsDirectoryConfigurationPanel.buildVcsWrappersModel(project));
    myDirectoryTextField.addActionListener(new MyBrowseFolderListener("Select Directory", "Select directory to map to a VCS",
                                                                      myDirectoryTextField, project,
                                                                      new FileChooserDescriptor(false, true, false, false, false, false)));
    myMappingCopy = new VcsDirectoryMapping("", "");
    setTitle(title);
    init();
    myVCSComboBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateVcsConfigurable();
      }
    });
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }

  public void setMapping(VcsDirectoryMapping mapping) {
    myMappingCopy = new VcsDirectoryMapping(mapping.getDirectory(), mapping.getVcs(), mapping.getRootSettings());
    myVCSComboBox.setSelectedItem(myVcses.get(mapping.getVcs()));
    myDirectoryTextField.setText(FileUtil.toSystemDependentName(mapping.getDirectory()));
    updateVcsConfigurable();
  }

  public void saveToMapping(VcsDirectoryMapping mapping) {
    VcsDescriptor wrapper = (VcsDescriptor) myVCSComboBox.getSelectedItem();
    mapping.setVcs((wrapper == null) || wrapper.isNone() ? "" : wrapper.getName());
    mapping.setDirectory(FileUtil.toSystemIndependentName(myDirectoryTextField.getText()));
    mapping.setRootSettings(myMappingCopy.getRootSettings());
  }

  private void updateVcsConfigurable() {
    if (myVcsConfigurable != null) {
      myVcsConfigurablePlaceholder.remove(myVcsConfigurableComponent);
      myVcsConfigurable.disposeUIResources();
      myVcsConfigurable = null;
    }
    VcsDescriptor wrapper = (VcsDescriptor) myVCSComboBox.getSelectedItem();
    if (wrapper != null && (! wrapper.isNone())) {
      final AbstractVcs vcs = myVcsManager.findVcsByName(wrapper.getName());
      if (vcs != null) {
        UnnamedConfigurable configurable = vcs.getRootConfigurable(myMappingCopy);
        if (configurable != null) {
          myVcsConfigurable = configurable;
          myVcsConfigurableComponent = myVcsConfigurable.createComponent();
          myVcsConfigurablePlaceholder.add(myVcsConfigurableComponent, BorderLayout.CENTER);
        }
      }
    }
    pack();
  }

  @Override
  protected Action[] createLeftSideActions() {
    return new Action[] { new ConfigureVcsAction() };
  }

  protected void doOKAction() {
    if (myVcsConfigurable != null) {
      try {
        myVcsConfigurable.apply();
      }
      catch(ConfigurationException ex) {
        Messages.showErrorDialog(myPanel, "Invalid VCS options: " + ex.getMessage());
      }
    }
    super.doOKAction();
  }

  private class MyBrowseFolderListener extends ComponentWithBrowseButton.BrowseFolderActionListener<JTextField> {

    public MyBrowseFolderListener(String title, String description, TextFieldWithBrowseButton textField, Project project,
                                  FileChooserDescriptor fileChooserDescriptor) {
      super(title, description, textField, project, fileChooserDescriptor, TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT);
    }
    
    @Override
    protected VirtualFile getInitialFile() {
      // suggest project base dir only if nothing is typed in the component.
      String text = getComponentText();
      if(text.length() == 0) {
        VirtualFile file = myProject.getBaseDir();
        if(file != null) {
          return file;
        }
      }
      return super.getInitialFile();
    }

    @Override
    protected void onFileChoosen(final VirtualFile chosenFile) {
      String oldText = myDirectoryTextField.getText();
      super.onFileChoosen(chosenFile);
      final VcsDescriptor wrapper = (VcsDescriptor) myVCSComboBox.getSelectedItem();
      if (oldText.length() == 0 && (wrapper == null || wrapper.isNone())) {
        VcsDescriptor probableVcs = null;
        for(VcsDescriptor vcs: myVcses.values()) {
          if (vcs.probablyUnderVcs(chosenFile)) {
            if (probableVcs != null) {
              probableVcs = null;
              break;
            }
            probableVcs = vcs;
          }
        }
        if (probableVcs != null) {
          // todo none
          myVCSComboBox.setSelectedItem(probableVcs);
        }
      }
    }
  }

  private class ConfigureVcsAction extends AbstractAction {
    public ConfigureVcsAction() {
      super(VcsBundle.message("button.configure"));
    }

    public void actionPerformed(ActionEvent e) {
      VcsDescriptor wrapper = (VcsDescriptor) myVCSComboBox.getSelectedItem();
      new VcsConfigurationsDialog(myProject, null, wrapper).show();
    }
  }
}
