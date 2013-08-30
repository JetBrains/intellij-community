/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.xdebugger.impl.evaluate.quick.common;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.tree.TreeModelAdapter;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.*;
import java.util.EventObject;

/**
 * @author nik
 */
public abstract class AbstractValueHint {
  private static final Logger LOG = Logger.getInstance("#com.intellij.xdebugger.impl.evaluate.quick.common.AbstractValueHint");
  @NonNls private final static String DIMENSION_SERVICE_KEY = "DebuggerActiveHint";
  private static final Icon COLLAPSED_TREE_ICON = IconUtil.getAddIcon();
  private static final int HINT_TIMEOUT = 7000; // ms
  private final KeyListener myEditorKeyListener = new KeyAdapter() {
    @Override
    public void keyReleased(KeyEvent e) {
      if(!isAltMask(e.getModifiers())) {
        ValueLookupManager.getInstance(myProject).hideHint();
      }
    }
  };
  private static final TextAttributes ourReferenceAttributes = new TextAttributes();
  static {
    ourReferenceAttributes.setForegroundColor(JBColor.BLUE);
    ourReferenceAttributes.setEffectColor(JBColor.BLUE);
    ourReferenceAttributes.setEffectType(EffectType.LINE_UNDERSCORE);
  }

  private RangeHighlighter myHighlighter;
  private Cursor myStoredCursor;
  private final Project myProject;
  private final Editor myEditor;
  private final ValueHintType myType;
  private Point myPoint;
  private LightweightHint myCurrentHint;
  private JBPopup myPopup;
  private boolean myHintHidden;
  private TextRange myCurrentRange;
  private Runnable myHideRunnable;

  public AbstractValueHint(Project project, Editor editor, Point point, ValueHintType type, final TextRange textRange) {
    myPoint = point;
    myProject = project;
    myEditor = editor;
    myType = type;
    myCurrentRange = textRange;
  }

  protected abstract boolean canShowHint();

  protected abstract void evaluateAndShowHint();

  private void resize(final TreePath path, JTree tree) {
    if (myPopup == null || !myPopup.isVisible()) return;
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
    final Rectangle targetBounds = new Rectangle(location.x,
                                                 location.y,
                                                 Math.max(size.width + 250, windowBounds.width),
                                                 Math.max(size.height, windowBounds.height));
    ScreenUtil.cropRectangleToFitTheScreen(targetBounds);
    popupWindow.setBounds(targetBounds);
    popupWindow.validate();
    popupWindow.repaint();
  }

  public boolean isKeepHint(Editor editor, Point point) {
    if (myCurrentHint != null && myCurrentHint.canControlAutoHide()) return true;

    if(myType == ValueHintType.MOUSE_ALT_OVER_HINT) {
      return false;
    }
    else if(myType == ValueHintType.MOUSE_CLICK_HINT) {
      if(myCurrentHint != null && myCurrentHint.isVisible()) {
        return true;
      }
    }
    else {
      int offset = calculateOffset(editor, point);

      if (myCurrentRange != null && myCurrentRange.getStartOffset() <= offset && offset <= myCurrentRange.getEndOffset()) {
        return true;
      }
    }
    return false;
  }

  public static int calculateOffset(@NotNull Editor editor, @NotNull Point point) {
    LogicalPosition pos = editor.xyToLogicalPosition(point);
    return editor.logicalPositionToOffset(pos);
  }

  public void hideHint() {
    myHintHidden = true;
    myCurrentRange = null;
    if(myStoredCursor != null) {
      Component internalComponent = myEditor.getContentComponent();
      internalComponent.setCursor(myStoredCursor);
      if (LOG.isDebugEnabled()) {
        LOG.debug("internalComponent.setCursor(myStoredCursor)");
      }
      internalComponent.removeKeyListener(myEditorKeyListener);
    }

    if(myCurrentHint != null) {
      myCurrentHint.hide();
      myCurrentHint = null;
    }
    if(myHighlighter != null) {
      myHighlighter.dispose();
      myHighlighter = null;
    }
  }

  public void invokeHint(Runnable hideRunnable) {
    myHideRunnable = hideRunnable;

    if(!canShowHint()) {
      hideHint();
      return;
    }

    if (myType == ValueHintType.MOUSE_ALT_OVER_HINT) {
      myHighlighter = myEditor.getMarkupModel().addRangeHighlighter(myCurrentRange.getStartOffset(), myCurrentRange.getEndOffset(),
                                                                    HighlighterLayer.SELECTION + 1, ourReferenceAttributes,
                                                                    HighlighterTargetArea.EXACT_RANGE);
      Component internalComponent = myEditor.getContentComponent();
      myStoredCursor = internalComponent.getCursor();
      internalComponent.addKeyListener(myEditorKeyListener);
      internalComponent.setCursor(hintCursor());
      if (LOG.isDebugEnabled()) {
        LOG.debug("internalComponent.setCursor(hintCursor())");
      }
    }
    else {
      evaluateAndShowHint();
    }
  }

