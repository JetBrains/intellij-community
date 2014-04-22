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

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
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
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroupingRule;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointsDialogState;
import com.intellij.xdebugger.impl.breakpoints.ui.grouping.XBreakpointCustomGroup;
import com.intellij.xdebugger.impl.breakpoints.ui.tree.BreakpointItemNode;
import com.intellij.xdebugger.impl.breakpoints.ui.tree.BreakpointItemsTreeController;
import com.intellij.xdebugger.impl.breakpoints.ui.tree.BreakpointsCheckboxTree;
import com.intellij.xdebugger.impl.breakpoints.ui.tree.BreakpointsGroupNode;
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

  private final Collection<BreakpointItem> myBreakpointItems = new ArrayList<BreakpointItem>();

  private final List<XBreakpointGroupingRule> myRulesAvailable = new ArrayList<XBreakpointGroupingRule>();

  private final Set<XBreakpointGroupingRule> myRulesEnabled = new TreeSet<XBreakpointGroupingRule>(XBreakpointGroupingRule.PRIORITY_COMPARATOR);
  private final Disposable myListenerDisposable = Disposer.newDisposable();
  private final List<ToggleActionButton> myToggleRuleActions = new ArrayList<ToggleActionButton>();

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
      disposeItems();
      myBreakpointItems.clear();
      for (BreakpointPanelProvider panelProvider : myBreakpointsPanelProviders) {
        panelProvider.provideBreakpointItems(myProject, myBreakpointItems);
      }
    }
  }

  void initSelection(Collection<BreakpointItem> breakpoints) {
    boolean found = selectBreakpoint(myInitialBreakpoint);
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
    return new Action[]{getOKAction(), getHelpAction()};
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
    final JTree tree = new BreakpointsCheckboxTree(myProject, myTreeController) {
      @Override
      protected void onDoubleClick(CheckedTreeNode node) {
        navigate(false);
      }
    };

    PopupHandler.installPopupHandler(tree, new ActionGroup() {
      @NotNull
      @Override
      public AnAction[] getChildren(@Nullable AnActionEvent e) {
        ActionGroup group = new ActionGroup("Move to group", true) {
          @NotNull
          @Override
          public AnAction[] getChildren(@Nullable AnActionEvent e) {
            Set<String> groups = getBreakpointManager().getAllGroups();
            AnAction[] res = new AnAction[groups.size()+3];
            int i = 0;
            res[i++] = new MoveToGroupAction(null);
            for (String group : groups) {
              res[i++] = new MoveToGroupAction(group);
            }
            res[i++] = new Separator();
            res[i] = new MoveToGroupAction();
            return res;
          }
        };
        List<AnAction> res = new ArrayList<AnAction>();
        res.add(group);
        Object component = tree.getLastSelectedPathComponent();
        if (tree.getSelectionCount() == 1 && component instanceof BreakpointsGroupNode &&
            ((BreakpointsGroupNode)component).getGroup() instanceof XBreakpointCustomGroup) {
          res.add(new SetAsDefaultGroupAction((XBreakpointCustomGroup)((BreakpointsGroupNode)component).getGroup()));
        }
        if (tree.getSelectionCount() == 1 && component instanceof BreakpointItemNode) {
          res.add(new EditDescriptionAction((XBreakpointBase)((BreakpointItemNode)component).getBreakpointItem().getBreakpoint()));
        }
        return res.toArray(new AnAction[res.size()]);
      }
    }, ActionPlaces.UNKNOWN, ActionManager.getInstance());

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
        navigate(true);
        close(OK_EXIT_CODE);
      }
    }.registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE).getShortcutSet(), tree);

    final DefaultActionGroup breakpointTypes = new DefaultActionGroup();
    for (XBreakpointType<?, ?> type : XBreakpointUtil.getBreakpointTypes()) {
      if (type.isAddBreakpointButtonVisible()) {
        breakpointTypes.addAll(new AddXBreakpointAction(type));
      }
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
        myDetailController.doUpdateDetailView(true);
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
    Collections.sort(myRulesAvailable, XBreakpointGroupingRule.PRIORITY_COMPARATOR);

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
    disposeItems();
    super.dispose();
  }

  private void disposeItems() {
    for (BreakpointItem item : myBreakpointItems) {
      item.dispose();
    }
  }

  @Nullable
  @Override
  protected String getHelpId() {
    return "reference.dialogs.breakpoints";
  }

  private void saveCurrentItem() {
    ItemWrapper item = myDetailController.getSelectedItem();
    if (item instanceof BreakpointItem) {
      ((BreakpointItem)item).saveState();
    }
  }

  private class AddXBreakpointAction extends AnAction {
    private final XBreakpointType<?, ?> myType;

    public AddXBreakpointAction(XBreakpointType<?, ?> type) {
      myType = type;
      getTemplatePresentation().setIcon(type.getEnabledIcon());
      getTemplatePresentation().setText(type.getTitle());
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      saveCurrentItem();
      XBreakpoint<?> breakpoint = myType.addBreakpoint(myProject, null);
      if (breakpoint != null) {
        selectBreakpoint(breakpoint);
      }
    }
  }

  private boolean selectBreakpoint(Object breakpoint) {
    for (BreakpointItem item : myBreakpointItems) {
      if (item.getBreakpoint() == breakpoint) {
        myTreeController.selectBreakpointItem(item, null);
        return true;
      }
    }
    return false;
  }

  private class MoveToGroupAction extends AnAction {
    private final String myGroup;
    private final boolean myNewGroup;

    private MoveToGroupAction(String group) {
      super(group == null ? "<no group>" : group);
      myGroup = group;
      myNewGroup = false;
    }

    private MoveToGroupAction() {
      super("Create new...");
      myNewGroup = true;
      myGroup = null;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      String groupName = myGroup;
      if (myNewGroup) {
        groupName = Messages.showInputDialog("New group name", "New Group", AllIcons.Nodes.NewFolder);
        if (groupName == null) {
          return;
        }
      }
      for (BreakpointItem item : myTreeController.getSelectedBreakpoints()) {
        Object breakpoint = item.getBreakpoint();
        if (breakpoint instanceof XBreakpointBase) {
          ((XBreakpointBase)breakpoint).setGroup(groupName);
        }
      }
      myTreeController.rebuildTree(myBreakpointItems);
    }
  }

  private class SetAsDefaultGroupAction extends AnAction {
    private final String myName;

    private SetAsDefaultGroupAction(XBreakpointCustomGroup group) {
      super(group.isDefault() ? "Unset as default" : "Set as default");
      myName = group.isDefault() ? null : group.getName();
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      getBreakpointManager().setDefaultGroup(myName);
      myTreeController.rebuildTree(myBreakpointItems);
    }
  }

  private class EditDescriptionAction extends AnAction {
    private final XBreakpointBase myBreakpoint;

    private EditDescriptionAction(XBreakpointBase breakpoint) {
      super("Edit description");
      myBreakpoint = breakpoint;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      String description = Messages.showInputDialog("", "Edit Description", null, myBreakpoint.getUserDescription(), null);
      if (description == null) {
        return;
      }
      myBreakpoint.setUserDescription(description);
      myTreeController.rebuildTree(myBreakpointItems);
    }
  }
}
