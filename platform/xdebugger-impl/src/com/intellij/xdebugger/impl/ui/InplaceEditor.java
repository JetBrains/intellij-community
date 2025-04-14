// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui;

import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl;
import com.intellij.ui.ClientProperty;
import com.intellij.ui.ComponentUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebuggerManagerListener;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.basic.ComboPopup;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

@ApiStatus.Internal
public abstract class InplaceEditor implements AWTEventListener {
  public static final Key<Boolean> IGNORE_MOUSE_EVENT = Key.create("InplaceEditor.Ignore.Mouse.Event");
  private static final Logger LOG = Logger.getInstance(InplaceEditor.class);
  private JComponent myInplaceEditorComponent;
  private final List<Runnable> myRemoveActions = new ArrayList<>();
  protected final Disposable myDisposable = Disposer.newDisposable();

  protected abstract JComponent createInplaceEditorComponent();

  protected abstract JComponent getPreferredFocusedComponent();

  public abstract Editor getEditor();

  public abstract JComponent getEditorComponent();

  protected void doPopupOKAction() {
    doOKAction();
  }

  public void doOKAction() {
    hide();
  }

  public void cancelEditing() {
    hide();
  }

  protected abstract JComponent getHostComponent();

  private void hide() {
    if (!isShown()) {
      return;
    }
    myInplaceEditorComponent = null;
    onHidden();
    myRemoveActions.forEach(Runnable::run);
    myRemoveActions.clear();

    Disposer.dispose(myDisposable);

    final JComponent hostComponent = getHostComponent();
    hostComponent.repaint();
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(hostComponent, true));
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
    beforeShow();

    final JComponent hostComponent = getHostComponent();

    final JRootPane rootPane = getHostComponent().getRootPane();
    if (rootPane == null) {
      return;
    }
    final JLayeredPane layeredPane = rootPane.getLayeredPane();

    Rectangle bounds = getEditorBounds();
    if (bounds == null) {
      return;
    }
    Point layeredPanePoint = SwingUtilities.convertPoint(hostComponent, bounds.x, bounds.y,layeredPane);

    final JComponent inplaceEditorComponent = createInplaceEditorComponent();
    myInplaceEditorComponent = inplaceEditorComponent;
    LOG.assertTrue(inplaceEditorComponent != null);
    setInplaceEditorBounds(inplaceEditorComponent, layeredPanePoint.x, layeredPanePoint.y, bounds.width, bounds.height);

    layeredPane.add(inplaceEditorComponent, Integer.valueOf(250));
    ClientProperty.put(inplaceEditorComponent, ToolWindowManagerImpl.PARENT_COMPONENT, hostComponent);

    myRemoveActions.add(() -> {
      layeredPane.remove(inplaceEditorComponent);
      ClientProperty.put(inplaceEditorComponent, ToolWindowManagerImpl.PARENT_COMPONENT, null);
    });

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
          JComponent hostComponent = getHostComponent();
          JLayeredPane layeredPane1 = hostComponent.getRootPane().getLayeredPane();
          Rectangle bounds1 = getEditorBounds();
          if (bounds1 == null) {
            doOKAction();
            return;
          }
          Point layeredPanePoint1 = SwingUtilities.convertPoint(hostComponent, bounds1.x, bounds1.y, layeredPane1);
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
      if (!hostComponent.isShowing()) {
        cancelEditing();
      }
    };

    hostComponent.addHierarchyListener(hierarchyListener);
    hostComponent.addComponentListener(componentListener);
    rootPane.addComponentListener(componentListener);

    myRemoveActions.add(() -> {
      hostComponent.removeHierarchyListener(hierarchyListener);
      hostComponent.removeComponentListener(componentListener);
      rootPane.removeComponentListener(componentListener);
    });

    getProject().getMessageBus().connect(myDisposable).subscribe(XDebuggerManager.TOPIC, new XDebuggerManagerListener() {
      @Override
      public void currentSessionChanged(@Nullable XDebugSession previousSession, @Nullable XDebugSession currentSession) {
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

  protected abstract void beforeShow();

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

    Boolean property = ClientProperty.get(sourceComponent, IGNORE_MOUSE_EVENT);
    if (property != null && property.equals(true)) {
      return;
    }

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

  protected abstract @Nullable Rectangle getEditorBounds();

  public boolean isShown() {
    return myInplaceEditorComponent != null;
  }


}