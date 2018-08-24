// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.update;

import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.options.CancelledConfigurationException;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.ui.OptionsDialog;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public abstract class UpdateOrStatusOptionsDialog extends OptionsDialog {
  private final JComponent myMainPanel;
  private final List<Configurable> myConfigurables = new ArrayList<>();
  private final boolean myHelpAvailable;
  protected final Project myProject;

  public UpdateOrStatusOptionsDialog(Project project, Map<Configurable, AbstractVcs> confs) {
    super(project);
    setTitle(getRealTitle());
    myProject = project;
    if (confs.size() == 1) {
      myMainPanel = new JPanel(new BorderLayout());
      addComponent(confs.keySet().iterator().next(), BorderLayout.CENTER);
      myMainPanel.add(Box.createVerticalStrut(10), BorderLayout.SOUTH);
    }
    else {
      myMainPanel = new JBTabbedPane();
      confs.entrySet().stream()
        .sorted(Comparator.comparing(entry -> entry.getValue().getDisplayName()))
        .forEach(entry -> addComponent(entry.getKey(), entry.getKey().getDisplayName()));
    }
    myHelpAvailable = myConfigurables.stream().anyMatch(c -> c.getHelpTopic() != null);
    init();
  }

  @Override
  protected String getDimensionServiceKey() {
    return "com.intellij.openapi.vcs.update.UpdateOrStatusOptionsDialog" + getActionNameForDimensions();
  }

  protected abstract String getRealTitle();
  protected abstract String getActionNameForDimensions();

  private void addComponent(Configurable configurable, String constraint) {
    myConfigurables.add(configurable);
    myMainPanel.add(configurable.createComponent(), constraint);
    configurable.reset();
  }

  @Override
  protected void doOKAction() {
    for (Configurable configurable : myConfigurables) {
      try {
        configurable.apply();
      }
      catch (CancelledConfigurationException e) {
        return;
      }
      catch (ConfigurationException e) {
        Messages.showErrorDialog(myProject, VcsBundle.message("messge.text.cannot.save.settings", e.getLocalizedMessage()), getRealTitle());
        return;
      }
    }
    super.doOKAction();
  }

  @Override
  protected boolean shouldSaveOptionsOnCancel() {
    return false;
  }

  @Override
  protected JComponent createCenterPanel() {
    return myMainPanel;
  }

  @Nullable
  @Override
  protected String getHelpId() {
    return myHelpAvailable ? "(fake)" : null;
  }

  @Override
  protected void doHelpAction() {
    String helpTopic = null;
    if (myMainPanel instanceof JTabbedPane) {
      int idx = ((JTabbedPane)myMainPanel).getSelectedIndex();
      if (0 <= idx && idx < myConfigurables.size()) {
        helpTopic = myConfigurables.get(idx).getHelpTopic();
      }
    }
    else {
      helpTopic = myConfigurables.get(0).getHelpTopic();
    }
    if (helpTopic != null) {
      HelpManager.getInstance().invokeHelp(helpTopic);
    }
  }
}