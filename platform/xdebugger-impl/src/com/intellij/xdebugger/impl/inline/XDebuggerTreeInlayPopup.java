// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.inline;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionButtonLook;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.ui.speedSearch.SpeedSearchSupply;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.util.ui.tree.TreeModelAdapter;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.XDebuggerManagerImpl;
import com.intellij.xdebugger.impl.XDebuggerWatchesManager;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.evaluate.quick.XDebuggerTreeCreator;
import com.intellij.xdebugger.impl.evaluate.quick.common.DebuggerTreeCreator;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeListener;
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueContainerNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promises;

import javax.swing.*;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.Collections;
import java.util.List;

import static com.intellij.xdebugger.impl.ui.DebuggerSessionTabBase.getCustomizedActionGroup;

public class XDebuggerTreeInlayPopup<D> {
  private static final Logger LOG = Logger.getInstance(XDebuggerTreeInlayPopup.class);
  protected final DebuggerTreeCreator<D> myTreeCreator;
  private final XSourcePosition myPresentationPosition;
  @NotNull protected final XDebugSession mySession;
  @NonNls private final static String DIMENSION_SERVICE_KEY = "DebuggerActiveHint";
  private JBPopup myPopup;
  private final Editor myEditor;
  private final Point myPoint;
  @Nullable private final Runnable myHideRunnable;
  private XValueNodeImpl myValueNode;
  private ActionToolbar myToolbar;

  private XDebuggerTreeInlayPopup(@NotNull DebuggerTreeCreator<D> creator,
                                  @NotNull Editor editor,
                                  @NotNull Point point,
                                  @NotNull XSourcePosition presentationPosition,
                                  @NotNull XDebugSession session,
                                  @Nullable Runnable hideRunnable,
                                  @NotNull XValueNodeImpl valueNode) {
    myTreeCreator = creator;
    myPresentationPosition = presentationPosition;
    mySession = session;
    myEditor = editor;
    myPoint = point;
    myHideRunnable = hideRunnable;
    myValueNode = valueNode;
  }

  protected BorderLayoutPanel createMainPanel(Tree tree) {
    return fillMainPanel(JBUI.Panels.simplePanel(), tree);
  }

  protected BorderLayoutPanel fillMainPanel(BorderLayoutPanel mainPanel, Tree tree) {
    return mainPanel.addToCenter(ScrollPaneFactory.createScrollPane(tree)).addToBottom(createToolbar(mainPanel, tree));
  }

  protected void updateTree(@NotNull D selectedItem) {
    updateContainer(myTreeCreator.createTree(selectedItem));
  }

  private JComponent createToolbar(JPanel parent, Tree tree) {
    DefaultActionGroup toolbarGroup = new DefaultActionGroup();
    toolbarGroup.addAll(getCustomizedActionGroup(XDebuggerActions.WATCHES_INLINE_POPUP_GROUP));
    AnAction watchAction = myValueNode instanceof InlineWatchNodeImpl
                           ? new EditInlineWatch()
                           : new AddInlineWatch();

    toolbarGroup.add(watchAction, Constraints.LAST);

    myToolbar = new ActionToolbarImpl("XDebuggerTreeInlayPopup", toolbarGroup, true) {
      @Override
      protected @NotNull ActionButton createToolbarButton(@NotNull AnAction action,
                                                          ActionButtonLook look,
                                                          @NotNull String place,
                                                          @NotNull Presentation presentation,
                                                          @NotNull Dimension minimumSize) {
        return new ActionButtonWithText(action, presentation, place, minimumSize);
      }
    };

    return myToolbar.getComponent();
  }

  private class AddInlineWatch extends XDebuggerTreeActionBase {

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setIcon(AllIcons.Debugger.AddToWatch);
      e.getPresentation().setText(XDebuggerBundle.message("debugger.inline.watches.popup.action.add.as.inline.watch"));
      super.update(e);
    }

