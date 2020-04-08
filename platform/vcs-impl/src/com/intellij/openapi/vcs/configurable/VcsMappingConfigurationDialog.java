// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.configurable;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vcs.impl.DefaultVcsRootPolicy;
import com.intellij.openapi.vcs.impl.VcsDescriptor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBLabel;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

import static com.google.common.collect.Maps.uniqueIndex;
import static com.intellij.openapi.fileChooser.FileChooserDescriptorFactory.createSingleFolderDescriptor;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static com.intellij.openapi.util.io.FileUtil.toSystemIndependentName;
import static com.intellij.openapi.vcs.configurable.VcsDirectoryConfigurationPanel.buildVcsWrappersModel;
import static com.intellij.util.containers.UtilKt.getIfSingle;
import static com.intellij.xml.util.XmlStringUtil.wrapInHtml;

public class VcsMappingConfigurationDialog extends DialogWrapper {
  @NotNull private final Project myProject;
  private ComboBox<VcsDescriptor> myVCSComboBox;
  private TextFieldWithBrowseButton myDirectoryTextField;
  private JPanel myPanel;
  private JPanel myVcsConfigurablePlaceholder;
  private JRadioButton myProjectRadioButton;
  private JRadioButton myDirectoryRadioButton;
  private JBLabel myProjectButtonComment;
  private UnnamedConfigurable myVcsConfigurable;
  private VcsDirectoryMapping myMappingCopy;
  private JComponent myVcsConfigurableComponent;
  private final ProjectLevelVcsManager myVcsManager;
  @NotNull private final Map<String, VcsDescriptor> myVcses;

  public VcsMappingConfigurationDialog(@NotNull Project project, @NlsContexts.DialogTitle String title) {
    super(project, false);
    myProject = project;
    myVcsManager = ProjectLevelVcsManager.getInstance(myProject);
    myVcses = uniqueIndex(Arrays.asList(myVcsManager.getAllVcss()), VcsDescriptor::getName);
    myVCSComboBox.setModel(buildVcsWrappersModel(project));
    myDirectoryTextField.addActionListener(
      new MyBrowseFolderListener(VcsBundle.getString("settings.vcs.mapping.browser.select.directory.title"),
                                 VcsBundle.getString("settings.vcs.mapping.browser.select.directory.description"),
                                 myDirectoryTextField,
                                 project,
                                 createSingleFolderDescriptor()));
    setMapping(suggestDefaultMapping(project));
    initProjectMessage();
    setTitle(title);
    init();
    myVCSComboBox.addActionListener(e -> updateVcsConfigurable());
  }