  private static Cursor hintCursor() {
    return Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
  }

  public void shiftLocation() {
    if (myPopup != null) {
      final Window window = SwingUtilities.getWindowAncestor(myPopup.getContent());
      if (window != null) {
        myPoint = new RelativePoint(window, new Point(2, 2)).getPoint(myEditor.getContentComponent());
      }
    }
  }

  public Project getProject() {
    return myProject;
  }

  protected Editor getEditor() {
    return myEditor;
  }

  protected ValueHintType getType() {
    return myType;
  }

  public void showTreePopup(final AbstractValueHintTreeComponent<?> component, final Tree tree, final String title) {
    if (myPopup != null) {
      myPopup.cancel();
    }
    myPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(component.getMainPanel(), tree)
      .setRequestFocus(true)
      .setTitle(title)
      .setResizable(true)
      .setMovable(true)
      .setDimensionServiceKey(getProject(), DIMENSION_SERVICE_KEY, false)
      .createPopup();

    if (tree instanceof Disposable) {
      Disposer.register(myPopup, (Disposable)tree);
    }
    
    //Editor may be disposed before later invokator process this action
    if (getEditor().getComponent().getRootPane() == null) {
      myPopup.cancel();
      return;
    }
    myPopup.show(new RelativePoint(getEditor().getContentComponent(), myPoint));

    updateInitialBounds(tree);
  }

  protected boolean showHint(final JComponent component) {
    myCurrentHint = new LightweightHint(component);
    myCurrentHint.addHintListener(new HintListener() {
      @Override
      public void hintHidden(EventObject event) {
        if (myHideRunnable != null) {
          myHideRunnable.run();
        }
      }
    });

    //Editor may be disposed before later invokator process this action
    final Editor editor = getEditor();
    final JRootPane rootPane = editor.getComponent().getRootPane();
    if(rootPane == null) return false;

    Point p = HintManagerImpl.getHintPosition(myCurrentHint, editor, editor.xyToLogicalPosition(myPoint), HintManager.UNDER);
    final HintHint hintHint = HintManagerImpl.createHintHint(editor, p, myCurrentHint, HintManager.UNDER, true);

    HintManagerImpl.getInstanceImpl().showEditorHint(myCurrentHint, editor, p,
                               HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_SCROLLING, HINT_TIMEOUT, false,
                               hintHint);
    return true;
  }

  protected boolean isHintHidden() {
    return myHintHidden;
  }

  protected JComponent createExpandableHintComponent(final SimpleColoredText text, final Runnable expand) {
    final JComponent component = HintUtil.createInformationLabel(text, COLLAPSED_TREE_ICON);
    addClickListenerToHierarchy(component, new ClickListener() {
      @Override
      public boolean onClick(MouseEvent event, int clickCount) {
        if (myCurrentHint != null) {
          myCurrentHint.hide();
        }
        expand.run();
        return true;
      }
    });
    return component;
  }

  private static void addClickListenerToHierarchy(Component c, ClickListener l) {
    l.installOn(c);
    if (c instanceof Container) {
      final Container container = (Container)c;
      Component[] children = container.getComponents();
      for (Component child : children) {
        addClickListenerToHierarchy(child, l);
      }
    }
  }

  protected TextRange getCurrentRange() {
    return myCurrentRange;
  }

  protected TreeModelListener createTreeListener(final Tree tree) {
    return new TreeModelAdapter() {
      @Override
      public void treeStructureChanged(TreeModelEvent e) {
        resize(e.getTreePath(), tree);
      }
    };
  }

  private static boolean isAltMask(@JdkConstants.InputEventMask int modifiers) {
    return modifiers == InputEvent.ALT_MASK;
  }

  public static ValueHintType getType(final EditorMouseEvent e) {
    return isAltMask(e.getMouseEvent().getModifiers()) ? ValueHintType.MOUSE_ALT_OVER_HINT : ValueHintType.MOUSE_OVER_HINT;
  }

  public boolean isInsideHint(Editor editor, Point point) {
    return myCurrentHint != null && myCurrentHint.isInsideHint(new RelativePoint(editor.getContentComponent(), point));
  }
}
