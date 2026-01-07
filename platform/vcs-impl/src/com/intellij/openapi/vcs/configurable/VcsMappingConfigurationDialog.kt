// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.configurable;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vcs.impl.DefaultVcsRootPolicy;
import com.intellij.openapi.vcs.impl.VcsDescriptor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static com.intellij.openapi.fileChooser.FileChooserDescriptorFactory.createSingleFolderDescriptor;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static com.intellij.openapi.util.io.FileUtil.toSystemIndependentName;
import static com.intellij.openapi.vcs.configurable.VcsDirectoryConfigurationPanel.buildVcsesComboBox;
import static com.intellij.util.containers.UtilKt.getIfSingle;
import static com.intellij.xml.util.XmlStringUtil.wrapInHtml;

@ApiStatus.Internal
public class VcsMappingConfigurationDialog extends DialogWrapper {
  private final @NotNull Project myProject;
  private ComboBox<AbstractVcs> myVCSComboBox;
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

  public VcsMappingConfigurationDialog(@NotNull Project project, @NlsContexts.DialogTitle String title) {
    super(project, false);
    myProject = project;
    myVcsManager = ProjectLevelVcsManager.getInstance(myProject);
    var descriptor = createSingleFolderDescriptor()
      .withTitle(VcsBundle.message("settings.vcs.mapping.browser.select.directory.title"))
      .withDescription(VcsBundle.message("settings.vcs.mapping.browser.select.directory.description"));
    myDirectoryTextField.addActionListener(new MyBrowseFolderListener(myDirectoryTextField, project, descriptor));
    setMapping(suggestDefaultMapping(project));
    initProjectMessage();
    setTitle(title);
    init();
    myVCSComboBox.addActionListener(e -> updateVcsConfigurable());
  }

  private static @NotNull VcsDirectoryMapping suggestDefaultMapping(@NotNull Project project) {
    AbstractVcs[] vcses = ProjectLevelVcsManager.getInstance(project).getAllSupportedVcss();
    ContainerUtil.sort(vcses, SuggestedVcsComparator.create(project));
    String defaultVcsName = vcses.length > 0 ? vcses[0].getName() : "";

    String basePath = project.getBasePath();
    if (basePath == null) return VcsDirectoryMapping.createDefault(defaultVcsName);
    return new VcsDirectoryMapping(basePath, defaultVcsName);
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

    myVCSComboBox.setSelectedItem(myVcsManager.findVcsByName(mapping.getVcs()));
    updateVcsConfigurable();
    myDirectoryTextField.setEnabled(myDirectoryRadioButton.isSelected());
  }

  public @NotNull VcsDirectoryMapping getMapping() {
    AbstractVcs vcs = myVCSComboBox.getItem();
    String vcsName = vcs != null ? vcs.getName() : "";
    String directory = myProjectRadioButton.isSelected() ? "" : toSystemIndependentName(myDirectoryTextField.getText());
    return new VcsDirectoryMapping(directory, vcsName, myMappingCopy.getRootSettings());
  }

  private void updateVcsConfigurable() {
    if (myVcsConfigurable != null) {
      myVcsConfigurablePlaceholder.remove(myVcsConfigurableComponent);
      myVcsConfigurable.disposeUIResources();
      myVcsConfigurable = null;
    }
    AbstractVcs vcs = myVCSComboBox.getItem();
    if (vcs != null) {
      UnnamedConfigurable configurable = vcs.getRootConfigurable(myMappingCopy);
      if (configurable != null) {
        myVcsConfigurable = configurable;
        myVcsConfigurableComponent = Objects.requireNonNull(myVcsConfigurable.createComponent());
        myVcsConfigurablePlaceholder.add(myVcsConfigurableComponent, BorderLayout.CENTER);
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

    myVCSComboBox = buildVcsesComboBox(myProject);
  }

  private void initProjectMessage() {
    myProjectButtonComment.setText(wrapInHtml(DefaultVcsRootPolicy.getInstance(myProject).getProjectConfigurationMessage()));
  }

  private class MyBrowseFolderListener extends ComponentWithBrowseButton.BrowseFolderActionListener<JTextField> {
    MyBrowseFolderListener(TextFieldWithBrowseButton textField, Project project, FileChooserDescriptor fileChooserDescriptor) {
      super(textField, project, fileChooserDescriptor, TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT);
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
    protected void onFileChosen(final @NotNull VirtualFile chosenFile) {
      String oldText = myDirectoryTextField.getText();
      super.onFileChosen(chosenFile);
      AbstractVcs vcs = myVCSComboBox.getItem();
      if (oldText.isEmpty() && vcs != null) {
        new Task.Backgroundable(myProject, VcsBundle.message("settings.vcs.mapping.status.looking.for.vcs.administrative.area"), true) {
          private VcsDescriptor probableVcs = null;

          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            List<VcsDescriptor> allVcss = Arrays.asList(myVcsManager.getAllVcss());
            probableVcs = getIfSingle(allVcss.stream().filter(descriptor -> descriptor.probablyUnderVcs(chosenFile)));
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
