// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.evaluate.quick.common;

import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
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
import com.intellij.ui.WindowMoveListener;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.AnActionLink;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.ui.speedSearch.SpeedSearchSupply;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.util.ui.tree.TreeModelAdapter;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeListener;
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
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;

import static com.intellij.xdebugger.impl.ui.DebuggerSessionTabBase.getCustomizedActionGroup;

@ApiStatus.Experimental
public class XDebuggerTreePopup<D> {
  public static final String ACTION_PLACE = "XDebuggerTreePopup";
  private final static @NonNls String DIMENSION_SERVICE_KEY = "DebuggerActiveHint";

  protected final @NotNull DebuggerTreeCreator<D> myTreeCreator;
  protected final @NotNull Project myProject;
  protected final @NotNull Editor myEditor;
  protected final @NotNull Point myPoint;
  protected final @Nullable Runnable myHideRunnable;
  protected @Nullable JBPopup myPopup;
  protected @Nullable JComponent myToolbar;

  @ApiStatus.Experimental
  public XDebuggerTreePopup(@NotNull DebuggerTreeCreator<D> creator,
                            @NotNull Editor editor,
                            @NotNull Point point,
                            @NotNull Project project,
                            @Nullable Runnable hideRunnable) {
    myTreeCreator = creator;
    myProject = project;
    myEditor = editor;
    myPoint = point;
    myHideRunnable = hideRunnable;
  }

  protected BorderLayoutPanel createMainPanel(Tree tree) {
    return fillMainPanel(JBUI.Panels.simplePanel(), tree);
  }

  protected BorderLayoutPanel fillMainPanel(BorderLayoutPanel mainPanel, Tree tree) {
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(tree, true);
    JComponent toolbar = createToolbar(mainPanel, tree);
    tree.setBackground(UIUtil.getToolTipBackground());
    toolbar.setBackground(UIUtil.getToolTipActionBackground());
    WindowMoveListener moveListener = new WindowMoveListener(mainPanel);
    toolbar.addMouseListener(moveListener);
    toolbar.addMouseMotionListener(moveListener);
    return mainPanel
      .addToCenter(scrollPane)
      .addToBottom(toolbar);
  }

  public void show(@NotNull D selectedItem) {
    showTreePopup(myTreeCreator.createTree(selectedItem));
  }

  protected DefaultActionGroup getToolbarActions() {
    DefaultActionGroup toolbarActions = new DefaultActionGroup();
    toolbarActions.addAll(getCustomizedActionGroup(XDebuggerActions.WATCHES_INLINE_POPUP_GROUP));
    toolbarActions.addSeparator();
    return toolbarActions;
  }

  protected DefaultActionGroup wrapActions(DefaultActionGroup toolbarActions, Tree tree) {
    DefaultActionGroup wrappedActions = new DefaultActionGroup();
    for (AnAction action : toolbarActions.getChildren(null)) {
      ActionWrapper actionLink = new ActionWrapper(action);
      actionLink.setDataProvider(tree);
      wrappedActions.add(actionLink);
    }

    return wrappedActions;
  }

  private JComponent createToolbar(JPanel mainPanel, Tree tree) {
    DefaultActionGroup wrappedActions = wrapActions(getToolbarActions(), tree);

    var toolbarImpl = new ActionToolbarImpl(ACTION_PLACE, wrappedActions, true);
    toolbarImpl.setTargetComponent(null);
    for (AnAction action : wrappedActions.getChildren(null)) {
      action.registerCustomShortcutSet(action.getShortcutSet(), mainPanel);
    }

    toolbarImpl.setBorder(BorderFactory.createEmptyBorder());
    myToolbar = toolbarImpl;
    return myToolbar;
  }

  protected static void registerTreeDisposable(Disposable disposable, Tree tree) {
    if (tree instanceof Disposable) {
      Disposer.register(disposable, (Disposable)tree);
    }
  }

