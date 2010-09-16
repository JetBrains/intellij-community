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
package com.intellij.xdebugger.impl.breakpoints.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointManager;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroupingRule;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointTypeDialogState;
import com.intellij.xdebugger.impl.breakpoints.ui.actions.GoToBreakpointAction;
import com.intellij.xdebugger.impl.breakpoints.ui.actions.RemoveBreakpointAction;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

/**
 * @author nik
 */
public class XBreakpointsPanel<B extends XBreakpoint<?>> extends AbstractBreakpointPanel<XBreakpoint> {
  private final Project myProject;
  private final DialogWrapper myParentDialog;
  private final XBreakpointType<B, ?> myType;
  private JPanel myMainPanel;
  private JPanel myPropertiesPanelWrapper;
  private JPanel myTreePanel;
  private JPanel myPropertiesPanel;
  private JPanel myButtonsPanel;
  private JPanel myEmptyPanel;
  private final XBreakpointsTree<B> myTree;
  private final XBreakpointPanelAction<B>[] myActions;
  private Map<XBreakpointPanelAction<B>, JButton> myButtons;
  private XBreakpointPropertiesPanel<B> mySelectedPropertiesPanel;
  private final Set<XBreakpointGroupingRule<B, ?>> mySelectedGroupingRules;
  private final List<XBreakpointGroupingRule<B, ?>> myAllGroupingRules;

