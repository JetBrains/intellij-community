// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.ui.tree;

import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.execution.Executor;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.execution.ui.RunContentWithExecutorListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.ComponentUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.basic.ComboPopup;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

public abstract class TreeInplaceEditor implements AWTEventListener {
  private static final Logger LOG = Logger.getInstance(TreeInplaceEditor.class);
  private JComponent myInplaceEditorComponent;
  private final List<Runnable> myRemoveActions = new ArrayList<>();
  protected final Disposable myDisposable = Disposer.newDisposable();

  protected abstract JComponent createInplaceEditorComponent();

  protected abstract JComponent getPreferredFocusedComponent();

  public abstract Editor getEditor();

  public abstract JComponent getEditorComponent();

  protected abstract TreePath getNodePath();

  protected abstract JTree getTree();

  protected void doPopupOKAction() {
    doOKAction();
  }

  public void doOKAction() {
    hide();
  }

  public void cancelEditing() {
    hide();
  }

  private void hide() {
    if (!isShown()) {
      return;
    }
    myInplaceEditorComponent = null;
    onHidden();
    myRemoveActions.forEach(Runnable::run);
    myRemoveActions.clear();

    Disposer.dispose(myDisposable);

    final JTree tree = getTree();
    tree.repaint();
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(tree, true));
  }

  protected void onHidden() {
  }

  protected abstract Project getProject();

  private static void setInplaceEditorBounds(JComponent component, int x, int y, int width, int height) {
    int h = Math.max(height, component.getPreferredSize().height);
    component.setBounds(x, y - (h - height) / 2, width, h);
  }

  public final void show() {
    LOG.assertTrue(myInplaceEditorComponent == null, "editor is not released");
    final JTree tree = getTree();
    tree.scrollPathToVisible(getNodePath());
    final JRootPane rootPane = tree.getRootPane();
    if (rootPane == null) {
      return;
    }
    final JLayeredPane layeredPane = rootPane.getLayeredPane();

    Rectangle bounds = getEditorBounds();
    if (bounds == null) {
      return;
    }
    Point layeredPanePoint = SwingUtilities.convertPoint(tree, bounds.x, bounds.y,layeredPane);

    final JComponent inplaceEditorComponent = createInplaceEditorComponent();
    myInplaceEditorComponent = inplaceEditorComponent;
    LOG.assertTrue(inplaceEditorComponent != null);
    setInplaceEditorBounds(inplaceEditorComponent, layeredPanePoint.x, layeredPanePoint.y, bounds.width, bounds.height);

    layeredPane.add(inplaceEditorComponent, new Integer(250));

    myRemoveActions.add(() -> layeredPane.remove(inplaceEditorComponent));

    inplaceEditorComponent.validate();
    inplaceEditorComponent.paintImmediately(0,0,inplaceEditorComponent.getWidth(),inplaceEditorComponent.getHeight());
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(
      () -> IdeFocusManager.getGlobalInstance().requestFocus(getPreferredFocusedComponent(), true));

    final ComponentAdapter componentListener = new ComponentAdapter() {
      @Override
      public void componentMoved(ComponentEvent e) {
        resetBounds();
      }

      @Override
      public void componentResized(ComponentEvent e) {
        resetBounds();
      }

      private void resetBounds() {
        final Project project = getProject();
        ApplicationManager.getApplication().invokeLater(() -> {
          if (!isShown() || project == null || project.isDisposed()) {
            return;
          }
          JTree tree1 = getTree();
          JLayeredPane layeredPane1 = tree1.getRootPane().getLayeredPane();
          Rectangle bounds1 = getEditorBounds();
          if (bounds1 == null) {
            doOKAction();
            return;
          }
          Point layeredPanePoint1 = SwingUtilities.convertPoint(tree1, bounds1.x, bounds1.y, layeredPane1);
          setInplaceEditorBounds(inplaceEditorComponent, layeredPanePoint1.x, layeredPanePoint1.y, bounds1.width, bounds1.height);
          inplaceEditorComponent.revalidate();
        });
      }

      @Override
      public void componentHidden(ComponentEvent e) {
        cancelEditing();
      }
    };

    final HierarchyListener hierarchyListener = e -> {
      if (!tree.isShowing()) {
        cancelEditing();
      }
    };

    tree.addHierarchyListener(hierarchyListener);
    tree.addComponentListener(componentListener);
    rootPane.addComponentListener(componentListener);

    myRemoveActions.add(() -> {
      tree.removeHierarchyListener(hierarchyListener);
      tree.removeComponentListener(componentListener);
      rootPane.removeComponentListener(componentListener);
    });

    getProject().getMessageBus().connect(myDisposable).subscribe(RunContentManager.TOPIC, new RunContentWithExecutorListener() {
      @Override
      public void contentSelected(@Nullable RunContentDescriptor descriptor, @NotNull Executor executor) {
        cancelEditing();
      }

      @Override
      public void contentRemoved(@Nullable RunContentDescriptor descriptor, @NotNull Executor executor) {
        cancelEditing();
      }
    });

    final JComponent editorComponent = getEditorComponent();
    editorComponent.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "enterStroke");
    editorComponent.getActionMap().put("enterStroke", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        doOKAction();
      }
    });
    editorComponent.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "escapeStroke");
    editorComponent.getActionMap().put("escapeStroke", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        cancelEditing();
      }
    });
    final Toolkit defaultToolkit = Toolkit.getDefaultToolkit();
    SwingUtilities.invokeLater(() -> {
      if (!isShown()) return;
      defaultToolkit.addAWTEventListener(this, AWTEvent.MOUSE_EVENT_MASK);
    });

    myRemoveActions.add(() -> defaultToolkit.removeAWTEventListener(this));
    onShown();
  }

  protected void onShown() {
  }

  @Override
  public void eventDispatched(AWTEvent event) {
    if (!isShown()) {
      return;
    }
    MouseEvent mouseEvent = (MouseEvent)event;
    if (mouseEvent.getClickCount() == 0) {
      return;
    }

    final int id = mouseEvent.getID();
    if (id != MouseEvent.MOUSE_PRESSED && id != MouseEvent.MOUSE_RELEASED && id != MouseEvent.MOUSE_CLICKED) {
      return;
    }

    final Component sourceComponent = mouseEvent.getComponent();
    final Point originalPoint = mouseEvent.getPoint();

    final Editor editor = getEditor();
    if (editor == null) return;

    // do not cancel editing if we click or scroll in editor popup
    final List<JBPopup> popups = JBPopupFactory.getInstance().getChildPopups(myInplaceEditorComponent);
    for (JBPopup popup : popups) {
      if (!popup.isDisposed() && SwingUtilities.isDescendingFrom(sourceComponent, ComponentUtil.getWindow(popup.getContent()))) {
        return;
      }
    }

    Project project = editor.getProject();
    LookupImpl activeLookup = project != null ? (LookupImpl)LookupManager.getInstance(project).getActiveLookup() : null;
    if (activeLookup != null){
      final Point lookupPoint = SwingUtilities.convertPoint(sourceComponent, originalPoint, activeLookup.getComponent());
      if (activeLookup.getComponent().getBounds().contains(lookupPoint)){
        return; //mouse click inside lookup
      } else {
        activeLookup.hide(); //hide popup on mouse position changed
      }
    }

    final Point point = SwingUtilities.convertPoint(sourceComponent, originalPoint, myInplaceEditorComponent);
    if (myInplaceEditorComponent.contains(point)) {
      return;
    }
    final Component componentAtPoint = SwingUtilities.getDeepestComponentAt(sourceComponent, originalPoint.x, originalPoint.y);
    for (Component comp = componentAtPoint; comp != null; comp = comp.getParent()) {
      if (comp instanceof ComboPopup) {
        doPopupOKAction();
        return;
      }
    }

    if (ComponentUtil.getWindow(sourceComponent) == ComponentUtil.getWindow(myInplaceEditorComponent) && id == MouseEvent.MOUSE_PRESSED) {
      doOKAction();
    }
  }

  @Nullable
  protected Rectangle getEditorBounds() {
    final JTree tree = getTree();
    Rectangle bounds = tree.getVisibleRect();
    Rectangle nodeBounds = tree.getPathBounds(getNodePath());
    if (bounds == null || nodeBounds == null) {
      return null;
    }
    if (bounds.y > nodeBounds.y || bounds.y + bounds.height < nodeBounds.y + nodeBounds.height) {
      return null;
    }
    bounds.y = nodeBounds.y;
    bounds.height = nodeBounds.height;

    if(nodeBounds.x > bounds.x) {
      bounds.width = bounds.width - nodeBounds.x + bounds.x;
      bounds.x = nodeBounds.x;
    }
    return bounds;
  }

  public boolean isShown() {
    return myInplaceEditorComponent != null;
  }
}
