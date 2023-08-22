// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.treeView.TreeState;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.*;
import com.intellij.ui.popup.util.DetailController;
import com.intellij.ui.popup.util.DetailViewImpl;
import com.intellij.ui.popup.util.ItemWrapper;
import com.intellij.ui.popup.util.MasterController;
import com.intellij.ui.tree.TreeVisitor;
import com.intellij.util.SingleAlarm;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.xdebugger.XDebuggerBundle;
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
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.List;
import java.util.*;

public class BreakpointsDialog extends DialogWrapper {
  @NotNull private final Project myProject;

  private final Object myInitialBreakpoint;
  private final List<BreakpointPanelProvider> myBreakpointsPanelProviders;

  private BreakpointItemsTreeController myTreeController;

  final JLabel temp = new JLabel();

  private final MasterController myMasterController = new MasterController() {
    @Override
    public ItemWrapper[] getSelectedItems() {
      final List<BreakpointItem> res = myTreeController.getSelectedBreakpoints(false);
      return res.toArray(new ItemWrapper[0]);
    }

    @Override
    public JLabel getPathLabel() {
      return temp;
    }
  };

  private final DetailController myDetailController = new DetailController(myMasterController);

  private final Collection<BreakpointItem> myBreakpointItems = new ArrayList<>();

  private final SingleAlarm myRebuildAlarm = new SingleAlarm(() -> updateBreakpoints(), 100, myDisposable);

  private void updateBreakpoints() {
    collectItems();
    myTreeController.rebuildTree(myBreakpointItems);
    myDetailController.doUpdateDetailView(true);
  }

  private final List<XBreakpointGroupingRule> myRulesAvailable = new ArrayList<>();

  private final Set<XBreakpointGroupingRule> myRulesEnabled = new TreeSet<>(XBreakpointGroupingRule.PRIORITY_COMPARATOR);
  private final Disposable myListenerDisposable = Disposer.newDisposable();
  private final List<ToggleActionButton> myToggleRuleActions = new ArrayList<>();

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

