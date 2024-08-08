// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.frame;

import com.intellij.execution.ui.UIExperiment;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.dnd.DnDEvent;
import com.intellij.ide.dnd.DnDManager;
import com.intellij.ide.dnd.DnDNativeTarget;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.CompositeDisposable;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FocusChangeListener;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.*;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.util.SingleEdtTaskScheduler;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.XDebuggerManagerImpl;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.impl.XDebuggerWatchesManager;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import com.intellij.xdebugger.impl.evaluate.DebuggerEvaluationStatisticsCollector;
import com.intellij.xdebugger.impl.evaluate.XDebuggerEvaluationDialog;
import com.intellij.xdebugger.impl.frame.actions.XToggleEvaluateExpressionFieldAction;
import com.intellij.xdebugger.impl.inline.InlineWatch;
import com.intellij.xdebugger.impl.inline.InlineWatchNode;
import com.intellij.xdebugger.impl.inline.InlineWatchesRootNode;
import com.intellij.xdebugger.impl.inline.XInlineWatchesView;
import com.intellij.xdebugger.impl.ui.*;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.actions.XWatchTransferable;
import com.intellij.xdebugger.impl.ui.tree.nodes.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.*;
import java.util.List;
import java.util.*;

@ApiStatus.Internal
public class XWatchesViewImpl extends XVariablesView implements DnDNativeTarget, XWatchesView, XInlineWatchesView {
  private static final JBColor EVALUATE_FIELD_BACKGROUND_COLOR =
    JBColor.namedColor("Debugger.EvaluateExpression.background", new JBColor(0xFFFFFF, 0x45494A));

  protected WatchesRootNode myRootNode;
  private XDebuggerExpressionComboBox myEvaluateComboBox;

  private final CompositeDisposable myDisposables = new CompositeDisposable();
  private final boolean myWatchesInVariables;
  private final boolean inlineWatchesEnabled;

  public XWatchesViewImpl(@NotNull XDebugSessionImpl session, boolean watchesInVariables) {
    this(session, watchesInVariables, watchesInVariables);
  }

  protected XWatchesViewImpl(@NotNull XDebugSessionImpl session, boolean watchesInVariables, boolean vertical) {
    this(session, watchesInVariables, vertical, true);
  }

  public XWatchesViewImpl(@NotNull XDebugSessionImpl session, boolean watchesInVariables, boolean vertical, boolean withToolbar) {
    super(session);
    myWatchesInVariables = watchesInVariables;
    inlineWatchesEnabled = Registry.is("debugger.watches.inline.enabled");

    XDebuggerTree tree = getTree();
    createNewRootNode(null);

    DebuggerUIUtil.registerActionOnComponent(XDebuggerActions.XNEW_WATCH, tree, myDisposables);
    DebuggerUIUtil.registerActionOnComponent(XDebuggerActions.XREMOVE_WATCH, tree, myDisposables);
    DebuggerUIUtil.registerActionOnComponent(XDebuggerActions.XCOPY_WATCH, tree, myDisposables);
    DebuggerUIUtil.registerActionOnComponent(XDebuggerActions.XEDIT_WATCH, tree, myDisposables);

    ActionUtil.wrap(XDebuggerActions.XNEW_WATCH).registerCustomShortcutSet(CommonShortcuts.getNew(), tree);
    ActionUtil.wrap(XDebuggerActions.XREMOVE_WATCH).registerCustomShortcutSet(CommonShortcuts.getDelete(), tree);

    DnDManager.getInstance().registerTarget(this, tree);

    new AnAction() {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        Object contents = CopyPasteManager.getInstance().getContents(XWatchTransferable.EXPRESSIONS_FLAVOR);
        if (contents instanceof List) {
          for (Object item : ((List)contents)){
            if (item instanceof XExpression) {
              addWatchExpression(((XExpression)item), -1, true);
            }
          }
        }
      }
    }.registerCustomShortcutSet(CommonShortcuts.getPaste(), tree, myDisposables);

