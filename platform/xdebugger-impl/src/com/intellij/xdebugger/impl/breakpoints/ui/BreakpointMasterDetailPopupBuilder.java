/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.actionSystem.ex.CheckboxAction;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.ex.DefaultColorSchemesManager;
import com.intellij.openapi.editor.colors.impl.EditorColorsSchemeImpl;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ListUtil;
import com.intellij.ui.popup.util.MasterDetailPopupBuilder;
import com.intellij.util.IconUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.HashSet;
import com.intellij.xdebugger.breakpoints.ui.BreakpointItem;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroupingRule;
import com.intellij.xdebugger.ui.DebuggerColors;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class BreakpointMasterDetailPopupBuilder {

  private Project myProject;
  private MasterDetailPopupBuilder myPopupBuilder;
  private Collection<BreakpointPanelProvider> myBreakpointsPanelProviders;
  private BreakpointItemsTree myTree;
  private final List<XBreakpointGroupingRule> myRulesAvailable = new ArrayList<XBreakpointGroupingRule>();
    
  private final Set<XBreakpointGroupingRule> myRulesEnabled = new HashSet<XBreakpointGroupingRule>();

  @Nullable private Object myInitialBreakpoint;

  public void setInitialBreakpoint(@Nullable Object initialBreakpoint) {
    myInitialBreakpoint = initialBreakpoint;
  }

  public BreakpointMasterDetailPopupBuilder(Project project) {
    myProject = project;
  }
  

  public JBPopup createPopup() {
    myPopupBuilder = new MasterDetailPopupBuilder(myProject);

    for (BreakpointPanelProvider provider : myBreakpointsPanelProviders) {
      provider.createBreakpointsGroupingRules(myRulesAvailable);
    }

    for (XBreakpointGroupingRule rule : myRulesAvailable) {
      if (rule.isAlwaysEnabled()) {
        myRulesEnabled.add(rule);
      }
    }

    DefaultActionGroup actions = createActions();


    myTree = BreakpointItemsTree.createTree(getEnabledRulesList());

    final ArrayList<BreakpointItem> breakpoints = collectItems();
    myTree.buildTree(breakpoints);


    final BreakpointPanelProvider.BreakpointsListener listener = new BreakpointPanelProvider.BreakpointsListener() {
      @Override
      public void breakpointsChanged() {
        myTree.buildTree(collectItems());
      }
    };

    for (BreakpointPanelProvider provider : myBreakpointsPanelProviders) {
      provider.addListener(listener, myProject);
    }

    final JBPopup popup = myPopupBuilder.
      setActionsGroup(actions).
      setTree(myTree).
      setDelegate(new MasterDetailPopupBuilder.Delegate() {
        @Override
        public String getTitle() {
          return "Breakpoints";
        }

        @Override
        public void handleMnemonic(KeyEvent e, Project project, JBPopup popup) {
          //To change body of implemented methods use File | Settings | File Templates.
        }

        public JComponent createAccessoryView(Project project) {
          return new JCheckBox();
        }

        @Override
        public Object[] getSelectedItemsInTree() {
          final List<BreakpointItem> res = myTree.getSelectedBreakpoints();
          return res.toArray(new Object[res.size()]);
        }
      }).setCloseOnEnter(false).createMasterDetailPopup();

    myTree.setBorder(IdeBorderFactory.createBorder());

    myPopupBuilder.getDetailView().setScheme(createScheme());

    myTree.setDelegate(new BreakpointItemsTree.BreakpointItemsTreeDelegate() {
      @Override
      public void execute(BreakpointItem item) {
        item.execute(myProject, popup);
      }
    });

    initSelection(breakpoints);

    popup.addListener(new JBPopupListener() {
      @Override
      public void beforeShown(LightweightWindowEvent event) {
        //To change body of implemented methods use File | Settings | File Templates.
      }

      @Override
      public void onClosed(LightweightWindowEvent event) {
        for (BreakpointPanelProvider provider : myBreakpointsPanelProviders) {
          provider.removeListener(listener);
        }
      }
    });

    return popup;
  }

  void initSelection(ArrayList<BreakpointItem> breakpoints) {
    boolean found = false;
    for (BreakpointItem breakpoint : breakpoints) {
      if (breakpoint.getBreakpoint() == myInitialBreakpoint) {
        myTree.selectBreakpointItem(breakpoint);
        found = true;
        break;
      }
    }

    if (!found && !breakpoints.isEmpty()) {
      myTree.selectBreakpointItem(breakpoints.get(0));
    }
  }

  EditorColorsScheme createScheme() {
    final EditorColorsScheme scheme =
      new EditorColorsSchemeImpl(EditorColorsManager.getInstance().getGlobalScheme(), DefaultColorSchemesManager.getInstance());
    scheme.setName("abc");
    scheme
      .setAttributes(DebuggerColors.BREAKPOINT_ATTRIBUTES, new TextAttributes(Color.black, Color.CYAN, null, EffectType.BOXED, Font.BOLD));
    return scheme;
  }

  DefaultActionGroup createActions() {
    DefaultActionGroup actions = new DefaultActionGroup();
    final DefaultActionGroup breakpointTypes = new DefaultActionGroup();
    for (BreakpointPanelProvider provider : myBreakpointsPanelProviders) {
      breakpointTypes.addAll(provider.getAddBreakpointActions(myProject));
    }
    actions.add(new AnAction("Add Breakpoint", null, IconUtil.getAddIcon()) {
      @Override
      public void actionPerformed(AnActionEvent e) {

        JBPopupFactory.getInstance()
          .createActionGroupPopup(null, breakpointTypes, e.getDataContext(), JBPopupFactory.ActionSelectionAid.NUMBERING, false)
          .showUnderneathOf(myPopupBuilder.getActionToolbar().getComponent());
      }
    });
    actions.add(new AnAction("Remove Breakpoint", null, PlatformIcons.DELETE_ICON) {
      @Override
      public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(MasterDetailPopupBuilder.allowedToRemoveItems(myPopupBuilder.getSelectedItems()));
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        myPopupBuilder.removeSelectedItems(myProject);
      }
    });

    for (XBreakpointGroupingRule rule : myRulesAvailable) {
      if (!rule.isAlwaysEnabled()) {
        actions.add(new ToggleBreakpointGroupingRuleEnabledAction(rule));
      }
    }

    return actions;
  }

  ArrayList<BreakpointItem> collectItems() {
    ArrayList<BreakpointItem> items = new ArrayList<BreakpointItem>();
    for (BreakpointPanelProvider panelProvider : myBreakpointsPanelProviders) {
      panelProvider.provideBreakpointItems(myProject, items);
    }
    return items;
  }

  public void setBreakpointsPanelProviders(Collection<BreakpointPanelProvider> breakpointsPanelProviders) {
    myBreakpointsPanelProviders = breakpointsPanelProviders;
  }

  private class ToggleBreakpointGroupingRuleEnabledAction extends CheckboxAction {
    private XBreakpointGroupingRule myRule;

    public ToggleBreakpointGroupingRuleEnabledAction(XBreakpointGroupingRule rule) {
      super(rule.getPresentableName());
      myRule = rule;
      getTemplatePresentation().setText(rule.getPresentableName());
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myRulesEnabled.contains(myRule);
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      if (state) {
        myRulesEnabled.add(myRule);
      }
      else {
        myRulesEnabled.remove(myRule);
      }
      myTree.setGroupingRules(getEnabledRulesList());
    }
  }

  private List<XBreakpointGroupingRule> getEnabledRulesList() {
    List<XBreakpointGroupingRule> result = new ArrayList<XBreakpointGroupingRule>();
    for (XBreakpointGroupingRule rule : myRulesAvailable) {
      if (myRulesEnabled.contains(rule)) {
        result.add(rule);
      }
    }
    return result;
  }
}
