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
package com.intellij.openapi.options.ex;

import com.intellij.CommonBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

public class SingleConfigurableEditor extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.options.ex.SingleConfigurableEditor");
  private Project myProject;
  private Component myParentComponent;
  private Configurable myConfigurable;
  private JComponent myCenterPanel;
  private String myDimensionKey;
  private boolean myChangesWereApplied;

  public SingleConfigurableEditor(Project project, Configurable configurable, @NonNls String dimensionKey) {
    super(project, true);
    myDimensionKey = dimensionKey;
    setTitle(createTitleString(configurable));

    myProject = project;
    myConfigurable = configurable;
    init();
    myConfigurable.reset();
  }

  public SingleConfigurableEditor(Component parent, Configurable configurable, String dimensionServiceKey) {
    super(parent, true);
    myDimensionKey = dimensionServiceKey;
    setTitle(createTitleString(configurable));

    myParentComponent = parent;
    myConfigurable = configurable;
    init();
    myConfigurable.reset();
  }

  public Configurable getConfigurable() {
    return myConfigurable;
  }

  public Project getProject() {
    return myProject;
  }

  public SingleConfigurableEditor(Project project, Configurable configurable) {
    this(project, configurable, null);
  }

  public SingleConfigurableEditor(Component parent, Configurable configurable) {
    this(parent, configurable, null);
  }

  private static String createTitleString(Configurable configurable) {
    String displayName = configurable.getDisplayName();
    LOG.assertTrue(displayName != null, configurable.getClass().getName());
    return displayName.replaceAll("\n", " ");
  }

  protected String getDimensionServiceKey() {
    if (myDimensionKey == null) {
      return super.getDimensionServiceKey();
    }
    else {
      return myDimensionKey;
    }
  }

  protected Action[] createActions() {
    if (myConfigurable.getHelpTopic() != null) {
      return new Action[]{getOKAction(), getCancelAction(), new ApplyAction(), getHelpAction()};
    }
    else {
      return new Action[]{getOKAction(), getCancelAction(), new ApplyAction()};
    }
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(myConfigurable.getHelpTopic());
  }

  @Override
  public void doCancelAction() {
    if (myChangesWereApplied) {
      ApplicationManager.getApplication().saveAll();
    }
    super.doCancelAction();
  }

  protected void doOKAction() {
    try {
      if (myConfigurable.isModified()) myConfigurable.apply();

      ApplicationManager.getApplication().saveAll();
    }
    catch (ConfigurationException e) {
      if (e.getMessage() != null) {
        if (myProject != null) {
          Messages.showMessageDialog(myProject, e.getMessage(), e.getTitle(), Messages.getErrorIcon());
        }
        else {
          Messages.showMessageDialog(myParentComponent, e.getMessage(), e.getTitle(), Messages.getErrorIcon());
        }
      }
      return;
    }
    super.doOKAction();
  }

  protected static String createDimensionKey(Configurable configurable) {
    String displayName = configurable.getDisplayName();
    displayName = displayName.replaceAll("\n", "_").replaceAll(" ", "_");
    return "#" + displayName;
  }

  protected class ApplyAction extends AbstractAction {
    private final Alarm myUpdateAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);

    public ApplyAction() {
      super(CommonBundle.getApplyButtonText());
      final Runnable updateRequest = new Runnable() {
        public void run() {
          if (!SingleConfigurableEditor.this.isShowing()) return;
          ApplyAction.this.setEnabled(myConfigurable != null && myConfigurable.isModified());
          addUpdateRequest(this);
        }
      };

      // invokeLater necessary to make sure dialog is already shown so we calculate modality state correctly.
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          addUpdateRequest(updateRequest);
        }
      });
    }

    private void addUpdateRequest(final Runnable updateRequest) {
      myUpdateAlarm.addRequest(updateRequest, 500, ModalityState.stateForComponent(getWindow()));
    }

    public void actionPerformed(ActionEvent event) {
      if (myPerformAction) return;
      try {
        myPerformAction = true;
        if (myConfigurable.isModified()) {
          myConfigurable.apply();
          myChangesWereApplied = true;
          setCancelButtonText(CommonBundle.getCloseButtonText());
        }
      }
      catch (ConfigurationException e) {
        if (myProject != null) {
          Messages.showMessageDialog(myProject, e.getMessage(), e.getTitle(), Messages.getErrorIcon());
        }
        else {
          Messages.showMessageDialog(myParentComponent, e.getMessage(), e.getTitle(),
                                     Messages.getErrorIcon());
        }
      } finally {
        myPerformAction = false;
      }
    }
  }

  protected JComponent createCenterPanel() {
    myCenterPanel = myConfigurable.createComponent();
    return myCenterPanel;
  }

  public JComponent getPreferredFocusedComponent() {
    if (myConfigurable instanceof BaseConfigurable) {
      JComponent preferred = ((BaseConfigurable)myConfigurable).getPreferredFocusedComponent();
      if (preferred != null) return preferred;
    }
    return IdeFocusTraversalPolicy.getPreferredFocusedComponent(myCenterPanel);
  }

  public void dispose() {
    super.dispose();
    myConfigurable.disposeUIResources();
    myConfigurable = null;
  }
}