  public XBreakpointsPanel(@NotNull Project project, @NotNull DialogWrapper parentDialog, @NotNull XBreakpointType<B, ?> type) {
    super(type.getTitle(), type.getBreakpointsDialogHelpTopic(), XBreakpoint.class);
    myProject = project;
    myParentDialog = parentDialog;
    myType = type;

    myAllGroupingRules = new ArrayList<XBreakpointGroupingRule<B,?>>(myType.getGroupingRules());
    mySelectedGroupingRules = getInitialGroupingRules();

    myTree = XBreakpointsTree.createTree(myType, mySelectedGroupingRules, myParentDialog);
    myTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(final TreeSelectionEvent e) {
        onSelectionChanged();
      }
    });

    List<XBreakpointPanelAction<B>> actions = new ArrayList<XBreakpointPanelAction<B>>();
    if (type.isAddBreakpointButtonVisible()) {
      actions.add(new AddBreakpointAction<B>(this));
    }
    actions.add(new GoToBreakpointAction<B>(this, XDebuggerBundle.message("xbreakpoints.dialog.button.goto"), true));
    actions.add(new GoToBreakpointAction<B>(this, XDebuggerBundle.message("xbreakpoints.dialog.button.view.source"), false));
    actions.add(new RemoveBreakpointAction<B>(this));
    //noinspection unchecked
    myActions = actions.toArray(new XBreakpointPanelAction[actions.size()]);

    myTreePanel.add(ScrollPaneFactory.createScrollPane(myTree), BorderLayout.CENTER);
    initButtons();
    onSelectionChanged();
  }

  private Set<XBreakpointGroupingRule<B, ?>> getInitialGroupingRules() {
    HashSet<XBreakpointGroupingRule<B, ?>> rules = new HashSet<XBreakpointGroupingRule<B, ?>>();
    XBreakpointTypeDialogState settings = ((XBreakpointManagerImpl)getBreakpointManager()).getDialogState(myType);
    if (settings != null) {
      for (XBreakpointGroupingRule<B, ?> rule : myAllGroupingRules) {
        if (settings.getSelectedGroupingRules().contains(rule.getId())) {
          rules.add(rule);
        }
      }
    }
    return rules;
  }

  private void onSelectionChanged() {
    List<B> breakpoints = myTree.getSelectedBreakpoints();
    for (XBreakpointPanelAction<B> action : myActions) {
      JButton button = myButtons.get(action);
      button.setEnabled(action.isEnabled(breakpoints));
    }

    B selectedBreakpoint = breakpoints.size() == 1 ? breakpoints.get(0) : null;
    B oldBreakpoint = mySelectedPropertiesPanel != null ? mySelectedPropertiesPanel.getBreakpoint() : null;
    if (mySelectedPropertiesPanel != null && oldBreakpoint != selectedBreakpoint) {
      mySelectedPropertiesPanel.saveProperties();
      mySelectedPropertiesPanel.dispose();
      mySelectedPropertiesPanel = null;
      myPropertiesPanel.removeAll();
    }

    if (selectedBreakpoint != null && selectedBreakpoint != oldBreakpoint) {
      mySelectedPropertiesPanel = new XBreakpointPropertiesPanel<B>(myProject, getBreakpointManager(), selectedBreakpoint);
      myPropertiesPanel.add(mySelectedPropertiesPanel.getMainPanel(), BorderLayout.CENTER);
      myPropertiesPanelWrapper.revalidate();
      myEmptyPanel.setPreferredSize(myPropertiesPanel.getPreferredSize());
    }

    updatePropertiesWrapper();
  }

  private void updatePropertiesWrapper() {
    CardLayout cardLayout = (CardLayout)myPropertiesPanelWrapper.getLayout();
    cardLayout.show(myPropertiesPanelWrapper, mySelectedPropertiesPanel != null ? "properties" : "empty");
  }


  private void initButtons() {
    myButtons = new HashMap<XBreakpointPanelAction<B>, JButton>();
    GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridx = 0;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    constraints.insets = new Insets(0, 2, 2, 2);
    for (final XBreakpointPanelAction<B> action : myActions) {
      JButton button = createButton(action);
      myButtonsPanel.add(button, constraints);
      myButtons.put(action, button);
    }
    for (XBreakpointGroupingRule<B, ?> groupingRule : myAllGroupingRules) {
      myButtonsPanel.add(createGroupingRuleCheckBox(groupingRule), constraints);
    }
  }

  private JCheckBox createGroupingRuleCheckBox(final XBreakpointGroupingRule<B, ?> groupingRule) {
    final JCheckBox checkBox = new JCheckBox(groupingRule.getPresentableName());
    checkBox.setSelected(mySelectedGroupingRules.contains(groupingRule));
    checkBox.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        if (checkBox.isSelected()) {
          mySelectedGroupingRules.add(groupingRule);
        }
        else {
          mySelectedGroupingRules.remove(groupingRule);
        }
        myTree.setGroupingRules(getSelectedGroupingRules());
      }
    });
    return checkBox;
  }

  private List<XBreakpointGroupingRule<B, ?>> getSelectedGroupingRules() {
    ArrayList<XBreakpointGroupingRule<B, ?>> rules = new ArrayList<XBreakpointGroupingRule<B, ?>>();
    for (XBreakpointGroupingRule<B, ?> rule : myAllGroupingRules) {
      if (mySelectedGroupingRules.contains(rule)) {
        rules.add(rule);
      }
    }
    return rules;
  }

  private JButton createButton(final XBreakpointPanelAction<B> action) {
    final JButton button = new JButton(action.getName());
    button.addActionListener(action);
    return button;
  }

  public void dispose() {
    if (mySelectedPropertiesPanel != null) {
      mySelectedPropertiesPanel.dispose();
    }
  }

  public Icon getTabIcon() {
    for (B b : getBreakpoints()) {
      if (b.isEnabled()) {
        return myType.getEnabledIcon();
      }
    }
    return myType.getDisabledIcon();
  }

  public void saveBreakpoints() {
    if (mySelectedPropertiesPanel != null) {
      mySelectedPropertiesPanel.saveProperties();
    }
    if (!mySelectedGroupingRules.isEmpty()) {
      XBreakpointTypeDialogState state = new XBreakpointTypeDialogState();
      for (XBreakpointGroupingRule<B, ?> rule : mySelectedGroupingRules) {
        state.getSelectedGroupingRules().add(rule.getId());
      }
      ((XBreakpointManagerImpl)getBreakpointManager()).putDialogState(myType, state);
    }
  }

  public XBreakpointManager getBreakpointManager() {
    return XDebuggerManager.getInstance(myProject).getBreakpointManager();
  }

  public void resetBreakpoints() {
    Collection<? extends B> breakpoints = getBreakpoints();
    myTree.buildTree(breakpoints);
    fireBreakpointsChanged();
  }

  @Override
  public void ensureSelectionExists() {
    final B first = ContainerUtil.getFirstItem(getBreakpoints(), null);
    if (first != null) {
      selectBreakpoint(first);
    }
  }

  private Collection<? extends B> getBreakpoints() {
    return getBreakpointManager().getBreakpoints(myType);
  }

  public boolean hasBreakpoints() {
    return !getBreakpoints().isEmpty();
  }

  public JPanel getPanel() {
    return myMainPanel;
  }

  public XBreakpointType<B, ?> getType() {
    return myType;
  }

  public XBreakpointsTree<B> getTree() {
    return myTree;
  }

  public boolean canSelectBreakpoint(final XBreakpoint breakpoint) {
    return breakpoint.getType().equals(myType);
  }

  public void selectBreakpoint(final XBreakpoint breakpoint) {
    //noinspection unchecked
    myTree.selectBreakpoint((B)breakpoint);
  }

  public DialogWrapper getParentDialog() {
    return myParentDialog;
  }

  public void hideBreakpointProperties() {
    if (mySelectedPropertiesPanel != null) {
      mySelectedPropertiesPanel.dispose();
      mySelectedPropertiesPanel = null;
      myPropertiesPanel.removeAll();
      updatePropertiesWrapper();
    }
  }

  public Project getProject() {
    return myProject;
  }
}
