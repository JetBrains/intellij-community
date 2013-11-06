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
package com.intellij.xdebugger.impl.breakpoints.ui;

import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.*;
import com.intellij.ui.popup.util.DetailController;
import com.intellij.ui.popup.util.DetailViewImpl;
import com.intellij.ui.popup.util.ItemWrapper;
import com.intellij.ui.popup.util.MasterController;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroupingRule;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointsDialogState;
import com.intellij.xdebugger.impl.breakpoints.ui.tree.BreakpointItemNode;
import com.intellij.xdebugger.impl.breakpoints.ui.tree.BreakpointItemsTreeController;
import com.intellij.xdebugger.impl.breakpoints.ui.tree.BreakpointsCheckboxTree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.List;

public class BreakpointsDialog extends DialogWrapper {
  @NotNull private Project myProject;

  private Object myInitialBreakpoint;
  private List<BreakpointPanelProvider> myBreakpointsPanelProviders;

  private BreakpointItemsTreeController myTreeController;

  JLabel temp = new JLabel();

  private MasterController myMasterController = new MasterController() {
    @Override
    public ItemWrapper[] getSelectedItems() {
      final List<BreakpointItem> res = myTreeController.getSelectedBreakpoints();
      return res.toArray(new ItemWrapper[res.size()]);
    }

    @Override
    public JLabel getPathLabel() {
      return temp;
    }
  };

  private final DetailController myDetailController = new DetailController(myMasterController);

  private Collection<BreakpointItem> myBreakpointItems = new ArrayList<BreakpointItem>();

  private final List<XBreakpointGroupingRule> myRulesAvailable = new ArrayList<XBreakpointGroupingRule>();

  private Set<XBreakpointGroupingRule> myRulesEnabled = new TreeSet<XBreakpointGroupingRule>(new Comparator<XBreakpointGroupingRule>() {
    @Override
    public int compare(XBreakpointGroupingRule o1, XBreakpointGroupingRule o2) {
      final int res = o2.getPriority() - o1.getPriority();
      return res != 0 ? res : (o1.getId().compareTo(o2.getId()));
    }
  });
  private Disposable myListenerDisposable = Disposer.newDisposable();
  private List<ToggleActionButton> myToggleRuleActions = new ArrayList<ToggleActionButton>();

  private XBreakpointManagerImpl getBreakpointManager() {
    return (XBreakpointManagerImpl)XDebuggerManager.getInstance(myProject).getBreakpointManager();
  }

  protected BreakpointsDialog(@NotNull Project project, Object breakpoint, @NotNull List<BreakpointPanelProvider> providers) {
    super(project);
    myProject = project;
    myBreakpointsPanelProviders = providers;
    myInitialBreakpoint = breakpoint;

    collectGroupingRules();

    collectItems();

    setTitle("Breakpoints");
    setModal(false);
    init();
    setOKButtonText("Done");
  }

  private String getSplitterProportionKey() {
    return getDimensionServiceKey() + ".splitter";
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    JPanel mainPanel = new JPanel(new BorderLayout());

    JBSplitter splitPane = new JBSplitter(0.3f);
    splitPane.setSplitterProportionKey(getSplitterProportionKey());

    splitPane.setFirstComponent(createMasterView());
    splitPane.setSecondComponent(createDetailView());

    mainPanel.add(splitPane, BorderLayout.CENTER);

    return mainPanel;
  }

  private JComponent createDetailView() {
    DetailViewImpl detailView = new DetailViewImpl(myProject);
    myDetailController.setDetailView(detailView);

    return detailView;
  }