  private TreeModelListener createTreeListener(final Tree tree) {
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
    tree.getModel().addTreeModelListener(createTreeListener(tree));
    BorderLayoutPanel popupContent = createMainPanel(tree);
    myPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(popupContent, tree)
      .setRequestFocus(true)
      .setResizable(true)
      .setModalContext(false)
      .setMovable(true)
      .setDimensionServiceKey(myProject, DIMENSION_SERVICE_KEY, false)
      .setMayBeParent(true)
      .setCancelOnOtherWindowOpen(true)
      .setCancelKeyEnabled(false)
      .setKeyEventHandler(event -> {
        if (AbstractPopup.isCloseRequest(event)) {
          // Do not process a close request if the tree shows a speed search popup or 'set value' action is in process
          SpeedSearchBase speedSearch = ((SpeedSearchBase)SpeedSearchSupply.getSupply(tree));
          if (speedSearch != null && speedSearch.isPopupActive())
          {
            speedSearch.hidePopup();
            return true;
          } else if (IdeFocusManager.getInstance(myProject).getFocusOwner() == tree) {
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
    final Point location = popupWindow.getLocation();
    int hMargin = JBUI.scale(30);
    int vMargin = JBUI.scale(30);
    int width = Math.max(size.width, toolbar.getPreferredSize().width) + hMargin;
    Rectangle bounds = tree.getRowBounds(tree.getRowCount() - 1);
    int height = toolbar.getHeight() + vMargin + (bounds == null ? 0 : bounds.y + bounds.height);
    Rectangle screenRectangle = ScreenUtil.getScreenRectangle(toolbar);
    int maxWidth = screenRectangle.width / 2;
    int maxHeight = screenRectangle.height / 2;
    final Rectangle targetBounds = new Rectangle(location.x,
                                                 location.y,
                                                 Math.min(width, maxWidth),
                                                 Math.min(height, maxHeight));

    if (!canShrink) {
      targetBounds.width = Math.max(targetBounds.width, popupWindow.getWidth());
      targetBounds.height = Math.max(targetBounds.height, popupWindow.getHeight());
    }
    ScreenUtil.cropRectangleToFitTheScreen(targetBounds);
    if (targetBounds.width != popupWindow.getWidth() || targetBounds.height != popupWindow.getHeight()) {
      popupWindow.setBounds(targetBounds);
      popupWindow.validate();
      popupWindow.repaint();
    }
  }

  private static class ActionWrapper extends AnAction implements CustomComponentAction {
    private final AnAction myDelegate;
    private Component myProvider;

    ActionWrapper(AnAction delegate) {
      super(delegate.getTemplateText());
      copyFrom(delegate);
      myDelegate = delegate;
    }

    public void setDataProvider(Component provider) {
      myProvider = provider;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      AnActionEvent delegateEvent = AnActionEvent.createFromAnAction(myDelegate,
                                                                     e.getInputEvent(),
                                                                     ACTION_PLACE,
                                                                     DataManager.getInstance().getDataContext(myProvider));
      myDelegate.actionPerformed(delegateEvent);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      myDelegate.update(e);
    }

    @Override
    public boolean isDumbAware() {
      return myDelegate.isDumbAware();
    }

    @Override
    public @NotNull JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
      if (myDelegate instanceof Separator) {
        return DebuggerUIUtil.getSecretComponentForToolbar(); // this is necessary because the toolbar hide if all action panels are not visible
      }

      myDelegate.applyTextOverride(ACTION_PLACE, presentation);

      ActionLinkButton button = new ActionLinkButton(this, presentation, (DataProvider)myProvider);
      JPanel actionPanel = DebuggerUIUtil.createCustomToolbarComponent(this, button);

      presentation.addPropertyChangeListener(new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
          if (evt.getPropertyName() == Presentation.PROP_TEXT) {
            button.setText((String)evt.getNewValue());
            button.repaint();
          }
          if (evt.getPropertyName() == Presentation.PROP_ENABLED) {
            actionPanel.setVisible((Boolean)evt.getNewValue());
            actionPanel.repaint();
          }
        }
      });
      actionPanel.setVisible(presentation.isEnabled());

      return actionPanel;
    }
  }

  private static class ActionLinkButton extends AnActionLink {
    ActionLinkButton(@NotNull AnAction action,
                     @NotNull Presentation presentation,
                     @Nullable DataProvider contextComponent) {
      //noinspection ConstantConditions
      super(presentation.getText(), action);
      setDataProvider(contextComponent);
      setFont(UIUtil.getToolTipFont());
    }
  }
}