    setTitle(XDebuggerBundle.message("xbreakpoints.dialog.title"));
    setModal(false);
    init();
    setOKButtonText(XDebuggerBundle.message("done.action.text"));
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
    detailView.setEmptyLabel(XDebuggerBundle.message("xbreakpoint.label.empty"));
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
    XBreakpointsDialogState settings = (getBreakpointManager()).getBreakpointsDialogSettings();
    if (settings != null && settings.getTreeState() != null) {
      settings.getTreeState().applyTo(myTreeController.getTreeView());
      if (myTreeController.getTreeView().getSelectionCount() == 0) {
        myTreeController.selectFirstBreakpointItem();
      }
    }
    else {
      TreeUtil.expand(myTreeController.getTreeView(),
        path -> {
          Object lastPathComponent = path.getLastPathComponent();
          if (!(lastPathComponent instanceof BreakpointsGroupNode)) {
            return TreeVisitor.Action.CONTINUE;
          }
          return ((BreakpointsGroupNode<?>) lastPathComponent).getGroup().expandedByDefault() ?
            TreeVisitor.Action.CONTINUE :
            TreeVisitor.Action.SKIP_CHILDREN;
        },
        treePath -> {
        });
      myTreeController.selectFirstBreakpointItem();
    }
    selectBreakpoint(myInitialBreakpoint, false);
  }

  @Nullable
  @Override
  protected String getDimensionServiceKey() {
    return getClass().getName();
  }

  @Override
  protected Action @NotNull [] createActions() {
    return new Action[]{getOKAction(), getHelpAction()};
  }

  private class ToggleBreakpointGroupingRuleEnabledAction extends ToggleActionButton {
    private final XBreakpointGroupingRule myRule;

    ToggleBreakpointGroupingRuleEnabledAction(XBreakpointGroupingRule rule) {
      super(rule.getPresentableName(), rule.getIcon());
      myRule = rule;
      getTemplatePresentation().setText(rule.getPresentableName());
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
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
    BreakpointsCheckboxTree tree = new BreakpointsCheckboxTree(myProject, myTreeController) {
      @Override
      protected void onDoubleClick(CheckedTreeNode node) {
        if (node instanceof BreakpointsGroupNode) {
          TreePath path = TreeUtil.getPathFromRoot(node);
          if (isExpanded(path)) {
            collapsePath(path);
          }
          else {
            expandPath(path);
          }
        }
        else {
          navigate(false);
        }
      }
    };

    tree.setHorizontalAutoScrollingEnabled(false);
    PopupHandler.installPopupMenu(tree, new ActionGroup() {
      @Override
      public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
        ActionGroup group = new ActionGroup(XDebuggerBundle.message("move.to.group"), true) {
          @Override
          public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
            Set<String> groups = getBreakpointManager().getAllGroups();
            AnAction[] res = new AnAction[groups.size() + 3];
            int i = 0;
            res[i++] = new MoveToGroupAction(null);
            for (@NlsSafe String group : groups) {
              res[i++] = new MoveToGroupAction(group);
            }
            res[i++] = new Separator();
            res[i] = new MoveToGroupAction();
            return res;
          }
        };
        List<AnAction> res = new ArrayList<>();
        res.add(group);
        Object component = tree.getLastSelectedPathComponent();
        if (tree.getSelectionCount() == 1 && component instanceof BreakpointsGroupNode &&
            ((BreakpointsGroupNode<?>)component).getGroup() instanceof XBreakpointCustomGroup) {
          res.add(new SetAsDefaultGroupAction((XBreakpointCustomGroup)((BreakpointsGroupNode<?>)component).getGroup()));
        }
        if (tree.getSelectionCount() == 1 && component instanceof BreakpointItemNode) {
          res.add(new EditDescriptionAction((XBreakpointBase)((BreakpointItemNode)component).getBreakpointItem().getBreakpoint()));
        }
        return res.toArray(AnAction.EMPTY_ARRAY);
      }
    }, "BreakpointTreePopup");

    new AnAction(XDebuggerBundle.messagePointer("action.Anonymous.text.breakpointdialog.gotosource")) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        navigate(true);
        close(OK_EXIT_CODE);
      }
    }.registerCustomShortcutSet(CommonShortcuts.ENTER, tree, myDisposable);

    new AnAction(XDebuggerBundle.messagePointer("action.Anonymous.text.breakpointdialog.showsource")) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        navigate(true);
        close(OK_EXIT_CODE);
      }
    }.registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE).getShortcutSet(), tree, myDisposable);

    DefaultActionGroup breakpointTypes = XBreakpointUtil.breakpointTypes()
      .filter(XBreakpointType::isAddBreakpointButtonVisible)
      .map(AddXBreakpointAction::new)
      .toListAndThen(DefaultActionGroup::new);

    ToolbarDecorator decorator = ToolbarDecorator.createDecorator(tree).
      setToolbarPosition(ActionToolbarPosition.TOP).
      setPanelBorder(JBUI.Borders.empty()).
      setAddAction(button -> JBPopupFactory.getInstance()
        .createActionGroupPopup(null, breakpointTypes, DataManager.getInstance().getDataContext(button.getContextComponent()),
                                JBPopupFactory.ActionSelectionAid.NUMBERING, false)
        .show(button.getPreferredPopupPoint())).
      setRemoveAction(button -> myTreeController.removeSelectedBreakpoints(myProject)).
      setRemoveActionUpdater(e -> {
        for (BreakpointItem item : myTreeController.getSelectedBreakpoints(true)) {
          if (item.allowedToRemove()) {
            return true;
          }
        }
        return false;
      });
    myToggleRuleActions.forEach(decorator::addExtraAction);
    JPanel decoratorPanel = decorator.createPanel();

    myTreeController.setTreeView(tree);

    myTreeController.buildTree(myBreakpointItems);

    initSelection(myBreakpointItems);

    myBreakpointsPanelProviders.forEach(provider -> provider.addListener(myRebuildAlarm::cancelAndRequest, myProject, myListenerDisposable));

    return decoratorPanel;
  }

  private void navigate(final boolean requestFocus) {
    myTreeController.getSelectedBreakpoints(false).stream().findFirst().ifPresent(b -> b.navigate(requestFocus));
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myTreeController.getTreeView();
  }

  private void collectGroupingRules() {
    myRulesAvailable.addAll(XBreakpointGroupingRule.EP.getExtensionList());
    for (BreakpointPanelProvider provider : myBreakpointsPanelProviders) {
      provider.createBreakpointsGroupingRules(myRulesAvailable);
    }
    myRulesAvailable.sort(XBreakpointGroupingRule.PRIORITY_COMPARATOR);

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
    saveTreeState(dialogState);
    final List<XBreakpointGroupingRule> rulesEnabled = ContainerUtil.filter(myRulesEnabled, rule -> !rule.isAlwaysEnabled());

    dialogState.setSelectedGroupingRules(new HashSet<>(ContainerUtil.map(rulesEnabled, XBreakpointGroupingRule::getId)));
    getBreakpointManager().setBreakpointsDialogSettings(dialogState);
  }

  private void saveTreeState(XBreakpointsDialogState state) {
    JTree tree = myTreeController.getTreeView();
    state.setTreeState(TreeState.createOn(tree, (DefaultMutableTreeNode)tree.getModel().getRoot()));
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
    myBreakpointItems.forEach(BreakpointItem::dispose);
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

  private class AddXBreakpointAction extends AnAction implements DumbAware {
    private final XBreakpointType<?, ?> myType;

    AddXBreakpointAction(XBreakpointType<?, ?> type) {
      myType = type;
      getTemplatePresentation().setIcon(type.getEnabledIcon());
      getTemplatePresentation().setText(type.getTitle());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      saveCurrentItem();
      XBreakpoint<?> breakpoint = myType.addBreakpoint(myProject, null);
      if (breakpoint != null) {
        selectBreakpoint(breakpoint, true);
      }
    }
  }

  @Override
  public void toFront() {
    Window window = getWindow();
    if (window != null) {
      window.setBounds(window.getBounds()); // will force fit to screen
    }
    super.toFront();
  }

  public boolean selectBreakpoint(Object breakpoint, boolean update) {
    if (update) {
      updateBreakpoints();
    }
    if (breakpoint != null) {
      for (BreakpointItem item : myBreakpointItems) {
        if (item.getBreakpoint() == breakpoint) {
          myTreeController.selectBreakpointItem(item, null);
          return true;
        }
      }
    }
    return false;
  }

  private final class MoveToGroupAction extends AnAction {
    private final String myGroup;
    private final boolean myNewGroup;

    private MoveToGroupAction(@NlsActions.ActionText String group) {
      super(group == null ? XDebuggerBundle.message("breakpoints.dialog.no.group") : group);
      myGroup = group;
      myNewGroup = false;
    }

    private MoveToGroupAction() {
      super(XDebuggerBundle.message("breakpoints.dialog.create.new.group"));
      myNewGroup = true;
      myGroup = null;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      String groupName = myGroup;
      if (myNewGroup) {
        groupName = Messages.showInputDialog(XDebuggerBundle.message("breakpoints.dialog.new.group.name"),
                                             XDebuggerBundle.message("breakpoints.dialog.new.group"), AllIcons.Nodes.Folder);
        if (groupName == null) {
          return;
        }
      }
      for (BreakpointItem item : myTreeController.getSelectedBreakpoints(true)) {
        Object breakpoint = item.getBreakpoint();
        if (breakpoint instanceof XBreakpointBase) {
          ((XBreakpointBase<?, ?, ?>)breakpoint).setGroup(groupName);
        }
      }
      myTreeController.rebuildTree(myBreakpointItems);
    }
  }

  private final class SetAsDefaultGroupAction extends AnAction {
    private final String myName;

    private SetAsDefaultGroupAction(XBreakpointCustomGroup group) {
      super(group.isDefault()
            ? XDebuggerBundle.message("breakpoints.dialog.unset.as.default")
            : XDebuggerBundle.message("breakpoints.dialog.set.as.default"));
      myName = group.isDefault() ? null : group.getName();
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      getBreakpointManager().setDefaultGroup(myName);
      myTreeController.rebuildTree(myBreakpointItems);
    }
  }

  private final class EditDescriptionAction extends AnAction {
    private final XBreakpointBase myBreakpoint;

    private EditDescriptionAction(XBreakpointBase breakpoint) {
      super(XDebuggerBundle.message("breakpoints.dialog.edit.description"));
      myBreakpoint = breakpoint;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      String description = Messages.showInputDialog(
        "", XDebuggerBundle.message("breakpoints.dialog.edit.description"), null, myBreakpoint.getUserDescription(), null);
      if (description == null) {
        return;
      }
      myBreakpoint.setUserDescription(description);
      myTreeController.rebuildTree(myBreakpointItems);
    }
  }
}