  void collectItems() {
    if (!myBreakpointsPanelProviders.isEmpty()) {
      myBreakpointItems.clear();
      for (BreakpointPanelProvider panelProvider : myBreakpointsPanelProviders) {
        panelProvider.provideBreakpointItems(myProject, myBreakpointItems);
      }
    }
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
  @Override
  protected String getDimensionServiceKey() {
    return getClass().getName();
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    return new Action[]{getOKAction()};
  }

  private class ToggleBreakpointGroupingRuleEnabledAction extends ToggleActionButton {
    private XBreakpointGroupingRule myRule;

    public ToggleBreakpointGroupingRuleEnabledAction(XBreakpointGroupingRule rule) {
      super(rule.getPresentableName(), rule.getIcon());
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
      myTreeController.setGroupingRules(myRulesEnabled);
    }
  }

  private JComponent createMasterView() {
    myTreeController = new BreakpointItemsTreeController(myRulesEnabled) {
      @Override
      public void nodeStateWillChangeImpl(CheckedTreeNode node) {
        if (node instanceof BreakpointItemNode) {
          ((BreakpointItemNode)node).getBreakpointItem().saveState();
        }
        super.nodeStateWillChangeImpl(node);
      }

      @Override
      public void nodeStateDidChangeImpl(CheckedTreeNode node) {
        super.nodeStateDidChangeImpl(node);
        if (node instanceof BreakpointItemNode) {
          myDetailController.doUpdateDetailView(true);
        }
      }

      @Override
      protected void selectionChangedImpl() {
        super.selectionChangedImpl();
        saveCurrentItem();
        myDetailController.updateDetailView();
      }
    };
    JTree tree = new BreakpointsCheckboxTree(myProject, myTreeController);

    new AnAction("BreakpointDialog.GoToSource") {
      @Override
      public void actionPerformed(AnActionEvent e) {
        navigate(true);
        close(OK_EXIT_CODE);
      }
    }.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)), tree);

    new AnAction("BreakpointDialog.ShowSource") {
      @Override
      public void actionPerformed(AnActionEvent e) {
        navigate(false);
      }
    }.registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE).getShortcutSet(), tree);

    final DefaultActionGroup breakpointTypes = new DefaultActionGroup();
    for (BreakpointPanelProvider provider : myBreakpointsPanelProviders) {
      breakpointTypes.addAll(provider.getAddBreakpointActions(myProject));
    }

    ToolbarDecorator decorator = ToolbarDecorator.createDecorator(tree).
      setAddAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          JBPopupFactory.getInstance()
            .createActionGroupPopup(null, breakpointTypes, DataManager.getInstance().getDataContext(button.getContextComponent()),
                                    JBPopupFactory.ActionSelectionAid.NUMBERING, false)
            .show(button.getPreferredPopupPoint());
        }
      }).
      setRemoveAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          myTreeController.removeSelectedBreakpoints(myProject);
        }
      }).
      setRemoveActionUpdater(new AnActionButtonUpdater() {
        @Override
        public boolean isEnabled(AnActionEvent e) {
          boolean enabled = false;
          final ItemWrapper[] items = myMasterController.getSelectedItems();
          for (ItemWrapper item : items) {
            if (item.allowedToRemove()) {
              enabled = true;
            }
          }
          return enabled;
        }
      }).
      setToolbarPosition(ActionToolbarPosition.TOP).
      setToolbarBorder(IdeBorderFactory.createEmptyBorder());

    tree.setBorder(IdeBorderFactory.createBorder());

    for (ToggleActionButton action : myToggleRuleActions) {
      decorator.addExtraAction(action);
    }

    JPanel decoratedTree = decorator.createPanel();
    decoratedTree.setBorder(IdeBorderFactory.createEmptyBorder());

    myTreeController.setTreeView(tree);

    myTreeController.buildTree(myBreakpointItems);

    initSelection(myBreakpointItems);

    final BreakpointPanelProvider.BreakpointsListener listener = new BreakpointPanelProvider.BreakpointsListener() {
      @Override
      public void breakpointsChanged() {
        collectItems();
        myTreeController.rebuildTree(myBreakpointItems);
      }
    };

    for (BreakpointPanelProvider provider : myBreakpointsPanelProviders) {
      provider.addListener(listener, myProject, myListenerDisposable);
    }

    return decoratedTree;
  }

  private void navigate(final boolean requestFocus) {
    List<BreakpointItem> breakpoints = myTreeController.getSelectedBreakpoints();
    if (!breakpoints.isEmpty()) {
      breakpoints.get(0).navigate(requestFocus);
    }
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myTreeController.getTreeView();
  }

  private void collectGroupingRules() {
    for (BreakpointPanelProvider provider : myBreakpointsPanelProviders) {
      provider.createBreakpointsGroupingRules(myRulesAvailable);
    }

    myRulesEnabled.clear();
    XBreakpointsDialogState settings = (getBreakpointManager()).getBreakpointsDialogSettings();

    for (XBreakpointGroupingRule rule : myRulesAvailable) {
      if (rule.isAlwaysEnabled() || (settings != null && settings.getSelectedGroupingRules().contains(rule.getId()) ) ) {
        myRulesEnabled.add(rule);
      }
    }

    for (XBreakpointGroupingRule rule : myRulesAvailable) {
      if (!rule.isAlwaysEnabled()) {
        myToggleRuleActions.add(new ToggleBreakpointGroupingRuleEnabledAction(rule));
      }
    }
  }

  private void saveBreakpointsDialogState() {
    final XBreakpointsDialogState dialogState = new XBreakpointsDialogState();
    final List<XBreakpointGroupingRule> rulesEnabled = ContainerUtil.filter(myRulesEnabled, new Condition<XBreakpointGroupingRule>() {
      @Override
      public boolean value(XBreakpointGroupingRule rule) {
        return !rule.isAlwaysEnabled();
      }
    });

    dialogState.setSelectedGroupingRules(new HashSet<String>(ContainerUtil.map(rulesEnabled, new Function<XBreakpointGroupingRule, String>() {
      @Override
      public String fun(XBreakpointGroupingRule rule) {
        return rule.getId();
      }
    })));
    getBreakpointManager().setBreakpointsDialogSettings(dialogState);
  }


  @Override
  protected void dispose() {
    saveCurrentItem();
    Disposer.dispose(myListenerDisposable);
    saveBreakpointsDialogState();
    super.dispose();
  }

  private void saveCurrentItem() {
    ItemWrapper item = myDetailController.getSelectedItem();
    if (item instanceof BreakpointItem) {
      ((BreakpointItem)item).saveState();
    }
  }
}