  @NotNull
  private static VcsDirectoryMapping suggestDefaultMapping(@NotNull Project project) {
    String basePath = project.getBasePath();
    if (basePath == null) return VcsDirectoryMapping.createDefault("");
    return new VcsDirectoryMapping(basePath, "");
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  public void setMapping(@NotNull VcsDirectoryMapping mapping) {
    myMappingCopy = new VcsDirectoryMapping(mapping.getDirectory(), mapping.getVcs(), mapping.getRootSettings());
    myProjectRadioButton.setSelected(myMappingCopy.isDefaultMapping());
    myDirectoryRadioButton.setSelected(!myProjectRadioButton.isSelected());
    myDirectoryTextField.setText(myMappingCopy.isDefaultMapping() ? "" : toSystemDependentName(mapping.getDirectory()));

    myVCSComboBox.setSelectedItem(myVcses.get(mapping.getVcs()));
    updateVcsConfigurable();
    myDirectoryTextField.setEnabled(myDirectoryRadioButton.isSelected());
  }

  @NotNull
  public VcsDirectoryMapping getMapping() {
    VcsDescriptor wrapper = (VcsDescriptor) myVCSComboBox.getSelectedItem();
    String vcs = wrapper == null || wrapper.isNone() ? "" : wrapper.getName();
    String directory = myProjectRadioButton.isSelected() ? "" : toSystemIndependentName(myDirectoryTextField.getText());
    return new VcsDirectoryMapping(directory, vcs, myMappingCopy.getRootSettings());
  }

  private void updateVcsConfigurable() {
    if (myVcsConfigurable != null) {
      myVcsConfigurablePlaceholder.remove(myVcsConfigurableComponent);
      myVcsConfigurable.disposeUIResources();
      myVcsConfigurable = null;
    }
    VcsDescriptor wrapper = (VcsDescriptor) myVCSComboBox.getSelectedItem();
    if (wrapper != null && !wrapper.isNone()) {
      AbstractVcs vcs = myVcsManager.findVcsByName(wrapper.getName());
      if (vcs != null) {
        UnnamedConfigurable configurable = vcs.getRootConfigurable(myMappingCopy);
        if (configurable != null) {
          myVcsConfigurable = configurable;
          myVcsConfigurableComponent = Objects.requireNonNull(myVcsConfigurable.createComponent());
          myVcsConfigurablePlaceholder.add(myVcsConfigurableComponent, BorderLayout.CENTER);
        }
      }
    }
    pack();
  }

  @Override
  protected void doOKAction() {
    if (myVcsConfigurable != null) {
      try {
        myVcsConfigurable.apply();
      }
      catch(ConfigurationException ex) {
        Messages.showErrorDialog(myPanel, VcsBundle.message("settings.vcs.mapping.invalid.vcs.options.error", ex.getMessage()));
      }
    }
    super.doOKAction();
  }

  private void createUIComponents() {
    ButtonGroup bg = new ButtonGroup();
    myProjectRadioButton = new JRadioButton();
    myDirectoryRadioButton = new JRadioButton();
    bg.add(myProjectRadioButton);
    bg.add(myDirectoryRadioButton);
    ActionListener listener = e -> myDirectoryTextField.setEnabled(myDirectoryRadioButton.isSelected());
    myProjectRadioButton.addActionListener(listener);
    myDirectoryRadioButton.addActionListener(listener);
    myDirectoryRadioButton.setSelected(true);
  }

  private void initProjectMessage() {
    myProjectButtonComment.setText(wrapInHtml(DefaultVcsRootPolicy.getInstance(myProject).getProjectConfigurationMessage()));
  }

  private class MyBrowseFolderListener extends ComponentWithBrowseButton.BrowseFolderActionListener<JTextField> {

    MyBrowseFolderListener(String title,
                                  String description,
                                  TextFieldWithBrowseButton textField,
                                  Project project,
                                  FileChooserDescriptor fileChooserDescriptor) {
      super(title, description, textField, project, fileChooserDescriptor, TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT);
    }

    @Override
    protected VirtualFile getInitialFile() {
      // suggest project base dir only if nothing is typed in the component.
      String text = getComponentText();
      if (text.isEmpty()) {
        VirtualFile file = myProject.getBaseDir();
        if (file != null) {
          return file;
        }
      }
      return super.getInitialFile();
    }

    @Override
    protected void onFileChosen(@NotNull final VirtualFile chosenFile) {
      String oldText = myDirectoryTextField.getText();
      super.onFileChosen(chosenFile);
      VcsDescriptor wrapper = (VcsDescriptor)myVCSComboBox.getSelectedItem();
      if (oldText.isEmpty() && (wrapper == null || wrapper.isNone())) {
        new Task.Backgroundable(myProject, VcsBundle.message("settings.vcs.mapping.status.looking.for.vcs.administrative.area"), false) {
          private VcsDescriptor probableVcs = null;

          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            probableVcs = getIfSingle(myVcses.values().stream().filter(descriptor -> descriptor.probablyUnderVcs(chosenFile)));
          }

          @Override
          public void onSuccess() {
            if (probableVcs != null) {
              myVCSComboBox.setSelectedItem(probableVcs);
            }
          }
        }.queue();
      }
    }
  }
}
