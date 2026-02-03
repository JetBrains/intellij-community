// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.markup.ActiveGutterRenderer;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.LineMarkerRenderer;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.JBColor;
import com.intellij.ui.paint.LinePainter2D;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.accessibility.SimpleAccessible;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.AccessibleContext;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static com.intellij.util.ObjectUtils.notNull;

/**
 * A panel which provides a11y for the current line in a gutter.
 * The panel contains {@link AccessibleGutterElement} components each of them represents a particular element in the gutter.
 * The panel can be shown and focused via {@code EditorFocusGutter} action.
 *
 * @author tav
 */
final class AccessibleGutterLine extends JPanel {
  private final EditorGutterComponentImpl myGutter;
  private AccessibleGutterElement mySelectedElement;
  // [tav] todo: soft-wrap doesn't work correctly
  private final int myLogicalLineNum;
  private final int myVisualLineNum;

  private static boolean actionHandlerInstalled;

  private static final class MyShortcuts {
    static final CustomShortcutSet MOVE_RIGHT = new CustomShortcutSet(
      new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), null),
      new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), null));

    static final CustomShortcutSet MOVE_LEFT = new CustomShortcutSet(
      new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), null),
      new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK), null));
  }

  private static @NotNull AccessibleGutterLine createAndActivate(@NotNull EditorGutterComponentImpl gutter) {
    return new AccessibleGutterLine(gutter);
  }

  void escape(boolean requestFocusToEditor) {
    myGutter.remove(this);
    myGutter.repaint();
    myGutter.setCurrentAccessibleLine(null);
    if (requestFocusToEditor) {
      IdeFocusManager.getGlobalInstance().requestFocus(myGutter.getEditor().getContentComponent(), true);
    }
  }

  @Override
  public void paint(Graphics g) {
    mySelectedElement.paint(g);
  }

  static void installListeners(@NotNull EditorGutterComponentImpl gutter) {
    if (!actionHandlerInstalled) {
      // [tav] todo: when the API is stable and open move it to ShowGutterIconTooltipAction
      actionHandlerInstalled = true;
      EditorActionManager.getInstance().setActionHandler("EditorShowGutterIconTooltip", new EditorActionHandler() {
        @Override
        protected void doExecute(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
          AccessibleGutterLine line = ((EditorGutterComponentImpl)editor.getGutter()).getCurrentAccessibleLine();
          if (line != null) line.showTooltipIfPresent();
        }
      });
    }
    gutter.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        gutter.setCurrentAccessibleLine(createAndActivate(gutter));
      }
    });
    gutter.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        gutter.escapeCurrentAccessibleLine();
      }
    });
    gutter.getEditor().getCaretModel().addCaretListener(new CaretListener() {
      @Override
      public void caretPositionChanged(@NotNull CaretEvent event) {
        AccessibleGutterLine line = gutter.getCurrentAccessibleLine();
        if (line != null) line.maybeLineChanged();
      }
    });
  }

  private void moveRight() {
    IdeFocusManager.getGlobalInstance().requestFocus(getFocusTraversalPolicy().getComponentAfter(this, mySelectedElement), true);
  }

  private static @NotNull AnActionEvent convertAnActionEventToMouseAnActionEvent(@NotNull AnActionEvent e, @NotNull Component component) {
    int x = component.getX() + component.getWidth() / 2;
    int y = component.getY() + component.getHeight() / 2;
    return new AnActionEvent(new MouseEvent(component, MouseEvent.MOUSE_CLICKED, 0, 0, x, y, 1, false),
                             e.getDataContext(),
                             e.getPlace(),
                             e.getPresentation(),
                             e.getActionManager(),
                             e.getModifiers());
  }

  private void pressEnter(AnActionEvent e) {
    Component focusOwner = IdeFocusManager.getGlobalInstance().getFocusOwner();
    if (focusOwner instanceof AccessibleGutterElement) {
      ((AccessibleGutterElement)focusOwner).myAccessible.performAction(convertAnActionEventToMouseAnActionEvent(e, focusOwner));
    }
  }

  private void moveLeft() {
    IdeFocusManager.getGlobalInstance().requestFocus(getFocusTraversalPolicy().getComponentBefore(this, mySelectedElement), true);
  }

  private void showTooltipIfPresent() {
    mySelectedElement.showTooltip();
  }

  private AccessibleGutterLine(@NotNull EditorGutterComponentImpl gutter) {
    super(null);

    EditorImpl editor = gutter.getEditor();
    int lineHeight = editor.getLineHeight();

    myGutter = gutter;
    myLogicalLineNum = editor.getCaretModel().getPrimaryCaret().getLogicalPosition().line;
    myVisualLineNum = editor.getCaretModel().getPrimaryCaret().getVisualPosition().line;

    /* line numbers */
    if (myGutter.isLineNumbersShown()) {
      addNewElement(new MySimpleAccessible() {
        @Override
        public @NotNull String getAccessibleName() {
          return IdeBundle.message("accessible.name.line", myLogicalLineNum + 1);
        }

        @Override
        public String getAccessibleTooltipText() {
          return null;
        }
      }, myGutter.getLineNumberAreaOffset(), 0, myGutter.getLineNumberAreaWidth(), lineHeight);
    }

    /* annotations */
    if (myGutter.isAnnotationsShown()) {
      int x = myGutter.getAnnotationsAreaOffset();
      AtomicInteger width = new AtomicInteger();
      AtomicReference<String> tooltipText = new AtomicReference<>();
      StringBuilder buf = new StringBuilder();
      myGutter.processTextAnnotationGutterProviders((gutterProvider, annotationSize) -> {
        if (tooltipText.get() == null) {
          tooltipText.set(gutterProvider.getToolTip(myLogicalLineNum, editor)); // [tav] todo: take first non-null?
        }
        buf.append(notNull(gutterProvider.getLineText(myLogicalLineNum, editor), ""));
        width.getAndAdd(annotationSize);
      });
      if (!buf.isEmpty()) {
        String tt = tooltipText.get();
        addNewElement(new MySimpleAccessible() {
          @Override
          public @NotNull String getAccessibleName() {
            return IdeBundle.message("accessible.name.annotation", buf.toString());
          }

          @Override
          public String getAccessibleTooltipText() {
            return tt;
          }
        }, x, 0, width.get(), lineHeight);
      }
    }

    /* icons */
    if (myGutter.areIconsShown()) {
      List<GutterMark> row = myGutter.getGutterRenderers(myVisualLineNum);
      myGutter.processIconsRow(myVisualLineNum, row, (x, y, renderer) -> {
        Icon icon = myGutter.scaleIcon(renderer.getIcon());
        addNewElement(new MySimpleAccessible() {
          final AnAction myAction = ((GutterIconRenderer)renderer).getClickAction();

          @Override
          public void performAction(@NotNull AnActionEvent e) {
            myAction.actionPerformed(e);
          }

          @Override
          public @NotNull String getAccessibleName() {
            if (renderer instanceof SimpleAccessible) {
              return ((SimpleAccessible)renderer).getAccessibleName();
            }
            return IdeBundle.message("accessible.name.icon", renderer.getClass().getSimpleName());
          }
          @Override
          public String getAccessibleTooltipText() {
            return renderer.getTooltipText();
          }
        }, x, 0, icon.getIconWidth(), lineHeight);
      });
    }

    /* active markers */
    if (myGutter.isLineMarkersShown()) {
      int firstVisibleOffset = editor.visualLineStartOffset(myVisualLineNum);
      int lastVisibleOffset = editor.visualLineStartOffset(myVisualLineNum + 1);
      myGutter.processGutterRangeHighlighters(firstVisibleOffset, lastVisibleOffset, highlighter -> {
        LineMarkerRenderer renderer = highlighter.getLineMarkerRenderer();
        if (renderer instanceof ActiveGutterRenderer) {
          Rectangle rect = myGutter.getLineRendererRectangle(highlighter);
          if (rect != null) {
            Rectangle bounds = ((ActiveGutterRenderer)renderer).calcBounds(editor, myVisualLineNum, rect);
            if (bounds != null) {
              addNewElement(new MySimpleAccessibleDelegate((ActiveGutterRenderer)renderer), bounds.x, 0, bounds.width, bounds.height);
            }
          }
        }
        return true;
      });
    }

    setOpaque(false);
    setFocusable(false);
    setFocusCycleRoot(true);
    setFocusTraversalPolicyProvider(true);
    setFocusTraversalPolicy(new LayoutFocusTraversalPolicy());
    setBounds(0, editor.visualLineToY(myVisualLineNum), myGutter.getWhitespaceSeparatorOffset(), lineHeight);

    myGutter.add(this);
    mySelectedElement = (AccessibleGutterElement)getFocusTraversalPolicy().getFirstComponent(this);
    if (mySelectedElement == null) {
      Rectangle b = getBounds(); // set above
      mySelectedElement = addNewElement(new MySimpleAccessible() {
        @Override
        public @NotNull String getAccessibleName() {
          return IdeBundle.message("accessible.name.empty");
        }

        @Override
        public String getAccessibleTooltipText() {
          return null;
        }
      }, 0, 0, b.width, b.height);
    }

    installActionHandler(CommonShortcuts.ESCAPE, () -> escape(true));
    installActionHandler(CommonShortcuts.ENTER, this::pressEnter); // [tav] todo: it can do something useful, e.g. forcing Screen Reader to voice
    installActionHandler(MyShortcuts.MOVE_RIGHT, this::moveRight);
    installActionHandler(MyShortcuts.MOVE_LEFT, this::moveLeft);

    IdeFocusManager.getGlobalInstance().requestFocus(mySelectedElement, true);
  }

  private void installActionHandler(ShortcutSet shortcut, Runnable action) {
    DumbAwareAction.create(e -> action.run()).registerCustomShortcutSet(shortcut, this);
  }

  @SuppressWarnings("SameParameterValue")
  private void installActionHandler(ShortcutSet shortcut, Consumer<? super AnActionEvent> action) {
    DumbAwareAction.create(e -> action.accept(e)).registerCustomShortcutSet(shortcut, this);
  }

  @SuppressWarnings("SameParameterValue")
  private @NotNull AccessibleGutterElement addNewElement(@NotNull MySimpleAccessible accessible, int x, int y, int width, int height) {
    AccessibleGutterElement obj = new AccessibleGutterElement(accessible, new Rectangle(x, y, width, height));
    add(obj);
    return obj;
  }

  @Override
  public void removeNotify() {
    repaint();
    super.removeNotify();
  }

  private void maybeLineChanged() {
    if (myLogicalLineNum == myGutter.getEditor().getCaretModel().getPrimaryCaret().getLogicalPosition().line) return;

    myGutter.remove(this);
    myGutter.setCurrentAccessibleLine(createAndActivate(myGutter));
  }

  @Override
  public void repaint() {
    if (myGutter == null) return;
    int y = myGutter.getEditor().visualLineToY(myVisualLineNum);
    myGutter.repaint(0, y, myGutter.getWhitespaceSeparatorOffset(), y + myGutter.getEditor().getLineHeight());
  }

  private boolean isActive() {
    return this == myGutter.getCurrentAccessibleLine();
  }

  static boolean isAccessibleGutterElement(Object element) {
    return element instanceof MySimpleAccessible;
  }

  /**
   * A component which represents a particular element in the gutter like a line number, an icon, a marker, etc.
   * The component is transparent, placed above the element and is made focusable. It's possible to navigate the
   * components in the current gutter line via the left/right arrow keys. Also, it's possible to show a tooltip
   * (when available) above an element via {@code EditorShowGutterIconTooltip} action.
   *
   * @author tav
   */
  private final class AccessibleGutterElement extends JLabel {
    private final @NotNull MySimpleAccessible myAccessible;

    AccessibleGutterElement(@NotNull MySimpleAccessible accessible, @NotNull Rectangle bounds) {
      myAccessible = accessible;

      setFocusable(true);
      setBounds(bounds.x, bounds.y, bounds.width, myGutter.getEditor().getLineHeight());
      setOpaque(false);

      setText(myAccessible.getAccessibleName());

      addFocusListener(new FocusListener() {
        @Override
        public void focusGained(FocusEvent e) {
          mySelectedElement = AccessibleGutterElement.this;
          getParent().repaint();
        }
        @Override
        public void focusLost(FocusEvent e) {
          if (!(e.getOppositeComponent() instanceof AccessibleGutterElement) && isActive()) {
            escape(false);
          }
        }
      });
    }

    @Override
    public void paint(Graphics g) {
      if (mySelectedElement != this) return;

      Color oldColor = g.getColor();
      try {
        g.setColor(JBColor.blue);
        Point parentLoc = getParent().getLocation();
        Rectangle bounds = getBounds();
        bounds.setLocation(parentLoc.x + bounds.x, parentLoc.y + bounds.y);
        int y = bounds.y + bounds.height - JBUIScale.scale(1);
        LinePainter2D.paint((Graphics2D)g, bounds.x, y, bounds.x + bounds.width, y,
                            LinePainter2D.StrokeType.INSIDE, JBUIScale.scale(1));
      } finally {
        g.setColor(oldColor);
      }
    }

    void showTooltip() {
      if (myAccessible.getAccessibleTooltipText() != null) {
        Rectangle bounds = getBounds();
        Rectangle pBounds = getParent().getBounds();
        int x = pBounds.x + bounds.x + bounds.width / 2;
        int y = pBounds.y + bounds.y + bounds.height / 2;
        myGutter.showToolTip(myAccessible.getAccessibleTooltipText(), new Point(x, y), Balloon.Position.atRight);
      }
    }

    @Override
    public AccessibleContext getAccessibleContext() {
      if (accessibleContext == null) {
        accessibleContext = new AccessibleJLabel() {
          @Override
          public String getAccessibleName() {
            return getText();
          }
        };
      }
      return accessibleContext;
    }
  }

  /**
   * The interface provides the ability to perform actions by clicking on the icon of the gutter.
   *
   * @author ASemenov
   */
  private interface MySimpleAccessible extends SimpleAccessible {
    /**
     * Performs the gutter icon action.
     */
    default void performAction(@NotNull AnActionEvent e) {}
  }

  /**
   * This delegate implements wrapping over SimpleAccessible for active gutter renderer.
   */
  private static final class MySimpleAccessibleDelegate implements MySimpleAccessible {
    private final @NotNull SimpleAccessible simpleAccessible;

    private MySimpleAccessibleDelegate(@NotNull SimpleAccessible accessible) {
      simpleAccessible = accessible;
    }

    @Override
    public @Nls @NotNull String getAccessibleName() {
      return simpleAccessible.getAccessibleName();
    }

    @Override
    public @Nullable @Nls(capitalization = Nls.Capitalization.Sentence) String getAccessibleTooltipText() {
      return simpleAccessible.getAccessibleTooltipText();
    }
  }
}