    if (withToolbar) {
      ActionToolbarImpl toolbar = (ActionToolbarImpl)ActionManager.getInstance().createActionToolbar(
        ActionPlaces.DEBUGGER_TOOLBAR,
        DebuggerSessionTabBase.getCustomizedActionGroup(XDebuggerActions.WATCHES_TREE_TOOLBAR_GROUP),
        !vertical);
      toolbar.setBorder(new CustomLineBorder(0, 0,
                                             vertical ? 0 : 1,
                                             vertical ? 1 : 0));
      toolbar.setTargetComponent(tree);
      getPanel().add(toolbar.getComponent(), vertical ? BorderLayout.WEST : BorderLayout.NORTH);
    }

    if (!myWatchesInVariables) {
      getTree().getEmptyText().setText(XDebuggerBundle.message("debugger.no.watches"));
    }
    installEditListeners();

    if (!ApplicationManager.getApplication().isUnitTestMode() && myEvaluateComboBox != null) {
      myEvaluateComboBox.fixEditorNotReleasedFalsePositiveException(session.getProject(), this);
    }
  }

  @Override
  protected JPanel createMainPanel(@NotNull JComponent localsPanelComponent) {
    var top = createTopPanel();
    if (top == null) {
      return super.createMainPanel(localsPanelComponent);
    }
    var layout = localsPanelComponent.getLayout();
    boolean canAddComponentToTheRightOfToolbar = layout instanceof BorderLayout;
    if (canAddComponentToTheRightOfToolbar) {
      var panel = new BorderLayoutPanel()
        .addToCenter(((BorderLayout)layout).getLayoutComponent(BorderLayout.CENTER))
        .addToTop(top);
      localsPanelComponent.add(panel, BorderLayout.CENTER);
      return super.createMainPanel(localsPanelComponent);
    } else {
      return new BorderLayoutPanel()
        .addToCenter(localsPanelComponent)
        .addToTop(top);
    }
  }

  private @Nullable JComponent createTopPanel() {
    //if (UIExperiment.isNewDebuggerUIEnabled()) {
      XDebuggerTree tree = getTree();
      Ref<AnAction> addToWatchesActionRef = new Ref<>();
      XDebuggerEditorsProvider provider = tree.getEditorsProvider();
      if (!provider.isEvaluateExpressionFieldEnabled()) {
        return null;
      }
      myEvaluateComboBox =
        new XDebuggerExpressionComboBox(tree.getProject(), provider, "evaluateExpression", null, false, true) {
          @Override
          protected ComboBox<XExpression> createComboBox(CollectionComboBoxModel<XExpression> model, int width) {
            AnAction addToWatchesAction =
              new DumbAwareAction(ActionsBundle.actionText(XDebuggerActions.ADD_TO_WATCH), null, AllIcons.Debugger.AddToWatch) {
                @Override
                public void actionPerformed(@NotNull AnActionEvent e) {
                  myEvaluateComboBox.saveTextInHistory();
                  addWatchExpression(getExpression(), -1, false);
                  DebuggerEvaluationStatisticsCollector.WATCH_FROM_INLINE_ADD.log(e);
                }

                @Override
                public void update(@NotNull AnActionEvent e) {
                  e.getPresentation().setEnabled(!XDebuggerUtilImpl.isEmptyExpression(getExpression()));
                }

                @Override
                public @NotNull ActionUpdateThread getActionUpdateThread() {
                  return ActionUpdateThread.BGT;
                }
              };
            ActionToolbarImpl toolbar = (ActionToolbarImpl)ActionManager.getInstance()
              .createActionToolbar("DebuggerVariablesEvaluate", new DefaultActionGroup(addToWatchesAction), true);
            addToWatchesActionRef.set(addToWatchesAction);
            toolbar.setOpaque(false);
            toolbar.setReservePlaceAutoPopupIcon(false);
            toolbar.setTargetComponent(tree);
            XDebuggerEmbeddedComboBox<XExpression> comboBox = new XDebuggerEmbeddedComboBox<>(model, width);
            comboBox.setExtension(toolbar);
            return comboBox;
          }

          @Override
          protected void prepareEditor(EditorEx editor) {
            super.prepareEditor(editor);
            editor.setPlaceholder(XDebuggerBundle.message(
              "debugger.evaluate.expression.or.add.a.watch.hint",
              KeymapUtil.getShortcutText(new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), null)),
              KeymapUtil.getShortcutText(new KeyboardShortcut(XDebuggerEvaluationDialog.getAddWatchKeystroke(), null))
            ));
            editor.addFocusListener(new FocusChangeListener() {
              private static final Set<FocusEvent.Cause> myCauses = Set.of(
                FocusEvent.Cause.UNKNOWN,
                FocusEvent.Cause.TRAVERSAL_FORWARD,
                FocusEvent.Cause.TRAVERSAL_BACKWARD
              );

              @Override
              public void focusGained(@NotNull Editor editor, @NotNull FocusEvent event) {
                if (myCauses.contains(event.getCause())) {
                  boolean shouldBeIgnored = myEvaluateComboBox.getComboBox().isPopupVisible();
                  if (!shouldBeIgnored) {
                    DebuggerEvaluationStatisticsCollector.INPUT_FOCUS.log(getTree().getProject());
                  }
                }
              }
            });
          }
        };
      final JComponent editorComponent = myEvaluateComboBox.getEditorComponent();
      editorComponent.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
        .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "enterStroke");
      editorComponent.getActionMap().put("enterStroke", new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          // This listener overrides one from BasicComboBoxUI$Actions
          // Close popup manually instead of default handler
          if (myEvaluateComboBox.getComboBox().isPopupVisible()) {
            myEvaluateComboBox.getComboBox().setPopupVisible(false);
          }
          else {
            addExpressionResultNode();
          }
        }
      });
      editorComponent.setBackground(EVALUATE_FIELD_BACKGROUND_COLOR);

      myEvaluateComboBox.getComboBox().addPopupMenuListener(new PopupMenuListenerAdapter() {
        private int selectedIndexOnPopupOpen = -1;

        @Override
        public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
          selectedIndexOnPopupOpen = myEvaluateComboBox.getComboBox().getSelectedIndex();
          myEvaluateComboBox.requestFocusInEditor();
          DebuggerEvaluationStatisticsCollector.HISTORY_SHOW.log(getTree().getProject());
        }

        @Override
        public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
          if (myEvaluateComboBox.getComboBox().getSelectedIndex() != selectedIndexOnPopupOpen) {
            DebuggerEvaluationStatisticsCollector.HISTORY_CHOOSE.log(getTree().getProject());
          }
        }
      });
      addToWatchesActionRef.get()
        .registerCustomShortcutSet(new CustomShortcutSet(XDebuggerEvaluationDialog.getAddWatchKeystroke()), editorComponent);
      JComponent component = myEvaluateComboBox.getComponent();
      //component.setBackground(tree.getBackground());
      component.setBorder(JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0));
      if (!UIExperiment.isNewDebuggerUIEnabled()) {
        XToggleEvaluateExpressionFieldAction.markAsEvaluateExpressionField(component);
      }
      return component;
    //}
    //return null;
  }

  @Override
  protected void beforeTreeBuild(@NotNull SessionEvent event) {
    if (event != SessionEvent.SETTINGS_CHANGED) {
      myRootNode.removeResultNode();
    }
  }

  private void addExpressionResultNode() {
    XExpression expression = myEvaluateComboBox.getExpression();
    if (!XDebuggerUtilImpl.isEmptyExpression(expression)) {
      myEvaluateComboBox.saveTextInHistory();
      XDebugSession session = getSession(getTree());
      myRootNode.addResultNode(session != null ? session.getCurrentStackFrame() : null, expression);
      DebuggerEvaluationStatisticsCollector.INLINE_EVALUATE.log(getTree().getProject());
    }
  }

  @Override
  protected void buildTreeAndRestoreState(@NotNull XStackFrame stackFrame) {
    super.buildTreeAndRestoreState(stackFrame);
    if (myEvaluateComboBox != null) {
      myEvaluateComboBox.setSourcePosition(stackFrame.getSourcePosition());
    }
  }

  private void installEditListeners() {
    final XDebuggerTree watchTree = getTree();
    SingleEdtTaskScheduler quitePeriod = SingleEdtTaskScheduler.createSingleEdtTaskScheduler();
    SingleEdtTaskScheduler editAlarm = SingleEdtTaskScheduler.createSingleEdtTaskScheduler();
    final ClickListener mouseListener = new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent event, int clickCount) {
        if (!SwingUtilities.isLeftMouseButton(event) ||
            ((event.getModifiers() & (InputEvent.SHIFT_MASK | InputEvent.ALT_MASK | InputEvent.CTRL_MASK | InputEvent.META_MASK)) !=0) ) {
          return false;
        }
        boolean sameRow = isAboveSelectedItem(event, watchTree, false);
        if (!sameRow || clickCount > 1) {
          editAlarm.cancel();
          return false;
        }
        final AnAction editWatchAction = ActionManager.getInstance().getAction(XDebuggerActions.XEDIT_WATCH);
        Presentation presentation = editWatchAction.getTemplatePresentation().clone();
        DataContext context = DataManager.getInstance().getDataContext(watchTree);
        final AnActionEvent actionEvent = new AnActionEvent(null, context, "WATCH_TREE", presentation, ActionManager.getInstance(), 0);
        Runnable runnable = () -> editWatchAction.actionPerformed(actionEvent);
        if (editAlarm.isEmpty() && quitePeriod.isEmpty()) {
          editAlarm.request(UIUtil.getMultiClickInterval(), runnable);
        }
        else {
          editAlarm.cancel();
        }
        return false;
      }
    };
    final ClickListener mouseEmptySpaceListener = new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(@NotNull MouseEvent event) {
        if (!isAboveSelectedItem(event, watchTree, true)) {
          myRootNode.addNewWatch();
          return true;
        }
        return false;
      }
    };
    ListenerUtil.addClickListener(watchTree, mouseListener);
    ListenerUtil.addClickListener(watchTree, mouseEmptySpaceListener);

    final FocusListener focusListener = new FocusListener() {
      @Override
      public void focusGained(@NotNull FocusEvent e) {
        quitePeriod.cancelAndRequest(UIUtil.getMultiClickInterval(), EmptyRunnable.getInstance());
      }

      @Override
      public void focusLost(@NotNull FocusEvent e) {
        editAlarm.cancel();
      }
    };
    ListenerUtil.addFocusListener(watchTree, focusListener);

    final TreeSelectionListener selectionListener = new TreeSelectionListener() {
      @Override
      public void valueChanged(@NotNull TreeSelectionEvent e) {
        quitePeriod.cancelAndRequest(UIUtil.getMultiClickInterval(), EmptyRunnable.getInstance());
      }
    };
    watchTree.addTreeSelectionListener(selectionListener);
    myDisposables.add(new Disposable() {
      @Override
      public void dispose() {
        ListenerUtil.removeClickListener(watchTree, mouseListener);
        ListenerUtil.removeClickListener(watchTree, mouseEmptySpaceListener);
        ListenerUtil.removeFocusListener(watchTree, focusListener);
        watchTree.removeTreeSelectionListener(selectionListener);
      }
    });
  }

  @Override
  public void dispose() {
    Disposer.dispose(myDisposables);
    DnDManager.getInstance().unregisterTarget(this, getTree());
    super.dispose();
  }

  private static boolean isAboveSelectedItem(MouseEvent event, XDebuggerTree watchTree, boolean fullWidth) {
    Rectangle bounds = watchTree.getRowBounds(watchTree.getLeadSelectionRow());
    if (bounds != null) {
      if (fullWidth) {
        bounds.x = 0;
      }
      bounds.width = watchTree.getWidth();
      if (bounds.contains(event.getPoint())) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void addWatchExpression(@NotNull XExpression expression, int index, final boolean navigateToWatchNode) {
    addWatchExpression(expression, index, navigateToWatchNode, false);
  }

  public void addWatchExpression(@NotNull XExpression expression, int index, final boolean navigateToWatchNode, boolean noDuplicates) {
    ThreadingAssertions.assertEventDispatchThread();
    XDebugSession session = getSession(getTree());
    boolean found = false;
    if (noDuplicates) {
      for (WatchNode child : myRootNode.getWatchChildren()) {
        if (child.getExpression().equals(expression)) {
          TreeUtil.selectNode(getTree(), child);
          found = true;
        }
      }
    }
    if (!found) {
      myRootNode.addWatchExpression(session != null ? session.getCurrentStackFrame() : null, expression, index, navigateToWatchNode);
      updateSessionData();
    }
    if (navigateToWatchNode && session != null) {
      XDebugSessionTab.showWatchesView((XDebugSessionImpl)session);
    }
  }

  public void computeWatches() {
    myRootNode.computeWatches();
  }

  @Override
  protected XValueContainerNode doCreateNewRootNode(@Nullable XStackFrame stackFrame) {
    if (inlineWatchesEnabled) {
      myRootNode = new InlineWatchesRootNode(getTree(), this, getExpressions(), getInlineExpressions(), stackFrame, myWatchesInVariables);
    } else {
      myRootNode = new WatchesRootNode(getTree(), this, getExpressions(), stackFrame, myWatchesInVariables);
    }
    return myRootNode;
  }

  @NotNull
  private List<InlineWatch> getInlineExpressions() {
    return getWatchesManager().getInlineWatches();
  }

  private XDebuggerWatchesManager getWatchesManager() {
    return ((XDebuggerManagerImpl)XDebuggerManager.getInstance(getTree().getProject()))
      .getWatchesManager();
  }

  @Override
  public void addInlineWatchExpression(@NotNull InlineWatch watch, int index, boolean navigateToWatchNode) {
    ThreadingAssertions.assertEventDispatchThread();
    XDebugSession session = getSession(getTree());

    ((InlineWatchesRootNode)myRootNode).addInlineWatchExpression(session != null ? session.getCurrentStackFrame() : null, watch, index, navigateToWatchNode);

    if (navigateToWatchNode && session != null) {
      XDebugSessionTab.showWatchesView((XDebugSessionImpl)session);
    }
  }

  @Override
  public void removeInlineWatches(Collection<InlineWatch> watches) {
    InlineWatchesRootNode rootNode = (InlineWatchesRootNode)myRootNode;
    @SuppressWarnings("unchecked")
    List<? extends XDebuggerTreeNode> nodesToRemove =
      (List<? extends XDebuggerTreeNode>)ContainerUtil.filter(rootNode.getInlineWatchChildren(), node -> watches.contains(node.getWatch()));

    if (!nodesToRemove.isEmpty()) {
      removeInlineNodes(nodesToRemove, false);
    }
  }


  private void removeInlineNodes(List<? extends XDebuggerTreeNode> inlineWatches, boolean updateManager) {
    InlineWatchesRootNode rootNode = (InlineWatchesRootNode)myRootNode;
    List<? extends InlineWatchNode> inlineWatchChildren = rootNode.getInlineWatchChildren();
    final int[] minIndex = {Integer.MAX_VALUE};
    List<InlineWatchNode> toRemoveInlines = new ArrayList<>();
    inlineWatches.forEach((node) -> {
      int index = inlineWatchChildren.indexOf(node);
      if (index != -1) {
        toRemoveInlines.add((InlineWatchNode)node);
        minIndex[0] = Math.min(minIndex[0], index);
      }
    });

    rootNode.removeInlineChildren(toRemoveInlines);

    List<? extends InlineWatchNode> newChildren = rootNode.getInlineWatchChildren();
    if (!newChildren.isEmpty()) {
      InlineWatchNode node = newChildren.get(Math.min(minIndex[0], newChildren.size() - 1));
      TreeUtil.selectNode(getTree(), node);
    }
    if (updateManager) {
      getWatchesManager().inlineWatchesRemoved(ContainerUtil.map(toRemoveInlines, node -> node.getWatch()), this);
    }
  }

  @Override
  protected void addEmptyMessage(XValueContainerNode root) {
    if (myWatchesInVariables) {
      super.addEmptyMessage(root);
    }
  }

  @NotNull
  protected List<XExpression> getExpressions() {
    XDebuggerTree tree = getTree();
    XDebugSession session = getSession(tree);
    List<XExpression> expressions;
    if (session != null) {
      expressions = ((XDebugSessionImpl)session).getSessionData().getWatchExpressions();
    }
    else {
      XDebuggerTreeNode root = tree.getRoot();
      expressions = root instanceof WatchesRootNode ? ((WatchesRootNode)root).getWatchExpressions() : Collections.emptyList();
    }
    return expressions;
  }

  @Override
  protected void uiDataSnapshot(@NotNull DataSink sink) {
    super.uiDataSnapshot(sink);
    sink.set(XWatchesView.DATA_KEY, this);
  }

  @Override
  public void removeWatches(List<? extends XDebuggerTreeNode> nodes) {
    ThreadingAssertions.assertEventDispatchThread();

    List<? extends XDebuggerTreeNode> ordinaryWatches = ContainerUtil.filter(nodes, node -> !(node instanceof InlineWatchNode));
    List<? extends XDebuggerTreeNode> inlineWatches = ContainerUtil.filter(nodes, node -> node instanceof InlineWatchNode);
    if (!inlineWatches.isEmpty()) {
      removeInlineNodes(inlineWatches, true);
    }
    if (ordinaryWatches.isEmpty()) return;

    List<? extends WatchNode> children = myRootNode.getWatchChildren();
    int minIndex = Integer.MAX_VALUE;
    List<XDebuggerTreeNode> toRemove = new ArrayList<>();
    for (XDebuggerTreeNode node : ordinaryWatches) {
      int index = children.indexOf(node);
      if (index != -1) {
        toRemove.add(node);
        minIndex = Math.min(minIndex, index);
      }
    }
    myRootNode.removeChildren(toRemove);

    List<? extends WatchNode> newChildren = myRootNode.getWatchChildren();
    if (!newChildren.isEmpty()) {
      WatchNode node = newChildren.get(Math.min(minIndex, newChildren.size() - 1));
      TreeUtil.selectNode(getTree(), node);
    }
    updateSessionData();
  }

  @Override
  public void removeAllWatches() {
    ThreadingAssertions.assertEventDispatchThread();
    if (inlineWatchesEnabled) {
      List<? extends InlineWatchNode> children = ((InlineWatchesRootNode)myRootNode).getInlineWatchChildren();
      if (!children.isEmpty()) {
        //noinspection unchecked
        removeInlineNodes((List<? extends XDebuggerTreeNode>)children, true);
      }
    }
    myRootNode.removeAllChildren();
    updateSessionData();
  }

  public void moveWatchUp(WatchNode node) {
    myRootNode.moveUp(node);
    updateSessionData();
  }

  public void moveWatchDown(WatchNode node) {
    myRootNode.moveDown(node);
    updateSessionData();
  }

  public void updateSessionData() {
    List<XExpression> watchExpressions = myRootNode.getWatchExpressions();
    XDebugSession session = getSession(getTree());
    XDebugSessionData data = (session != null) ? ((XDebugSessionImpl)session).getSessionData()
                                               : getData(XDebugSessionData.DATA_KEY, getTree());
    if (data != null) {
      data.setWatchExpressions(watchExpressions);
      getWatchesManager().setWatches(data.getConfigurationName(), watchExpressions);
    }
  }

  @Override
  public boolean update(final DnDEvent aEvent) {
    Object object = aEvent.getAttachedObject();
    boolean possible = false;
    if (object instanceof XValueNodeImpl[]) {
      possible = true;
      // do not add new watch if node is dragged to itself
      if (((XValueNodeImpl[])object).length == 1) {
        Point point = aEvent.getPoint();
        XDebuggerTree tree = getTree();
        TreePath path = tree.getClosestPathForLocation(point.x, point.y);
        if (path != null && path.getLastPathComponent() == ((XValueNodeImpl[])object)[0]) {
          // the same item is under pointer, filter out place below the tree
          Rectangle pathBounds = tree.getPathBounds(path);
          possible = pathBounds != null && pathBounds.y + pathBounds.height < point.y;
        }
      }
    }
    else if (object instanceof EventInfo) {
      possible = ((EventInfo)object).getTextForFlavor(DataFlavor.stringFlavor) != null;
    }

    aEvent.setDropPossible(possible, XDebuggerBundle.message("xdebugger.drop.text.add.to.watches"));

    return true;
  }

  @Override
  public void drop(DnDEvent aEvent) {
    Object object = aEvent.getAttachedObject();
    if (object instanceof XValueNodeImpl[]) {
      for (XValueNodeImpl node : (XValueNodeImpl[])object) {
        DebuggerUIUtil.addToWatches(this, node);
      }
    }
    else if (object instanceof EventInfo) {
      String text = ((EventInfo)object).getTextForFlavor(DataFlavor.stringFlavor);
      if (text != null) {
        addWatchExpression(XExpressionImpl.fromText(text), -1, false);
      }
    }
  }
}
