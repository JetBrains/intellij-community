/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.xdebugger.impl.ui.tree;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentListener;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.basic.ComboPopup;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public abstract class TreeInplaceEditor implements AWTEventListener {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.impl.watch.DebuggerTreeInplaceEditor");
  private JComponent myInplaceEditorComponent;
  private final List<Runnable> myRemoveActions = new ArrayList<Runnable>();

  protected abstract JComponent createInplaceEditorComponent();

  protected abstract JComponent getPreferredFocusedComponent();

  public abstract Editor getEditor();

  public abstract JComponent getEditorComponent();

  protected abstract TreePath getNodePath();

  protected abstract JTree getTree();

  public void doOKAction() {
    remove();
  }

  public void cancelEditing() {
    remove();
  }

  protected void remove() {
    if (!isShown()) {
      return;
    }
    for (Runnable action : myRemoveActions) {
      action.run();
    }
    myRemoveActions.clear();

    myInplaceEditorComponent = null;

    final JTree tree = getTree();
    tree.repaint();
    tree.requestFocus();
  }

  protected abstract Project getProject();

  public void show() {
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
    inplaceEditorComponent.setBounds(
      layeredPanePoint.x,
      layeredPanePoint.y,
      bounds.width,
      Math.max(bounds.height, inplaceEditorComponent.getPreferredSize().height)
    );

    layeredPane.add(inplaceEditorComponent, new Integer(250));

    myRemoveActions.add(new Runnable() {
      public void run() {
        layeredPane.remove(inplaceEditorComponent);
      }
    });

    inplaceEditorComponent.validate();
    inplaceEditorComponent.paintImmediately(0,0,inplaceEditorComponent.getWidth(),inplaceEditorComponent.getHeight());
    getPreferredFocusedComponent().requestFocus();

    final ComponentAdapter componentListener = new ComponentAdapter() {
      public void componentResized(ComponentEvent e) {
        final Project project = getProject();
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            if (!isShown() || project == null || project.isDisposed()) {
              return;
            }
            JTree tree = getTree();
            JLayeredPane layeredPane = tree.getRootPane().getLayeredPane();
            Rectangle bounds = getEditorBounds();
            if (bounds == null) {
              return;
            }
            Point layeredPanePoint = SwingUtilities.convertPoint(tree, bounds.x, bounds.y, layeredPane);
            inplaceEditorComponent.setBounds(layeredPanePoint.x, layeredPanePoint.y, bounds.width, bounds.height);
            inplaceEditorComponent.revalidate();
          }
        });
      }

      public void componentHidden(ComponentEvent e) {
        cancelEditing();
      }
    };
    rootPane.addComponentListener(componentListener);
    myRemoveActions.add(new Runnable() {
      public void run() {
        rootPane.removeComponentListener(componentListener);
      }
    });

    final RunContentManager contentManager = ExecutionManager.getInstance(getProject()).getContentManager();
    final RunContentListener runContentListener = new RunContentListener() {
      public void contentSelected(RunContentDescriptor descriptor) {
        cancelEditing();
      }

      public void contentRemoved(RunContentDescriptor descriptor) {
        cancelEditing();
      }
    };
    contentManager.addRunContentListener(runContentListener);
    myRemoveActions.add(new Runnable() {
      public void run() {
        contentManager.removeRunContentListener(runContentListener);
      }
    });

    final JComponent editorComponent = getEditorComponent();
    editorComponent.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "enterStroke");
    editorComponent.getActionMap().put("enterStroke", new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        doOKAction();
      }
    });
    editorComponent.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "escapeStroke");
    editorComponent.getActionMap().put("escapeStroke", new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        cancelEditing();
      }
    });
    final Toolkit defaultToolkit = Toolkit.getDefaultToolkit();
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        if (!isShown()) return;
        defaultToolkit.addAWTEventListener(TreeInplaceEditor.this, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_WHEEL_EVENT_MASK);
      }
    });

    myRemoveActions.add(new Runnable() {
      public void run() {
        defaultToolkit.removeAWTEventListener(TreeInplaceEditor.this);
      }
    });
  }

  public void eventDispatched(AWTEvent event) {
    if (!isShown()) {
      return;
    }
    MouseEvent mouseEvent = (MouseEvent)event;
    if (mouseEvent.getClickCount() == 0 && !(event instanceof MouseWheelEvent)) {
      return;
    }

    final int id = mouseEvent.getID();
    if (id == MouseEvent.MOUSE_WHEEL) {
      cancelEditing();
      return;
    }

    if (id != MouseEvent.MOUSE_PRESSED && id != MouseEvent.MOUSE_RELEASED && id != MouseEvent.MOUSE_CLICKED) {
      return;
    }
    
    final Component sourceComponent = mouseEvent.getComponent();
    final Point originalPoint = mouseEvent.getPoint();

    final Editor editor = getEditor();
    if (editor == null) return;
    
    final Lookup activeLookup = LookupManager.getInstance(editor.getProject()).getActiveLookup();
    if (activeLookup != null){
      final JLayeredPane layeredPane = getTree().getRootPane().getLayeredPane();
      final Point layeredPoint = SwingUtilities.convertPoint(sourceComponent, originalPoint, layeredPane);
      if (activeLookup.getBounds().contains(layeredPoint)){
        return; //mouse click inside lookup
      } else {
        ((LookupImpl)activeLookup).hide(); //hide popup on mouse position changed
      }
    }

    final Point point = SwingUtilities.convertPoint(sourceComponent, originalPoint, myInplaceEditorComponent);
    if (myInplaceEditorComponent.contains(point)) {
      return;
    }
    final Component componentAtPoint = SwingUtilities.getDeepestComponentAt(sourceComponent, originalPoint.x, originalPoint.y);
    for (Component comp = componentAtPoint; comp != null; comp = comp.getParent()) {
      if (comp instanceof ComboPopup) {
        return;
      }
    }
    cancelEditing();
  }

  @Nullable
  private Rectangle getEditorBounds() {
    final JTree tree = getTree();
    Rectangle bounds = tree.getVisibleRect();
    Rectangle nodeBounds = tree.getPathBounds(getNodePath());
    if (bounds == null || nodeBounds == null) {
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
