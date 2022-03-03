// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.evaluate.quick.common;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SpeedSearchBase;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.ui.speedSearch.SpeedSearchSupply;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeModelAdapter;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeListener;
import com.intellij.xdebugger.impl.ui.tree.actions.XSetValueAction;
import com.intellij.xdebugger.impl.ui.tree.nodes.RestorableStateNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueContainerNode;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.util.List;

import static com.intellij.xdebugger.impl.ui.DebuggerSessionTabBase.getCustomizedActionGroup;

@ApiStatus.Experimental
public class XDebuggerTreePopup<D> extends XDebuggerPopupPanel {
  public static final String ACTION_PLACE = "XDebuggerTreePopup";
  private final static @NonNls String DIMENSION_SERVICE_KEY = "DebuggerActiveHint";

  protected final @NotNull DebuggerTreeCreator<D> myTreeCreator;
  protected final @NotNull Project myProject;
  protected final @NotNull Editor myEditor;
  protected final @NotNull Point myPoint;
  protected final @Nullable Runnable myHideRunnable;
  protected @Nullable JBPopup myPopup;
  private boolean mySetValueModeEnabled = false;

  @ApiStatus.Experimental
  public XDebuggerTreePopup(@NotNull DebuggerTreeCreator<D> creator,
                            @NotNull Editor editor,
                            @NotNull Point point,
                            @NotNull Project project,
                            @Nullable Runnable hideRunnable) {
    super();
    myTreeCreator = creator;
    myProject = project;
    myEditor = editor;
    myPoint = point;
    myHideRunnable = hideRunnable;
  }

  protected JComponent createPopupContent(Tree tree) {
    tree.setBackground(UIUtil.getToolTipBackground());
    return ScrollPaneFactory.createScrollPane(tree, true);
  }

  public void show(@NotNull D selectedItem) {
    showTreePopup(myTreeCreator.createTree(selectedItem));
  }

  protected @NotNull DefaultActionGroup getToolbarActions() {
    DefaultActionGroup toolbarActions = new DefaultActionGroup();
    toolbarActions.add(new EnableSetValueMode());
    toolbarActions.add(new SetValue());
    toolbarActions.add(new CancelSetValue());
    toolbarActions.addAll(getCustomizedActionGroup(XDebuggerActions.WATCHES_INLINE_POPUP_GROUP));
    return toolbarActions;
  }

  private FocusListener createTreeFocusListener() {
    return new FocusListener() {
      @Override
      public void focusGained(FocusEvent e) {
        if (mySetValueModeEnabled) {
          disableSetValueMode();
        }
      }

      @Override
      public void focusLost(FocusEvent e) {
        // do nothing
      }
    };
  }

  private TreeModelListener createTreeModelListener(final Tree tree) {
    return new TreeModelAdapter() {
      @Override
      public void treeStructureChanged(TreeModelEvent e) {
        resize(e.getTreePath(), tree);
      }
    };
  }