    @Override
    protected void perform(XValueNodeImpl node, @NotNull String nodeName, AnActionEvent e) {
      node.getValueContainer().calculateEvaluationExpression()
        .thenAsync(expr -> {
          if (expr == null && node != myValueNode) {
            return myValueNode.getValueContainer().calculateEvaluationExpression();
          } else {
            return Promises.resolvedPromise(expr);
          }
        }).onSuccess(expr -> {
        AppUIUtil.invokeOnEdt(() -> {
          XDebuggerWatchesManager manager = ((XDebuggerManagerImpl)XDebuggerManager.getInstance(mySession.getProject())).getWatchesManager();
          manager.showInplaceEditor(myPresentationPosition, myEditor, mySession, expr);
        });
      });
    }
  }

  private class EditInlineWatch extends AnAction {

    EditInlineWatch() {
      super(XDebuggerBundle.message("debugger.inline.watches.edit.watch.expression.text"));
      ActionUtil.mergeFrom(this, "XDebugger.SetValue");
    }

    @Override
    public boolean displayTextInToolbar() {
      return true;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      InlineWatchNodeImpl watch = (InlineWatchNodeImpl)myValueNode;
      XDebuggerWatchesManager watchesManager = ((XDebuggerManagerImpl)XDebuggerManager.getInstance(mySession.getProject())).getWatchesManager();
      XDebugSession session = e.getData(XDebugSession.DATA_KEY);
      if (session == null) {
        Project project = e.getProject();
        if (project != null) {
          session = XDebuggerManager.getInstance(project).getCurrentSession();
        }
      }
      if (session != null) {
        myPopup.cancel();
        watchesManager.inlineWatchesRemoved(Collections.singletonList(watch.getWatch()), null);
        watchesManager.showInplaceEditor(watch.getPosition(), myEditor, session, watch.getExpression());
      }
    }

  }

  protected static void registerTreeDisposable(Disposable disposable, Tree tree) {
    if (tree instanceof Disposable) {
      Disposer.register(disposable, (Disposable)tree);
    }
  }

  public static <D> void showTreePopup(XDebuggerTreeCreator creator,
                                       Pair<XValue, String> initialItem,
                                       XValueNodeImpl valueNode,
                                       @NotNull Editor editor,
                                       @NotNull Point point,
                                       @NotNull XSourcePosition position,
                                       @NotNull XDebugSession session,
                                       Runnable hideRunnable) {
    new XDebuggerTreeInlayPopup<>(creator, editor, point, position, session, hideRunnable, valueNode).updateTree(initialItem);
  }

  private TreeModelListener createTreeListener(final Tree tree) {
    return new TreeModelAdapter() {
      @Override
      public void treeStructureChanged(TreeModelEvent e) {
        resize(e.getTreePath(), tree);
      }
    };
  }

  protected void updateContainer(final Tree tree) {
    if (myPopup != null) {
      myPopup.cancel();
    }
    tree.getModel().addTreeModelListener(createTreeListener(tree));
    myPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(createMainPanel(tree), tree)
      .setRequestFocus(true)
      .setResizable(true)
      .setMovable(true)
      .setDimensionServiceKey(mySession.getProject(), DIMENSION_SERVICE_KEY, false)
      .setMayBeParent(true)
      .setCancelOnOtherWindowOpen(true)
      .setKeyEventHandler(event -> {
        if (AbstractPopup.isCloseRequest(event)) {
          // Do not process a close request if the tree shows a speed search popup
          SpeedSearchSupply supply = SpeedSearchSupply.getSupply(tree);
          return supply != null && StringUtil.isEmpty(supply.getEnteredPrefix());
        }
        return false;
      })
      .addListener(new JBPopupListener() {
        @Override
        public void onClosed(@NotNull LightweightWindowEvent event) {
          if (myHideRunnable != null) {
            myHideRunnable.run();
          }
        }
      })
      .setCancelCallback(() -> {
        Window parent = SwingUtilities.getWindowAncestor(tree);
        if (parent != null) {
          for (Window child : parent.getOwnedWindows()) {
            if (child.isShowing()) {
              return false;
            }
          }
        }
        return true;
      })
      .createPopup();

    registerTreeDisposable(myPopup, tree);

    //Editor may be disposed before later invokator process this action
    if (myEditor.getComponent().getRootPane() == null) {
      myPopup.cancel();
      return;
    }
    myPopup.show(new RelativePoint(myEditor.getContentComponent(), myPoint));

    ((XDebuggerTree)tree).addTreeListener(new XDebuggerTreeListener() {
      @Override
      public void childrenLoaded(@NotNull XDebuggerTreeNode node,
                                 @NotNull List<? extends XValueContainerNode<?>> children,
                                 boolean last) {
        if (last) {
          updateInitialBounds(tree);
          ((XDebuggerTree)tree).removeTreeListener(this);
        }
      }
    });

    updateInitialBounds(tree);
  }

  private void resize(final TreePath path, JTree tree) {
    if (myPopup == null || !myPopup.isVisible() || myPopup.isDisposed()) return;
    final Window popupWindow = SwingUtilities.windowForComponent(myPopup.getContent());
    if (popupWindow == null) return;
    final Dimension size = tree.getPreferredSize();
    final Point location = popupWindow.getLocation();
    final Rectangle windowBounds = popupWindow.getBounds();
    final Rectangle bounds = tree.getPathBounds(path);
    if (bounds == null) return;

    final Rectangle targetBounds = new Rectangle(location.x,
                                                 location.y,
                                                 Math.max(Math.max(size.width, bounds.width) + 20, windowBounds.width),
                                                 Math.max(tree.getRowCount() * bounds.height + 55, windowBounds.height));
    ScreenUtil.cropRectangleToFitTheScreen(targetBounds);
    popupWindow.setBounds(targetBounds);
    popupWindow.validate();
    popupWindow.repaint();
  }

  private void updateInitialBounds(final Tree tree) {
    final Window popupWindow = SwingUtilities.windowForComponent(myPopup.getContent());
    final Dimension size = tree.getPreferredSize();
    final Point location = popupWindow.getLocation();
    final Rectangle windowBounds = popupWindow.getBounds();
    int width = Math.max(size.width, myToolbar.getComponent().getPreferredSize().width) + 150;
    int maxWidth = 600;
    final Rectangle targetBounds = new Rectangle(location.x,
                                                 location.y,
                                                 Math.min(width, maxWidth),
                                                 Math.min(tree.getRowCount(), 12) * tree.getRowHeight() + 75);

    ScreenUtil.cropRectangleToFitTheScreen(targetBounds);
    popupWindow.setBounds(targetBounds);
    popupWindow.validate();
    popupWindow.repaint();
  }
}
