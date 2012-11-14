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
package com.intellij.xdebugger.impl.breakpoints.ui.tree;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.CheckboxAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.popup.util.DetailView;
import com.intellij.ui.popup.util.DetailViewImpl;
import com.intellij.ui.popup.util.ItemWrapper;
import com.intellij.ui.popup.util.MasterDetailPopupBuilder;
import com.intellij.util.Function;
import com.intellij.util.IconUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.breakpoints.XBreakpointManager;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroupingRule;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointsDialogState;
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointItem;
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointPanelProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.List;

public class BreakpointMasterDetailPopupBuilder {

  private Project myProject;
  private MasterDetailPopupBuilder myPopupBuilder;
  private Collection<BreakpointPanelProvider> myBreakpointsPanelProviders = Collections.emptyList();
  private BreakpointItemsTreeController myTreeController;
  private final List<XBreakpointGroupingRule> myRulesAvailable = new ArrayList<XBreakpointGroupingRule>();
    
  private Set<XBreakpointGroupingRule> myRulesEnabled = new HashSet<XBreakpointGroupingRule>();

  @Nullable private Object myInitialBreakpoint;

  @Nullable private BreakpointChosenCallback myCallback = null;

  private boolean myAddDetailViewToEast = true;

  private DetailView myDetailView;

  private boolean myIsViewer;

  private boolean myPlainView = false;
  private JBPopup myPopup;

  public boolean isPlainView() {
    return myPlainView;
  }

  public void setPlainView(boolean plainView) {
    myPlainView = plainView;
  }

  public Collection<BreakpointItem> getBreakpointItems() {
    return myBreakpointItems;
  }

  public void setBreakpointItems(Collection<BreakpointItem> breakpointItems) {
    myBreakpointItems = breakpointItems;
  }

  private Collection<BreakpointItem> myBreakpointItems = new ArrayList<BreakpointItem>();

  public void setDetailView(DetailView detailView) {
    myDetailView = detailView;
  }

  public void setAddDetailViewToEast(boolean addDetailViewToEast) {
    myAddDetailViewToEast = addDetailViewToEast;
  }

  public void setCallback(BreakpointChosenCallback callback) {
    myCallback = callback;
  }

  public void setIsViewer(boolean isViewer) {
    myIsViewer = isViewer;
  }

  public interface BreakpointChosenCallback {
    void breakpointChosen(Project project, BreakpointItem breakpointItem, JBPopup popup, boolean withEnterOrDoubleClick);
  }

  public void setInitialBreakpoint(@Nullable Object initialBreakpoint) {
    myInitialBreakpoint = initialBreakpoint;
  }

  public BreakpointMasterDetailPopupBuilder(Project project) {
    myProject = project;
  }

  public JBPopup createPopup() {
    myPopupBuilder = new MasterDetailPopupBuilder(myProject);
    if (!myPlainView) {
      myPopupBuilder.setDimensionServiceKey(getClass().getName());
      myPopupBuilder.setCancelOnClickOutside(false);
    }
    myPopupBuilder.setCancelOnWindowDeactivation(false);

    DetailViewImpl view = null;
    if (myDetailView != null) {
      myPopupBuilder.setDetailView(myDetailView);
    } else {
      view = new DetailViewImpl(myProject);

      myPopupBuilder.setDetailView(view);
    }
    myPopupBuilder.setAddDetailViewToEast(myAddDetailViewToEast);

    for (BreakpointPanelProvider provider : myBreakpointsPanelProviders) {
      provider.createBreakpointsGroupingRules(myRulesAvailable);
    }

    if (!myIsViewer) {
      myRulesEnabled = getInitialGroupingRules();
    }

    DefaultActionGroup actions = createActions();

    myTreeController = new BreakpointItemsTreeController(myRulesEnabled);

    JTree tree = myIsViewer ? new BreakpointsSimpleTree(myTreeController) : new BreakpointsCheckboxTree(myTreeController);

    if (myPlainView) {
      tree.putClientProperty("plainView", Boolean.TRUE);
    }

    myTreeController.setTreeView(tree);

    collectItems();

    myTreeController.buildTree(myBreakpointItems);


    final BreakpointPanelProvider.BreakpointsListener listener = new BreakpointPanelProvider.BreakpointsListener() {
      @Override
      public void breakpointsChanged() {
        collectItems();
        myTreeController.rebuildTree(myBreakpointItems);
      }
    };

    for (BreakpointPanelProvider provider : myBreakpointsPanelProviders) {
      provider.addListener(listener, myProject);
    }

    final MasterDetailPopupBuilder.Delegate delegate = new MasterDetailPopupBuilder.Delegate() {
      @Nullable
      @Override
      public String getTitle() {
        return "";
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
        final List<BreakpointItem> res = myTreeController.getSelectedBreakpoints();
        return res.toArray(new Object[res.size()]);
      }

      @Override
      public void itemChosen(ItemWrapper item, Project project, JBPopup popup, boolean withEnterOrDoubleClick) {
        if (myCallback != null && item instanceof BreakpointItem) {
          myCallback.breakpointChosen(myProject, (BreakpointItem)item,  popup, withEnterOrDoubleClick);
        }
      }
    };

    myPopupBuilder.
          setActionsGroup(actions).
          setTree(tree).
          setDelegate(delegate);

    if (!myIsViewer) {
      myPopupBuilder.setMinSize(new Dimension(-1, 700));
    }

    if (!myPlainView) {
      myPopupBuilder.setDoneRunnable(new Runnable() {
        @Override
        public void run() {
          myPopup.cancel();
        }
      });
    }


    myPopup = myPopupBuilder.setCloseOnEnter(false).createMasterDetailPopup();

    myTreeController.setDelegate(new BreakpointItemsTreeController.BreakpointItemsTreeDelegate() {
      @Override
      public void execute(BreakpointItem item) {
        if (myCallback != null) {
          myCallback.breakpointChosen(myProject, item, myPopup, true);
        }
      }
    });

    initSelection(myBreakpointItems);

    myPopup.addListener(new JBPopupListener() {
      @Override
      public void beforeShown(LightweightWindowEvent event) {
        //To change body of implemented methods use File | Settings | File Templates.
      }

      @Override
      public void onClosed(LightweightWindowEvent event) {
        for (BreakpointPanelProvider provider : myBreakpointsPanelProviders) {
          provider.removeListener(listener);
        }
        saveBreakpointsDialogState();
      }
    });

    return myPopup;
  }

