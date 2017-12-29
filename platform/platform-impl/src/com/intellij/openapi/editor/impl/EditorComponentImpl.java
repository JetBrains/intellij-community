// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.ide.CutProvider;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.PasteProvider;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actions.EditorActionUtil;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.util.EditorUIUtil;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.ui.TypingTarget;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.Grayer;
import com.intellij.ui.components.Magnificator;
import com.intellij.util.ui.JBSwingUtilities;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.ScreenReader;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.*;
import javax.swing.*;
import javax.swing.event.ChangeListener;
import javax.swing.event.UndoableEditListener;
import javax.swing.plaf.TextUI;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.im.InputMethodRequests;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EditorComponentImpl extends JTextComponent implements Scrollable, DataProvider, Queryable, TypingTarget, Accessible {
  private final EditorImpl myEditor;
  private final ApplicationImpl myApplication;

  public EditorComponentImpl(@NotNull EditorImpl editor) {
    myEditor = editor;
    enableEvents(AWTEvent.KEY_EVENT_MASK | AWTEvent.INPUT_METHOD_EVENT_MASK);
    enableInputMethods(true);
    // Note: Ideally, we should always set "FocusCycleRoot" to "false", but,
    // in the interest of backward compatibility, we only do so when a
    // screen reader is active.
    setFocusCycleRoot(!ScreenReader.isActive());
    if (ScreenReader.isActive()) {
      setFocusable(true);
    }
    setOpaque(true);

    putClientProperty(Magnificator.CLIENT_PROPERTY_KEY, new Magnificator() {
      @Override
      public Point magnify(double scale, Point at) {
        if (myEditor.isDisposed()) return at;
        VisualPosition magnificationPosition = myEditor.xyToVisualPosition(at);
        double currentSize = myEditor.getColorsScheme().getEditorFontSize();
        int defaultFontSize = EditorColorsManager.getInstance().getGlobalScheme().getEditorFontSize();
        myEditor.setFontSize(Math.max((int)(currentSize * scale), defaultFontSize));

        return myEditor.visualPositionToXY(magnificationPosition);
      }
    });
    myApplication = (ApplicationImpl)ApplicationManager.getApplication();

    // This editor extends JTextComponent rather than JComponent *only* for accessibility
    // purposes, and the JTextComponent is not fully supported: it does not reflect the
    // true contents of the document, it doesn't paint, it doesn't have a proper UI delegate,
    // etc. It simply extends JTextComponent and accepts document and caret listeners (and
    // dispatches editing events to them) because on some platforms, the accessibility
    // support is hardcoded to only work for JTextComponent rather than AccessibleText in
    // general.
    setupJTextComponentContext();

    // Remove JTextComponent's mouse/focus listeners added in its ctor.
    for (MouseListener l : getMouseListeners()) removeMouseListener(l);
    for (FocusListener l : getFocusListeners()) removeFocusListener(l);
  }

  @Override
  public void paint(@NotNull Graphics g) {
    if (!isEnabled()) {
      g = new Grayer((Graphics2D)g, getBackground());
    }
    super.paint(g);
  }

  @NotNull
  public EditorImpl getEditor() {
    return myEditor;
  }

  @Override
  public Object getData(String dataId) {
    if (myEditor.isDisposed()) return null;

    if (PlatformDataKeys.COPY_PROVIDER.is(dataId)) {
      // enable copying from editor in renderer mode
      return myEditor.getCopyProvider();
    }
    
    if (myEditor.isRendererMode()) return null;

    if (CommonDataKeys.EDITOR.is(dataId)) {
      return myEditor;
    }
    if (CommonDataKeys.CARET.is(dataId)) {
      return myEditor.getCaretModel().getCurrentCaret();
    }
    if (PlatformDataKeys.DELETE_ELEMENT_PROVIDER.is(dataId)) {
      return myEditor.getDeleteProvider();
    }
    if (PlatformDataKeys.CUT_PROVIDER.is(dataId)) {
      return myEditor.getCutProvider();
    }
    if (PlatformDataKeys.PASTE_PROVIDER.is(dataId)) {
      return myEditor.getPasteProvider();
    }
    if (CommonDataKeys.EDITOR_VIRTUAL_SPACE.is(dataId)) {
      LogicalPosition location = myEditor.myLastMousePressedLocation;
      if (location == null) {
        location = myEditor.getCaretModel().getLogicalPosition();
      }
      return EditorUtil.inVirtualSpace(myEditor, location);
    }
    return null;
  }

  @Override
  public Color getBackground() {
    return myEditor.getBackgroundColor();
  }

  @Override
  public Dimension getPreferredSize() {
    return myEditor.getPreferredSize();
  }

  protected void fireResized() {
    processComponentEvent(new ComponentEvent(this, ComponentEvent.COMPONENT_RESIZED));
  }

  @Override
  protected void processInputMethodEvent(InputMethodEvent e) {
    // Don't dispatch to super first; now that EditorComponentImpl is a JTextComponent,
    // this would have the side effect of invoking Swing document machinery which relies
    // on creating Document positions etc (and won't update the document in an IntelliJ safe
    // way, such as running through all the carets etc.
    //    super.processInputMethodEvent(e);

    if (!e.isConsumed()) {
      switch (e.getID()) {
        case InputMethodEvent.INPUT_METHOD_TEXT_CHANGED:
          myEditor.replaceInputMethodText(e);
          // No breaks over here.

          //noinspection fallthrough
        case InputMethodEvent.CARET_POSITION_CHANGED:
          myEditor.inputMethodCaretPositionChanged(e);
          e.consume();
          break;
      }
    }

    super.processInputMethodEvent(e);
  }

  @Override
  public ActionCallback type(final String text) {
    final ActionCallback result = new ActionCallback();
    UIUtil.invokeLaterIfNeeded(() -> myEditor.type(text).notify(result));
    return result;
  }

  @Nullable
  @Override
  public InputMethodRequests getInputMethodRequests() {
    return IdeEventQueue.getInstance().isInputMethodEnabled() ? myEditor.getInputMethodRequests() : null;
  }

  @Override
  protected Graphics getComponentGraphics(Graphics graphics) {
    return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(graphics));
  }

  @Override
  public void paintComponent(Graphics g) {
    myApplication.editorPaintStart();

    try {
      Graphics2D gg = (Graphics2D)g;
      UIUtil.setupComposite(gg);
      if (myEditor.useEditorAntialiasing()) {
        EditorUIUtil.setupAntialiasing(gg);
      }
      else {
        UISettings.setupAntialiasing(gg);
      }
      gg.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, myEditor.myFractionalMetricsHintValue);
      AffineTransform origTx = JBUI.alignToIntGrid(gg, true, false);
      myEditor.paint(gg);
      if (origTx != null) gg.setTransform(origTx);
    }
    finally {
      myApplication.editorPaintFinish();
    }
  }

  public void repaintEditorComponent() {
    repaint();
  }

  public void repaintEditorComponent(int x, int y, int width, int height) {
    int topOverhang = Math.max(0, myEditor.myView.getTopOverhang());
    int bottomOverhang = Math.max(0, myEditor.myView.getBottomOverhang());
    repaint(x, y - topOverhang, width, height + topOverhang + bottomOverhang);
  }

  //--implementation of Scrollable interface--------------------------------------
  @Override
  public Dimension getPreferredScrollableViewportSize() {
    return myEditor.getPreferredSize();
  }

  @Override
  public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
    if (orientation == SwingConstants.VERTICAL) {
      return myEditor.getLineHeight();
    }
    // if orientation == SwingConstants.HORIZONTAL
    return EditorUtil.getSpaceWidth(Font.PLAIN, myEditor);
  }

  @Override
  public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
    if (orientation == SwingConstants.VERTICAL) {
      int lineHeight = myEditor.getLineHeight();
      if (direction > 0) {
        int lineNumber = (visibleRect.y + visibleRect.height) / lineHeight;
        return lineHeight * lineNumber - visibleRect.y;
      }
      else {
        int lineNumber = (visibleRect.y - visibleRect.height) / lineHeight;
        return visibleRect.y - lineHeight * lineNumber;
      }
    }
    // if orientation == SwingConstants.HORIZONTAL
    return visibleRect.width;
  }

  @Override
  public boolean getScrollableTracksViewportWidth() {
    return getParent() instanceof JViewport && getParent().getWidth() > getPreferredSize().width;
  }

  @Override
  public boolean getScrollableTracksViewportHeight() {
    return getParent() instanceof JViewport && getParent().getHeight() > getPreferredSize().height;
  }

  @Override
  public void putInfo(@NotNull Map<String, String> info) {
    myEditor.putInfo(info);
  }

  @NonNls
  @Override
  public String toString() {
    return "EditorComponent file=" + myEditor.getVirtualFile();
  }

  // -----------------------------------------------------------------------------------------------
  // Accessibility/screen reader support for the editor
  // -----------------------------------------------------------------------------------------------
  // Swing supports accessibility via the AccessibleText interface, as well as AccessibleEditableText
  // and some other accessibility interfaces. In theory, all we would need to do to support
  // accessibility for the IDE editor is to implement these in the accessible context for
  // the editor component.
  //
  // However, it turns out that on some platforms, such as on MacOSX, the accessibility integration
  // for Java is hardcoded to only work with JTextComponents! Not only does the code ignore
  // property change events announcing caret motion and document changes; it performs specific
  // instanceof checks, and the way it adds listeners is to directly add caret and document
  // listeners on the JTextComponent. It also performs other JTextComponent operations such
  // as looking up the TextUI delegate and performing viewToModel calls, it asks for the Swing
  // document's root element and uses it to compute line numbers (and line offset ranges),
  // etc.
  //
  // Therefore, in order to support accessibility for the source editor, we have to make the source
  // editor actually extend JTextComponent. However, we don't want to use any of the editor functionality,
  // so we use very simple stub implementations for:
  //   - the caret
  //   - the document
  //   - the UI delegate
  // and we override various JTextComponent to disable their normal operation. This leaves us with
  // (1) an accessible component which extends JTextComponent, and which provides a document and
  //     a caret instance that the accessibility infrastructure will register listeners with
  // (2) the ability to translate real IDE editor events (caret motion, editing events) into
  //     corresponding Swing events and dispatch these to the caret/document listeners

  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null) {
      accessibleContext = new AccessibleEditorComponentImpl();
    }
    return accessibleContext;
  }

  private void setupJTextComponentContext() {
    setDocument(new EditorAccessibilityDocument());
    setCaret(new EditorAccessibilityCaret());
  }

  /**
   *  We're inheriting method now in order to support accessibility, but you
   * should <b>NOT</b> call this method if you have an {@linkplain EditorComponentImpl}
   * and you're looking for the real document.
   */
  @Deprecated
  @Override
  public javax.swing.text.Document getDocument() {
    return super.getDocument();
  }

  @Override
  public int getCaretPosition() {
    return myEditor.getCaretModel().getOffset();
  }

  @Override
  public void updateUI() {
    // Don't use the default TextUI, BaseTextUI, which does a lot of unnecessary
    // work. We do however need to provide a TextUI implementation since some
    // screen reader support code will invoke it
    setUI(new EditorAccessibilityTextUI());
    UISettings.setupEditorAntialiasing(this);
    invalidate();
  }

  @Override
  public String getToolTipText(MouseEvent event) {
    // Undo effect of JTextComponent superclass: this is the default JComponent implementation
    return this.getToolTipText();
  }

  /** Redispatch an IDE {@link CaretEvent} to a Swing {@link javax.swing.event.CaretListener} */
  private void fireJTextComponentCaretChange(final CaretEvent event) {
    javax.swing.event.CaretEvent swingEvent = new javax.swing.event.CaretEvent(this) {
      @Override
      public int getDot() {
        Caret caret = event.getCaret();
        if (caret != null) {
          return caret.getOffset();
        }
        return 0;
      }

      @Override
      public int getMark() {
        Caret caret = event.getCaret();
        if (caret != null) {
          return caret.getLeadSelectionOffset();
        }
        return 0;
      }
    };
    for (javax.swing.event.CaretListener listener : getCaretListeners()) {
      listener.caretUpdate(swingEvent);
    }
  }

  /** Redispatch an IDE {@link DocumentEvent} to a Swing {@link javax.swing.event.DocumentListener} */
  private void fireJTextComponentDocumentChange(final DocumentEvent event) {
    //noinspection deprecation
    List<javax.swing.event.DocumentListener> listeners = ((EditorAccessibilityDocument)getDocument()).getListeners();
    if (listeners == null) {
      return;
    }

    javax.swing.event.DocumentEvent swingEvent = new javax.swing.event.DocumentEvent() {
      @Override
      public int getOffset() {
        return event.getOffset();
      }

      @Override
      public int getLength() {
        return event.getNewLength();
      }

      @Override
      public javax.swing.text.Document getDocument() {
        //noinspection deprecation
        return EditorComponentImpl.this.getDocument();
      }

      @Override
      public EventType getType() {
        return event.getOldLength() == 0 ? EventType.INSERT : event.getNewLength() == 0 ? EventType.REMOVE : EventType.CHANGE;
      }

      @Nullable
      @Override
      public ElementChange getChange(Element element) {
        return null;
      }
    };
    for (javax.swing.event.DocumentListener listener : listeners) {
      javax.swing.event.DocumentEvent.EventType type = swingEvent.getType();
      if (type == javax.swing.event.DocumentEvent.EventType.INSERT) {
        listener.insertUpdate(swingEvent);
      } else if (type == javax.swing.event.DocumentEvent.EventType.REMOVE) {
        listener.removeUpdate(swingEvent);
      } else if (type == javax.swing.event.DocumentEvent.EventType.CHANGE) {
        listener.changedUpdate(swingEvent);
      }
    }
  }

  private static void notSupported() {
    throw new RuntimeException("Not supported for this text implementation");
  }

  /** {@linkplain javax.swing.text.PlainDocument} does a lot of work we don't need.
   * This exists simply to be able to send editing events to the screen reader. */
  @SuppressWarnings("UnnecessaryFullyQualifiedName")
  private class EditorAccessibilityDocument implements javax.swing.text.Document, javax.swing.text.Element {
    private List<javax.swing.event.DocumentListener> myListeners;

    @Nullable
    public List<javax.swing.event.DocumentListener> getListeners() {
      return myListeners;
    }

    @Override
    public int getLength() {
      return myEditor.getDocument().getTextLength();
    }

    @Override
    public void addDocumentListener(javax.swing.event.DocumentListener documentListener) {
      if (myListeners == null) {
        myListeners = new ArrayList<>(2);
      }
      myListeners.add(documentListener);
    }

    @Override
    public void removeDocumentListener(javax.swing.event.DocumentListener documentListener) {
      if (myListeners != null) {
        myListeners.remove(documentListener);
      }
    }

    @Override
    public void addUndoableEditListener(UndoableEditListener undoableEditListener) {
    }

    @Override
    public void removeUndoableEditListener(UndoableEditListener undoableEditListener) {
    }

    @Override
    @Nullable
    public Object getProperty(Object o) {
      return null;
    }

    @Override
    public void putProperty(Object o, Object o1) {
    }

    @Override
    public void remove(final int offset, final int length) throws BadLocationException {
      editDocumentSafely(offset, length, null);
    }

    @Override
    public void insertString(final int offset, final String text, AttributeSet attributeSet) throws BadLocationException {
      editDocumentSafely(offset, 0, text);
    }

    @Override
    public String getText(final int offset, final int length) throws BadLocationException {
      return ReadAction
        .compute(() -> myEditor.getDocument().getText(new TextRange(offset, offset + length)));
    }

    @Override
    public void getText(int offset, int length, Segment segment) throws BadLocationException {
      char[] s = getText(offset, length).toCharArray();
      segment.array = s;
      segment.offset = 0;
      segment.count = s.length;
    }

    @Nullable
    @Override
    public Position getStartPosition() {
      notSupported();
      return null;
    }

    @Nullable
    @Override
    public Position getEndPosition() {
      notSupported();
      return null;
    }

    @Nullable
    @Override
    public Position createPosition(int i) throws BadLocationException {
      notSupported();
      return null;
    }

    @Override
    public Element[] getRootElements() {
      return new Element[] { this };
    }

    @Override
    public Element getDefaultRootElement() {
      return this;
    }

    @Override
    public void render(Runnable runnable) {
      ApplicationManager.getApplication().runReadAction(runnable);
    }

    // ---- Implements Element for the root element ----
    //
    // This is here because the accessibility code ends up calling some JTextComponent
    // methods; in particular, CAccessibleText calls root.getElementIndex(index)
    // to map an offset to a line number, and getRangeForLine calls root.getElement(lineIndex)
    // to get a range object for a given line, and then getStartOffset() and getEndOffset()
    // on the result.

    @Override
    public javax.swing.text.Document getDocument() {
      return this;
    }

    @Nullable
    @Override
    public Element getParentElement() {
      return null;
    }

    @Nullable
    @Override
    public String getName() {
      return null;
    }

    @Nullable
    @Override
    public AttributeSet getAttributes() {
      return null;
    }

    @Override
    public int getStartOffset() {
      return 0;
    }

    @Override
    public int getEndOffset() {
      return getLength();
    }

    @Override
    public int getElementIndex(int i) {
      // For the root element this asks for the index of the offset, which
      // means the line number
      Document document = myEditor.getDocument();
      return document.getLineNumber(i);
    }

    @Override
    public int getElementCount() {
      Document document = myEditor.getDocument();
      return document.getLineCount();
    }

    @Override
    public Element getElement(final int i) {
      return new Element() {
        @Override
        public javax.swing.text.Document getDocument() {
          return EditorAccessibilityDocument.this;
        }

        @Override
        public Element getParentElement() {
          return EditorAccessibilityDocument.this;
        }

        @Nullable
        @Override
        public String getName() {
          return null;
        }

        @Nullable
        @Override
        public AttributeSet getAttributes() {
          return null;
        }

        @Override
        public int getStartOffset() {
          Document document = myEditor.getDocument();
          return document.getLineStartOffset(i);
        }

        @Override
        public int getEndOffset() {
          Document document = myEditor.getDocument();
          return document.getLineEndOffset(i);
        }

        @Override
        public int getElementIndex(int i) {
          return 0;
        }

        @Override
        public int getElementCount() {
          return 0;
        }

        @Nullable
        @Override
        public Element getElement(int i) {
          return null;
        }

        @Override
        public boolean isLeaf() {
          return true;
        }
      };
    }

    @Override
    public boolean isLeaf() {
      return false;
    }
  }

  @Override
  public void setText(String text) {
    editDocumentSafely(0, myEditor.getDocument().getTextLength(), text);
  }

  /** Inserts, removes or replaces the given text at the given offset */
  private void editDocumentSafely(final int offset, final int length, @Nullable final String text) {
    TransactionGuard.submitTransaction(myEditor.getDisposable(), () -> {
      Project project = myEditor.getProject();
      Document document = myEditor.getDocument();
      if (!FileDocumentManager.getInstance().requestWriting(document, project)) {
        return;
      }

      CommandProcessor.getInstance().executeCommand(project, () -> WriteAction.run(() -> {
        document.startGuardedBlockChecking();
        try {
          if (text == null) {
            // remove
            document.deleteString(offset, offset + length);
          }
          else if (length == 0) {
            // insert
            document.insertString(offset, text);
          }
          else {
            document.replaceString(offset, offset + length, text);
          }
        }
        catch (ReadOnlyFragmentModificationException e) {
          EditorActionManager.getInstance().getReadonlyFragmentModificationHandler(document).handle(e);
        }
        finally {
          document.stopGuardedBlockChecking();
        }
      }), "", document, UndoConfirmationPolicy.DEFAULT, document);
    });
  }

  /** {@linkplain DefaultCaret} does a lot of work we don't want (listening
   * for focus events etc). This exists simply to be able to send caret events to the screen reader. */
  private class EditorAccessibilityCaret implements javax.swing.text.Caret {
    @Override
    public void install(JTextComponent jTextComponent) {
    }

    @Override
    public void deinstall(JTextComponent jTextComponent) {
    }

    @Override
    public void paint(Graphics graphics) {
    }

    @Override
    public void addChangeListener(ChangeListener changeListener) {
    }

    @Override
    public void removeChangeListener(ChangeListener changeListener) {
    }

    @Override
    public boolean isVisible() {
      return true;
    }

    @Override
    public void setVisible(boolean visible) {
    }

    @Override
    public boolean isSelectionVisible() {
      return true;
    }

    @Override
    public void setSelectionVisible(boolean visible) {
    }

    @Override
    public void setMagicCaretPosition(Point point) {
    }

    @Nullable
    @Override
    public Point getMagicCaretPosition() {
      return null;
    }

    @Override
    public void setBlinkRate(int rate) {
    }

    @Override
    public int getBlinkRate() {
      return 250;
    }

    @Override
    public int getDot() {
      return myEditor.getCaretModel().getOffset();
    }

    @Override
    public int getMark() {
      return myEditor.getSelectionModel().getSelectionStart();
    }

    @Override
    public void setDot(int offset) {
      if (!myEditor.isDisposed()) {
        myEditor.getCaretModel().moveToOffset(offset);
      }
    }

    @Override
    public void moveDot(int offset) {
      if (!myEditor.isDisposed()) {
        myEditor.getCaretModel().moveToOffset(offset);
      }
    }
  }

  /**
   * Specialized TextUI intended *only* for accessibility usage. Not all the methods are called; only viewToModel, not modelToView.
   */
  private class EditorAccessibilityTextUI extends TextUI {
    @Nullable
    @Override
    public Rectangle modelToView(JTextComponent tc, int offset) throws BadLocationException {
      return modelToView(tc, offset, Position.Bias.Forward);
    }

    @Override
    public int viewToModel(JTextComponent tc, Point pt) {
      LogicalPosition logicalPosition = myEditor.xyToLogicalPosition(pt);
      return myEditor.logicalPositionToOffset(logicalPosition);
    }

    @Nullable
    @Override
    public Rectangle modelToView(JTextComponent tc, int offset, Position.Bias bias) throws BadLocationException {
      LogicalPosition pos = myEditor.offsetToLogicalPosition(offset).leanForward(bias == Position.Bias.Forward);
      LogicalPosition posNext = myEditor.offsetToLogicalPosition(bias == Position.Bias.Forward ? offset + 1 : offset - 1)
        .leanForward(bias != Position.Bias.Forward);
      Point point = myEditor.logicalPositionToXY(pos);
      Point pointNext = myEditor.logicalPositionToXY(posNext);
      return point.y == pointNext.y 
             ? new Rectangle(Math.min(point.x, pointNext.x), point.y, Math.abs(point.x - pointNext.x), myEditor.getLineHeight()) 
             : new Rectangle(point.x, point.y, 0, myEditor.getLineHeight());
    }

    @Override
    public int viewToModel(JTextComponent tc, Point pt, Position.Bias[] ignored) {
      return viewToModel(tc, pt);
    }

    @Override
    public int getNextVisualPositionFrom(JTextComponent t, int pos, Position.Bias b,
                                         int direction,
                                         Position.Bias[] biasRet) throws BadLocationException {
      notSupported();
      return 0;
    }

    @Override
    public void damageRange(JTextComponent t, int p0, int p1) {
      myEditor.repaint(p0, p1);
    }

    @Override
    public void damageRange(JTextComponent t, int p0, int p1, Position.Bias ignored1, Position.Bias ignored2) {
      damageRange(t, p0, p1);
    }

    @Nullable
    @Override
    public EditorKit getEditorKit(JTextComponent t) {
      notSupported();
      return null;
    }

    @Nullable
    @Override
    public View getRootView(JTextComponent t) {
      notSupported();
      return null;
    }
  }

  private static class TextAccessibleRole extends AccessibleRole {
    // Can't use AccessibleRole.TEXT: The screen reader verbally refers to it as a text field
    // and doesn't do multi-line iteration. (This is hardcoded into the sun/lwawt/macosx implementation.)
    // As you can see from JavaAccessibilityUtilities.m, we should use the exact key "textarea" to get
    // proper text area handling.
    // Note: This is true for MacOS only. For other platform, we need to return the "regular"
    // TEXT role to ensure screen readers behave as expected.
    @SuppressWarnings("SpellCheckingInspection")
    private static final AccessibleRole TEXT_AREA = new TextAccessibleRole("textarea");

    private TextAccessibleRole(String key) {
      super(key);
    }
  }

  private class AccessibleEditorComponentImpl extends AccessibleJComponent
      implements AccessibleText, AccessibleEditableText, AccessibleExtendedText,
                 CaretListener, DocumentListener {

    public AccessibleEditorComponentImpl() {
      if (myEditor.isDisposed()) return;

      myEditor.getCaretModel().addCaretListener(this);
      myEditor.getDocument().addDocumentListener(this);

      Disposer.register(myEditor.getDisposable(), new Disposable() {
        @Override
        public void dispose() {
          myEditor.getCaretModel().removeCaretListener(AccessibleEditorComponentImpl.this);
          myEditor.getDocument().removeDocumentListener(AccessibleEditorComponentImpl.this);
        }
      });
    }

    // ---- Implements CaretListener ----

    private int myCaretPos;

    @Override
    public void caretPositionChanged(CaretEvent e) {
      Caret caret = e.getCaret();
      if (caret == null) {
        return;
      }
      int dot = caret.getOffset();
      int mark = caret.getLeadSelectionOffset();
      if (myCaretPos != dot) {
        ApplicationManager.getApplication().assertIsDispatchThread();
        firePropertyChange(ACCESSIBLE_CARET_PROPERTY,
                           new Integer(myCaretPos), new Integer(dot));

        if (SystemInfo.isMac) {
          // For MacOSX we also need to fire a caret event to anyone listening
          // to our Document, since *that* rather than the accessible property
          // change is the only way to trigger a speech update
          //fireJTextComponentCaretChange(dot, mark);
          fireJTextComponentCaretChange(e);
        }

        myCaretPos = dot;
      }

      if (mark != dot) {
        ApplicationManager.getApplication().assertIsDispatchThread();
        firePropertyChange(ACCESSIBLE_SELECTION_PROPERTY, null,
                           getSelectedText());
      }
    }

    // ---- Implements DocumentListener ----

    @Override
    public void documentChanged(final DocumentEvent event) {
      final Integer pos = event.getOffset();
      if (ApplicationManager.getApplication().isDispatchThread()) {
        firePropertyChange(ACCESSIBLE_TEXT_PROPERTY, null, pos);
        if (SystemInfo.isMac) {
          // For MacOSX we also need to fire a JTextComponent event to anyone listening
          // to our Document, since *that* rather than the accessible property
          // change is the only way to trigger a speech update
          fireJTextComponentDocumentChange(event);
        }
      } else {
        ApplicationManager.getApplication().invokeLater(() -> {
          firePropertyChange(ACCESSIBLE_TEXT_PROPERTY, null, pos);
          fireJTextComponentDocumentChange(event);
        });
      }
    }

    // ---- Implements AccessibleContext ----

    @Nullable
    @Override
    public String getAccessibleName() {
      if (accessibleName != null) {
        return accessibleName;
      }

      VirtualFile file = myEditor.getVirtualFile();
      if (file != null) {
        return "Editor for " + file.getName();
      }
      return "Editor";
    }

    @Override
    public AccessibleRole getAccessibleRole() {
      // See comment on TextAccessibleRole class.
      if (SystemInfo.isMac) {
        return TextAccessibleRole.TEXT_AREA;
      } else {
        return AccessibleRole.TEXT;
      }
    }

    @Override
    public AccessibleText getAccessibleText() {
      if (Disposer.isDisposed(myEditor.getDisposable())) return null;
      return this;
    }

    @Override
    public AccessibleEditableText getAccessibleEditableText() {
      if (Disposer.isDisposed(myEditor.getDisposable())) return null;
      return this;
    }

    @Override
    public AccessibleStateSet getAccessibleStateSet() {
      AccessibleStateSet states = super.getAccessibleStateSet();
      if (myEditor.getDocument().isWritable()) {
        states.add(AccessibleState.EDITABLE);
      }
      states.add(AccessibleState.MULTI_LINE);
      return states;
    }

    // ---- Implements AccessibleText ----

    @Override
    public int getIndexAtPoint(Point point) {
      LogicalPosition logicalPosition = myEditor.xyToLogicalPosition(point);
      return myEditor.logicalPositionToOffset(logicalPosition);
    }

    @Override
    public Rectangle getCharacterBounds(int offset) {
      // Since we report the very end of the document as being 1 character past the document
      // length, we need to validate the offset passed back by the screen reader.
      if (offset < 0 || offset > myEditor.getDocument().getTextLength() - 1) {
        return null;
      }
      LogicalPosition pos = myEditor.offsetToLogicalPosition(offset);
      Point point = myEditor.logicalPositionToXY(pos);
      FontMetrics fontMetrics = myEditor.getFontMetrics(Font.PLAIN);
      char c = myEditor.getDocument().getCharsSequence().subSequence(offset, offset + 1).charAt(0);
      return new Rectangle(point.x, point.y, fontMetrics.charWidth(c), fontMetrics.getHeight());
    }

    @Override
    public int getCharCount() {
      return myEditor.getDocument().getTextLength();
    }

    @Override
    public int getCaretPosition() {
      return myEditor.getCaretModel().getOffset();
    }

    @Nullable
    @Override
    public String getAtIndex(
      @MagicConstant(intValues = {
        AccessibleText.CHARACTER,
        AccessibleText.WORD,
        AccessibleText.SENTENCE})
      int part,
      int index) {
      return getTextAtOffset(part, index, HERE);
    }

    @Nullable
    @Override
    public String getAfterIndex(
      @MagicConstant(intValues = {AccessibleText.CHARACTER, AccessibleText.WORD, AccessibleText.SENTENCE})
      int part,
      int index) {
      return getTextAtOffset(part, index, AFTER);
    }

    @Nullable
    @Override
    public String getBeforeIndex(
      @MagicConstant(intValues = {AccessibleText.CHARACTER, AccessibleText.WORD, AccessibleText.SENTENCE})
      int part,
      int index) {
      return getTextAtOffset(part, index, BEFORE);
    }

    @Override
    public AttributeSet getCharacterAttribute(int index) {
      return new SimpleAttributeSet();
    }

    @Override
    public int getSelectionStart() {
      return myEditor.getSelectionModel().getSelectionStart();
    }

    @Override
    public int getSelectionEnd() {
      return myEditor.getSelectionModel().getSelectionEnd();
    }

    @Nullable
    @Override
    public String getSelectedText() {
      return myEditor.getSelectionModel().getSelectedText();
    }

    // ---- Implements AccessibleEditableText ----

    @Override
    public void setTextContents(String s) {
      setText(s);
    }

    @Override
    public void insertTextAtIndex(int index, String s) {
      editDocumentSafely(index, 0, s);
    }

    @Override
    public String getTextRange(int startIndex, int endIndex) {
      return myEditor.getDocument().getCharsSequence().subSequence(startIndex, endIndex).toString();
    }

    @Override
    public void delete(int startIndex, int endIndex) {
      editDocumentSafely(startIndex, endIndex - startIndex, null);
    }

    @Override
    public void cut(int startIndex, int endIndex) {
      myEditor.getSelectionModel().setSelection(startIndex, endIndex);
      DataContext dataContext = DataManager.getInstance().getDataContext(EditorComponentImpl.this);
      CutProvider cutProvider = myEditor.getCutProvider();
      if (cutProvider.isCutEnabled(dataContext)) {
        cutProvider.performCut(dataContext);
      }
    }

    @Override
    public void paste(int startIndex) {
      myEditor.getCaretModel().moveToOffset(startIndex);
      DataContext dataContext = DataManager.getInstance().getDataContext(EditorComponentImpl.this);
      PasteProvider pasteProvider = myEditor.getPasteProvider();
      if (pasteProvider.isPasteEnabled(dataContext)) {
        pasteProvider.performPaste(dataContext);
      }
    }

    @Override
    public void replaceText(int startIndex, int endIndex, String s) {
      editDocumentSafely(startIndex, endIndex, s);
    }

    @Override
    public void selectText(int startIndex, int endIndex) {
      myEditor.getSelectionModel().setSelection(startIndex, endIndex);
    }

    @Override
    public void setAttributes(int startIndex, int endIndex, AttributeSet as) {
    }

    // ---- Implements AccessibleExtendedText ----

    /** Looking for text before the given offset */
    private static final int BEFORE = -1;
    /** Looking for text at the given offset */
    private static final int HERE = 0;
    /** Looking for text after the given offset */
    private static final int AFTER = 1;

    @Nullable
    @Override
    public AccessibleTextSequence getTextSequenceAt(
      @MagicConstant(intValues = {
        AccessibleText.CHARACTER,
        AccessibleText.WORD,
        AccessibleText.SENTENCE,
        AccessibleExtendedText.LINE,
        AccessibleExtendedText.ATTRIBUTE_RUN})
      int part,
      int index) {
      return getSequenceAtIndex(part, index, HERE);
    }

    @Nullable
    @Override
    public AccessibleTextSequence getTextSequenceAfter(
      @MagicConstant(intValues = {
        AccessibleText.CHARACTER,
        AccessibleText.WORD,
        AccessibleText.SENTENCE,
        AccessibleExtendedText.LINE,
        AccessibleExtendedText.ATTRIBUTE_RUN})
      int part,
      int index) {
      return getSequenceAtIndex(part, index, AFTER);
    }

    @Nullable
    @Override
    public AccessibleTextSequence getTextSequenceBefore(
      @MagicConstant(intValues = {
        AccessibleText.CHARACTER,
        AccessibleText.WORD,
        AccessibleText.SENTENCE,
        AccessibleExtendedText.LINE,
        AccessibleExtendedText.ATTRIBUTE_RUN})
      int part,
      int index) {
      return getSequenceAtIndex(part, index, BEFORE);
    }

    @Override
    @Nullable
    public Rectangle getTextBounds(int startIndex, int endIndex) {
      LogicalPosition startPos = myEditor.offsetToLogicalPosition(startIndex);
      Point startPoint = myEditor.logicalPositionToXY(startPos);
      Rectangle rectangle = new Rectangle(startPoint);

      LogicalPosition endPos = myEditor.offsetToLogicalPosition(endIndex);
      Point endPoint = myEditor.logicalPositionToXY(endPos);
      FontMetrics fontMetrics = myEditor.getFontMetrics(Font.PLAIN);
      char c = myEditor.getDocument().getCharsSequence().subSequence(endIndex - 1, endIndex).charAt(0);
      endPoint.x += fontMetrics.charWidth(c);
      endPoint.y += fontMetrics.getHeight();
      rectangle.add(endPoint);

      return rectangle;
    }

    @Nullable
    private String getTextAtOffset(
      @MagicConstant(intValues = {AccessibleText.CHARACTER, AccessibleText.WORD, AccessibleText.SENTENCE})
      int type,
      int offset,
      @MagicConstant(intValues = {BEFORE, HERE, AFTER})
      int direction) {
      DocumentEx document = myEditor.getDocument();
      if (offset < 0 || offset >= document.getTextLength()) {
        return null;
      }
      switch (type) {
        case AccessibleText.CHARACTER: {
          if (offset + direction < document.getTextLength() && offset + direction >= 0) {
            int startOffset = offset + direction;
            return document.getCharsSequence().subSequence(startOffset, startOffset + 1).toString();
          }
          break;
        }

        case AccessibleText.WORD: {
          int wordStart = getWordAtOffsetStart(offset, direction);
          int wordEnd = getWordAtOffsetEnd(offset, direction);
          if (wordStart == -1 || wordEnd == -1) {
            return null;
          }
          return myEditor.getDocument().getCharsSequence().subSequence(wordStart, wordEnd).toString();
        }

        case AccessibleText.SENTENCE: {
          int lineStart = getLineAtOffsetStart(offset, direction);
          int lineEnd = getLineAtOffsetEnd(offset, direction);
          if (lineStart == -1 || lineEnd == -1) {
            return null;
          }
          return document.getCharsSequence().subSequence(lineStart, lineEnd).toString();
        }

        case AccessibleExtendedText.LINE:
        case AccessibleExtendedText.ATTRIBUTE_RUN:
          // Not expected to be called in this method!
          assert false : type;

        default:
          break;
      }

      return null;
    }

    /**
     * Similar to {@link #getTextAtOffset} but returns an {@link AccessibleTextSequence} and can accept a few more types.
     */
    @Nullable
    private AccessibleTextSequence getSequenceAtIndex(
        @MagicConstant(intValues = {
          AccessibleText.CHARACTER,
          AccessibleText.WORD,
          AccessibleText.SENTENCE,
          AccessibleExtendedText.LINE,
          AccessibleExtendedText.ATTRIBUTE_RUN})
        int type,
        int offset,
        @MagicConstant(intValues = {BEFORE, HERE, AFTER})
        int direction) {
      assert direction == BEFORE || direction == HERE || direction == AFTER;

      DocumentEx document = myEditor.getDocument();
      if (offset < 0 || offset >= document.getTextLength()) {
        return null;
      }

      switch (type) {
        case AccessibleText.CHARACTER:
          AccessibleTextSequence charSequence = null;
          if (offset + direction < document.getTextLength() &&
              offset + direction >= 0) {
            int startOffset = offset + direction;
            charSequence = new AccessibleTextSequence(startOffset, startOffset + 1,
                                         document.getCharsSequence().subSequence(startOffset, startOffset + 1).toString());
          }
          return charSequence;

        case AccessibleExtendedText.ATTRIBUTE_RUN:
        case AccessibleText.WORD: {
          int wordStart = getWordAtOffsetStart(offset, direction);
          int wordEnd = getWordAtOffsetEnd(offset, direction);
          if (wordStart == -1 || wordEnd == -1) {
            return null;
          }
          return new AccessibleTextSequence(wordStart, wordEnd,
                                            document.getCharsSequence().subSequence(wordStart, wordEnd).toString());
        }

        case AccessibleExtendedText.LINE:
        case AccessibleText.SENTENCE: {
          int lineStart = getLineAtOffsetStart(offset, direction);
          int lineEnd = getLineAtOffsetEnd(offset, direction);
          if (lineStart == -1 || lineEnd == -1) {
            return null;
          }

          return new AccessibleTextSequence(lineStart, lineEnd,
                                            document.getCharsSequence().subSequence(lineStart, lineEnd).toString());
        }
      }
      return null;
    }

    private int getLineAtOffsetStart(int offset) {
      Document document = myEditor.getDocument();
      if (offset == 0) {
        return 0;
      }
      int lineNumber = myEditor.offsetToLogicalPosition(offset).line;
      return document.getLineStartOffset(lineNumber);
    }

    private int moveLineOffset(int offset, @MagicConstant(intValues = {BEFORE, HERE, AFTER}) int direction) {
      if (direction == AFTER) {
        int lineNumber = myEditor.offsetToLogicalPosition(offset).line;
        lineNumber++;
        Document document = myEditor.getDocument();
        if (lineNumber == document.getLineCount()) {
          return -1;
        }
        return document.getLineStartOffset(lineNumber);
      } else if (direction == BEFORE) {
        int lineNumber = myEditor.offsetToLogicalPosition(offset).line;
        lineNumber--;
        if (lineNumber < 0) {
          return -1;
        }
        Document document = myEditor.getDocument();
        return document.getLineStartOffset(lineNumber);
      } else {
        assert direction == HERE;
        return offset;
      }
    }

    private int getLineAtOffsetStart(int offset, @MagicConstant(intValues = {BEFORE, HERE, AFTER}) int direction) {
      offset = moveLineOffset(offset, direction);
      if (offset == -1) {
        return -1;
      }

      return getLineAtOffsetStart(offset);
    }

    private int getLineAtOffsetEnd(int offset) {
      Document document = myEditor.getDocument();
      if (offset == 0) {
        return 0;
      }
      int lineNumber = myEditor.offsetToLogicalPosition(offset).line;
      return document.getLineEndOffset(lineNumber);
    }

    private int getLineAtOffsetEnd(int offset, @MagicConstant(intValues = {BEFORE, HERE, AFTER}) int direction) {
      offset = moveLineOffset(offset, direction);
      if (offset == -1) {
        return -1;
      }

      return getLineAtOffsetEnd(offset);
    }

    private int moveWordOffset(int offset, @MagicConstant(intValues = {BEFORE, HERE, AFTER}) int direction) {
      if (direction == AFTER) {
        Document document = myEditor.getDocument();
        CharSequence text = document.getCharsSequence();
        int maxOffset = document.getTextLength();
        int newOffset = offset - 1;
        boolean camel = myEditor.getSettings().isCamelWords();
        for (; newOffset < maxOffset; newOffset++) {
          if (EditorActionUtil.isWordEnd(text, newOffset, camel)) {
            break;
          }
        }
        newOffset++;
        for (; newOffset < maxOffset; newOffset++) {
          if (EditorActionUtil.isWordStart(text, newOffset, camel)) {
            return newOffset;
          }
        }

        return -1;
      } else if (direction == BEFORE) {
        Document document = myEditor.getDocument();
        CharSequence text = document.getCharsSequence();
        int newOffset = offset - 1;
        boolean camel = myEditor.getSettings().isCamelWords();
        for (; newOffset >= 0; newOffset--) {
          if (EditorActionUtil.isWordStart(text, newOffset, camel)) {
            break;
          }
        }
        newOffset--;
        for (; newOffset >= 0; newOffset--) {
          if (EditorActionUtil.isWordEnd(text, newOffset, camel)) {
            return newOffset;
          }
        }

        return -1;
      } else {
        assert direction == HERE;
        return offset;
      }
    }

    private int getWordAtOffsetStart(int offset, @MagicConstant(intValues = {BEFORE, HERE, AFTER}) int direction) {
      offset = moveWordOffset(offset, direction);
      if (offset == -1) {
        return -1;
      }

      return getWordAtOffsetStart(offset);
    }

    private int getWordAtOffsetEnd(int offset, @MagicConstant(intValues = {BEFORE, HERE, AFTER}) int direction) {
      offset = moveWordOffset(offset, direction);
      if (offset == -1) {
        return -1;
      }

      return getWordAtOffsetEnd(offset);
    }

    // Based on CaretImpl#getWordAtCaretStart
    private int getWordAtOffsetStart(int offset) {
      Document document = myEditor.getDocument();
      if (offset == 0) {
        return 0;
      }
      int lineNumber = myEditor.offsetToLogicalPosition(offset).line;
      CharSequence text = document.getCharsSequence();
      int newOffset = offset - 1;
      int minOffset = lineNumber > 0 ? document.getLineEndOffset(lineNumber - 1) : 0;
      boolean camel = myEditor.getSettings().isCamelWords();
      for (; newOffset > minOffset; newOffset--) {
        if (EditorActionUtil.isWordStart(text, newOffset, camel)) {
          break;
        }
      }

      return newOffset;
    }

    // Based on CaretImpl#getWordAtCaretEnd
    private int getWordAtOffsetEnd(int offset) {
      Document document = myEditor.getDocument();

      CharSequence text = document.getCharsSequence();
      if (offset >= document.getTextLength() - 1 || document.getLineCount() == 0) {
        return offset;
      }

      int newOffset = offset + 1;
      int lineNumber = myEditor.offsetToLogicalPosition(offset).line;
      int maxOffset = document.getLineEndOffset(lineNumber);
      if (newOffset > maxOffset) {
        if (lineNumber + 1 >= document.getLineCount()) {
          return offset;
        }
        maxOffset = document.getLineEndOffset(lineNumber + 1);
      }
      boolean camel = myEditor.getSettings().isCamelWords();
      for (; newOffset < maxOffset; newOffset++) {
        if (EditorActionUtil.isWordEnd(text, newOffset, camel)) {
          break;
        }
      }

      return newOffset;
    }
  }
}
