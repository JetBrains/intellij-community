/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.*;

public abstract class UpdateOrStatusOptionsDialog extends OptionsDialog {
  private final JComponent myMainPanel;
  private final Map<AbstractVcs, Configurable> myEnvToConfMap = new HashMap<>();
  protected final Project myProject;


  public UpdateOrStatusOptionsDialog(Project project, Map<Configurable, AbstractVcs> confs) {
    super(project);
    setTitle(getRealTitle());
    myProject = project;
    if (confs.size() == 1) {
      myMainPanel = new JPanel(new BorderLayout());
      final Configurable configurable = confs.keySet().iterator().next();
      addComponent(confs.get(configurable), configurable, BorderLayout.CENTER);
      myMainPanel.add(Box.createVerticalStrut(10), BorderLayout.SOUTH);
    }
    else {
      myMainPanel = new JBTabbedPane();
      final ArrayList<AbstractVcs> vcses = new ArrayList<>(confs.values());
      Collections.sort(vcses, new Comparator<AbstractVcs>() {
        public int compare(final AbstractVcs o1, final AbstractVcs o2) {
          return o1.getDisplayName().compareTo(o2.getDisplayName());
        }
      });
      Map<AbstractVcs, Configurable> vcsToConfigurable = revertMap(confs);
      for (AbstractVcs vcs : vcses) {
        addComponent(vcs, vcsToConfigurable.get(vcs), vcs.getDisplayName());
      }
    }
    init();
  }

  @Override
  protected String getDimensionServiceKey() {
    return "com.intellij.openapi.vcs.update.UpdateOrStatusOptionsDialog" + getActionNameForDimensions();
  }

  private static Map<AbstractVcs, Configurable> revertMap(final Map<Configurable, AbstractVcs> confs) {
    final HashMap<AbstractVcs, Configurable> result = new HashMap<>();
    for (Configurable configurable : confs.keySet()) {
      result.put(confs.get(configurable), configurable);
    }
    return result;
  }

  protected abstract String getRealTitle();
  protected abstract String getActionNameForDimensions();

  private void addComponent(AbstractVcs vcs, Configurable configurable, String constraint) {
    myEnvToConfMap.put(vcs, configurable);
    myMainPanel.add(configurable.createComponent(), constraint);
    configurable.reset();
  }

  protected void doOKAction() {
    for (Configurable configurable : myEnvToConfMap.values()) {
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

  protected boolean shouldSaveOptionsOnCancel() {
    return false;
  }

  protected JComponent createCenterPanel() {

    return myMainPanel;
  }

  @NotNull
  protected Action[] createActions() {
    for(Configurable conf: myEnvToConfMap.values()) {
      if (conf.getHelpTopic() != null) {
        return new Action[] { getOKAction(), getCancelAction(), getHelpAction() };
      }
    }
    return super.createActions();
  }

  protected void doHelpAction() {
    String helpTopic = null;
    final Collection<Configurable> v = myEnvToConfMap.values();
    final Configurable[] configurables = v.toArray(new Configurable[v.size()]);
    if (myMainPanel instanceof JTabbedPane) {
      final int tabIndex = ((JTabbedPane)myMainPanel).getSelectedIndex();
      if (tabIndex >= 0 && tabIndex < configurables.length) {
        helpTopic = configurables [tabIndex].getHelpTopic();
      }
    }
    else {
      helpTopic = configurables [0].getHelpTopic();
    }
    if (helpTopic != null) {
      HelpManager.getInstance().invokeHelp(helpTopic);
    }
    else {
      super.doHelpAction();
    }
  }
}