  private void saveBreakpointsDialogState() {
    final XBreakpointsDialogState dialogState = new XBreakpointsDialogState();
    dialogState.setSelectedGroupingRules(new HashSet<String>(ContainerUtil.map(myRulesEnabled, new Function<XBreakpointGroupingRule, String>() {
      @Override
      public String fun(XBreakpointGroupingRule rule) {
        return rule.getId();
      }
    })));
    ((XBreakpointManagerImpl)getBreakpointManager()).setBreakpointsDialogSettings(dialogState);
  }

  private Set<XBreakpointGroupingRule> getInitialGroupingRules() {
      java.util.HashSet<XBreakpointGroupingRule> rules = new java.util.HashSet<XBreakpointGroupingRule>();
      XBreakpointsDialogState settings = ((XBreakpointManagerImpl)getBreakpointManager()).getBreakpointsDialogSettings();
      if (settings != null) {
        for (XBreakpointGroupingRule rule : myRulesAvailable) {
          if (settings.getSelectedGroupingRules().contains(rule.getId()) || rule.isAlwaysEnabled()) {
            rules.add(rule);
          }
        }
      }
      return rules;
    }

  private XBreakpointManager getBreakpointManager() {
    return XDebuggerManager.getInstance(myProject).getBreakpointManager();
  }

  void initSelection(Collection<BreakpointItem> breakpoints) {
    boolean found = false;
    for (BreakpointItem breakpoint : breakpoints) {
      if (breakpoint.getBreakpoint() == myInitialBreakpoint) {
        myTreeController.selectBreakpointItem(breakpoint, null);
        found = true;
        break;
      }
    }

    if (!found && !breakpoints.isEmpty()) {
      myTreeController.selectFirstBreakpointItem();
    }
  }

  @Nullable
  DefaultActionGroup createActions() {
    if (myIsViewer) {
      return null;
    }
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

  void collectItems() {
    if (!myBreakpointsPanelProviders.isEmpty()) {
      myBreakpointItems.clear();
      for (BreakpointPanelProvider panelProvider : myBreakpointsPanelProviders) {
        panelProvider.provideBreakpointItems(myProject, myBreakpointItems);
      }
    }
  }

  public void setBreakpointsPanelProviders(@NotNull Collection<BreakpointPanelProvider> breakpointsPanelProviders) {
    myBreakpointsPanelProviders = breakpointsPanelProviders;
  }

  private static Font smaller(Font f) {
    return f.deriveFont(f.getStyle(), f.getSize() - 2);
  }

  private class ToggleBreakpointGroupingRuleEnabledAction extends CheckboxAction {
    private XBreakpointGroupingRule myRule;

    public ToggleBreakpointGroupingRuleEnabledAction(XBreakpointGroupingRule rule) {
      super(rule.getPresentableName());
      myRule = rule;
      getTemplatePresentation().setText(rule.getPresentableName());
    }



    @Override
    public JComponent createCustomComponent(Presentation presentation) {
      JComponent component = super.createCustomComponent(presentation);
      if (SystemInfo.isMac) {
        component.setFont(smaller(component.getFont()));
      }
      return component;
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
      myTreeController.setGroupingRules(myRulesEnabled);
    }
  }
}
