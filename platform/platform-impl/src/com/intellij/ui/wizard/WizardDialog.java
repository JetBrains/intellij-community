/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ui.wizard;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ex.MultiLineLabel;
import com.intellij.ui.SeparatorComponent;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;

public class WizardDialog<T extends WizardModel> extends DialogWrapper implements WizardCallback {
  protected final T myModel;

  private final JButton myPrevious = new JButton();
  private final JButton myNext = new JButton();
  private final JButton myFinish = new JButton();
  private final JButton myCancel = new JButton();
  private final JButton myHelp = new JButton();

  private final JLabel myIcon = new JLabel();
  private final JLabel myHeader = new JLabel();
  private final JLabel myExplanation = new MultiLineLabel();

  private JPanel myStepContent;
  private CardLayout myCardLayout;
  private final Map<WizardStep, String> myStepCardNames = new HashMap<>();

  public WizardDialog(Project project, boolean canBeParent, T model) {
    super(project, canBeParent);
    myModel = model;
    init();
  }

  public WizardDialog(boolean canBeParent, T model) {
    super(canBeParent);
    myModel = model;
    init();
  }

  public WizardDialog(boolean canBeParent, boolean tryApplicationModal, T model) {
    super(null, canBeParent, tryApplicationModal);
    myModel = model;
    init();
  }

  public WizardDialog(Component parent, boolean canBeParent, T model) {
    super(parent, canBeParent);
    myModel = model;
    init();
  }


  protected JComponent createCenterPanel() {
    JPanel result = new JPanel(new BorderLayout());

    JPanel icon = new JPanel(new BorderLayout());
    icon.add(myIcon, BorderLayout.NORTH);
    result.add(icon, BorderLayout.WEST);

    JPanel header = new JPanel();
    header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
    header.add(myHeader);
    header.add(Box.createVerticalStrut(4));
    header.add(myExplanation);
    header.add(Box.createVerticalStrut(4));
    header.add(new SeparatorComponent(0, Color.gray, null));
    header.setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 2));

    JPanel content = new JPanel(new BorderLayout(12, 12));
    content.add(header, BorderLayout.NORTH);

    myCardLayout = new CardLayout();
    myStepContent = new JPanel(myCardLayout) {
      public Dimension getPreferredSize() {
        Dimension custom = getWindowPreferredSize();
        Dimension superSize = super.getPreferredSize();
        if (custom != null) {
          custom.width = custom.width > 0 ? custom.width : superSize.width;
          custom.height = custom.height > 0 ? custom.height : superSize.height;
        } else {
          custom = superSize;
        }
        return custom;
      }
    };

    content.add(header, BorderLayout.NORTH);
    content.add(myStepContent, BorderLayout.CENTER);

    result.add(content, BorderLayout.CENTER);

    //myHeader.setFont(myHeader.getFont().deriveFont(Font.BOLD, 14));
    //myHeader.setFont(myHeader.getFont().deriveFont(Font.PLAIN, 12));

    return result;
  }

  protected void init() {
    setTitle(myModel.getTitle());

    initHelpButton();

    myModel.setCallback(this);
    super.init();

    initCurrentStep();
  }

  private void initHelpButton() {
    myHelp.setText("Help");
    myHelp.setMnemonic('H');
    myHelp.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        onHelp();
      }
    });

    getRootPane().registerKeyboardAction(
      new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          onHelp();
        }
      },
      KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0),
      JComponent.WHEN_IN_FOCUSED_WINDOW
    );

    getRootPane().registerKeyboardAction(
      new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          onHelp();
        }
      },
      KeyStroke.getKeyStroke(KeyEvent.VK_HELP, 0),
      JComponent.WHEN_IN_FOCUSED_WINDOW
    );
  }

  private void onHelp() {
    HelpManager.getInstance().invokeHelp(myModel.getCurrentStep().getHelpId());
  }

  private void initCurrentStep() {
    WizardStep current = myModel.getCurrentStep();

    myIcon.setIcon(current.getIcon());
    myHeader.setFont(myHeader.getFont().deriveFont(Font.BOLD, 14));
    myHeader.setText(current.getTitle());
    myExplanation.setText(current.getExplanation());

    @NonNls String stepName = myStepCardNames.get(current);
    if (stepName == null) {
      stepName = "Step" + myStepCardNames.size();
      myStepContent.add(myModel.getCurrentComponent(), stepName);
      myStepCardNames.put(current, stepName);
    }
    myCardLayout.show(myStepContent, stepName);

    WizardNavigationState state = myModel.getCurrentNavigationState();
    myPrevious.setAction(state.PREVIOUS);
    myNext.setAction(state.NEXT);
    myFinish.setAction(state.FINISH);
    myCancel.setAction(state.CANCEL);

    if (myNext.isEnabled()) {
      getRootPane().setDefaultButton(myNext);
    }
    else if (myFinish.isEnabled()) {
      getRootPane().setDefaultButton(myFinish);
      myFinish.requestFocusInWindow();
    }
    else if (myCancel.isEnabled()) {
      getRootPane().setDefaultButton(myCancel);
    }
    else {
      getRootPane().setDefaultButton(null);
    }
    JComponent focusComponent = current.getPreferredFocusedComponent();
    if (focusComponent != null) {
      focusComponent.requestFocusInWindow();
    }
  }

  protected JComponent createSouthPanel() {
    final JPanel southPanel = new JPanel(new BorderLayout());
    final JPanel panel = new JPanel(new GridLayout(1, 0, 5, 0));
    panel.add(myPrevious);
    panel.add(myNext);
    panel.add(myFinish);
    panel.add(myCancel);
    if (ApplicationManager.getApplication() != null) {
      // we won't be able to show help if there's no HelpManager
      panel.add(myHelp);
    }
    southPanel.add(panel, BorderLayout.EAST);
    southPanel.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
    return southPanel;
  }

  public void onStepChanged() {
    initCurrentStep();
  }

  public void onWizardGoalDropped() {
    doCancelAction();
  }

  public void onWizardGoalAchieved() {
    doOKAction();
  }

  public boolean isWizardGoalAchieved() {
    return isOK();
  }

  protected Dimension getWindowPreferredSize() {
    return null;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myModel.getCurrentStep().getPreferredFocusedComponent();
  }
}