  protected void showTreePopup(final Tree tree) {
    if (myPopup != null) {
      myPopup.cancel();
    }

    tree.getModel().addTreeModelListener(createTreeModelListener(tree));
    FocusListener focusListener = createTreeFocusListener();
    tree.addFocusListener(focusListener);

    JComponent popupContent = createPopupContent(tree);

    setContent(popupContent, getToolbarActions(), ACTION_PLACE, tree);

    myPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(myContent, tree)
      .setRequestFocus(true)
      .setResizable(true)
      .setModalContext(false)
      .setMovable(true)
      .setDimensionServiceKey(myProject, DIMENSION_SERVICE_KEY, false)
      .setMayBeParent(true)
      .setCancelOnOtherWindowOpen(true)
      .setCancelKeyEnabled(false)
      .setKeyEventHandler(event -> {
        if (!mySetValueModeEnabled && event.getKeyCode() == KeyEvent.VK_F2) {
          enableSetValueMode();
        }
        else if (AbstractPopup.isCloseRequest(event)) {
          // Do not process a close request if the tree shows a speed search popup or 'set value' action is in process
          SpeedSearchBase speedSearch = ((SpeedSearchBase)SpeedSearchSupply.getSupply(tree));
          if (speedSearch != null && speedSearch.isPopupActive()) {
            speedSearch.hidePopup();
            return true;
          }
          else if (IdeFocusManager.getInstance(myProject).getFocusOwner() == tree) {
            myPopup.cancel();
            return true;
          }
          return false;
        }
        return false;
      })
      .addListener(new JBPopupListener() {
        @Override
        public void onClosed(@NotNull LightweightWindowEvent event) {
          tree.removeFocusListener(focusListener);
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
    myPopup.setSize(new Dimension(0, 0));

    setAutoResizeUntilToolbarNotFull(() -> updateDebugPopupBounds(tree, myToolbar, myPopup, false), myPopup);

    myPopup.show(new RelativePoint(myEditor.getContentComponent(), myPoint));
    setAutoResize(tree, myToolbar, myPopup);
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

  private void enableSetValueMode() {
    mySetValueModeEnabled = true;
    myToolbar.updateActionsImmediately();
  }

  private void disableSetValueMode() {
    mySetValueModeEnabled = false;
    myToolbar.updateActionsImmediately();
  }

  @Override
  protected boolean shouldBeVisible(AnAction action) {
    boolean isSetValueModeAction = action instanceof XDebuggerTreePopup.SetValue ||
                                   action instanceof XDebuggerTreePopup.CancelSetValue;
    return isSetValueModeAction && mySetValueModeEnabled || !isSetValueModeAction && !mySetValueModeEnabled;
  }

  protected static void registerTreeDisposable(Disposable disposable, Tree tree) {
    if (tree instanceof Disposable) {
      Disposer.register(disposable, (Disposable)tree);
    }
  }

  public static void setAutoResize(Tree tree, JComponent myToolbar, JBPopup myPopup) {
    Ref<Boolean> canShrink = Ref.create(true);
    ((XDebuggerTree)tree).addTreeListener(new XDebuggerTreeListener() {
      @Override
      public void childrenLoaded(@NotNull XDebuggerTreeNode node,
                                 @NotNull List<? extends XValueContainerNode<?>> children,
                                 boolean last) {
        if (last) {
          updateDebugPopupBounds(tree, myToolbar, myPopup, canShrink.get());
          canShrink.set(false);
        }
      }

      @Override
      public void nodeLoaded(@NotNull RestorableStateNode node, @NotNull String name) {
        updateDebugPopupBounds(tree, myToolbar, myPopup, false);
      }
    });
    updateDebugPopupBounds(tree, myToolbar, myPopup, canShrink.get());
  }

  public static void updateDebugPopupBounds(final Tree tree, JComponent toolbar, JBPopup popup, boolean canShrink) {
    final Window popupWindow = SwingUtilities.windowForComponent(popup.getContent());
    if (popupWindow == null) return;
    final Dimension size = tree.getPreferredSize();
    int hMargin = JBUI.scale(30);
    int vMargin = JBUI.scale(30);
    int width = Math.max(size.width, toolbar.getPreferredSize().width) + hMargin;
    Rectangle bounds = tree.getRowBounds(tree.getRowCount() - 1);
    int height = toolbar.getHeight() + vMargin + (bounds == null ? 0 : bounds.y + bounds.height);
    Rectangle screenRectangle = ScreenUtil.getScreenRectangle(toolbar);
    int maxWidth = screenRectangle.width / 2;
    int maxHeight = screenRectangle.height / 2;
    int newWidth = Math.min(width, maxWidth);
    int newHeight = Math.min(height, maxHeight);

    if (!canShrink) {
      newWidth = Math.max(newWidth, popupWindow.getWidth());
      newHeight = Math.max(newHeight, popupWindow.getHeight());
    }

    updatePopupBounds(popupWindow, newWidth, newHeight);
  }

  private class CancelSetValue extends AnAction {

    private CancelSetValue() {
      super(XDebuggerBundle.message("xdebugger.cancel.set.action.title"));
      setShortcutSet(CommonShortcuts.ESCAPE);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Component focusedComponent = IdeFocusManager.findInstance().getFocusOwner();
      KeyEvent event =
        new KeyEvent(focusedComponent, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED);
      myContent.dispatchEvent(event);
    }
  }

  private class EnableSetValueMode extends XSetValueAction {

    private EnableSetValueMode() {
      super();
      setShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0)));
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      Presentation presentation = e.getPresentation();
      presentation.setText(XDebuggerBundle.message("xdebugger.enable.set.action.title"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      enableSetValueMode();
      super.actionPerformed(e);
    }
  }

  private class SetValue extends AnAction {

    private SetValue() {
      super(XDebuggerBundle.message("xdebugger.set.text.value.action.title"));
      setShortcutSet(CommonShortcuts.ENTER);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Component focusedComponent = IdeFocusManager.findInstance().getFocusOwner();
      KeyEvent event =
        new KeyEvent(focusedComponent, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_ENTER, '\n');
      myContent.dispatchEvent(event);
    }
  }
}
