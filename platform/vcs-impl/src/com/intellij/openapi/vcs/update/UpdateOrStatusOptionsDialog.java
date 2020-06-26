// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.update;

import com.intellij.CommonBundle;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.options.CancelledConfigurationException;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.ui.OptionsDialog;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.*;

public abstract class UpdateOrStatusOptionsDialog extends OptionsDialog {
  protected final Project myProject;

  private final JComponent myMainPanel;
  private final List<Configurable> myConfigurables = new ArrayList<>();
  private final Action myHelpAction = new MyHelpAction();

  public UpdateOrStatusOptionsDialog(Project project, @NlsContexts.DialogTitle String title, Map<Configurable, AbstractVcs> envToConfMap) {
    super(project);
    setTitle(title);
    myProject = project;
    if (envToConfMap.size() == 1) {
      myMainPanel = new JPanel(new BorderLayout());
      addComponent(envToConfMap.keySet().iterator().next(), BorderLayout.CENTER);
      myMainPanel.add(Box.createVerticalStrut(10), BorderLayout.SOUTH);
    }
    else {
      myMainPanel = new JBTabbedPane();
      envToConfMap.entrySet().stream()
        .sorted(Comparator.comparing(entry -> entry.getValue().getDisplayName()))
        .forEach(entry -> addComponent(entry.getKey(), entry.getKey().getDisplayName()));
    }
    init();
  }

  @Override
  protected String getDimensionServiceKey() {
    return "com.intellij.openapi.vcs.update.UpdateOrStatusOptionsDialog" + getActionNameForDimensions();
  }

  protected abstract String getActionNameForDimensions();

  private void addComponent(Configurable configurable, String constraint) {
    myConfigurables.add(configurable);
    myMainPanel.add(Objects.requireNonNull(configurable.createComponent()), constraint);
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
        Messages.showErrorDialog(myProject, VcsBundle.message("message.text.cannot.save.settings", e.getLocalizedMessage()), getTitle());
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

  @NotNull
  @Override
  protected Action getHelpAction() {
    return myHelpAction;
  }

  private String helpTopic() {
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
    return helpTopic;
  }

  private final class MyHelpAction extends AbstractAction {
    private MyHelpAction() {
      super(CommonBundle.getHelpButtonText());
    }

    @Override
    public boolean isEnabled() {
      return super.isEnabled() && helpTopic() != null;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      HelpManager.getInstance().invokeHelp(helpTopic());
    }
  }
}