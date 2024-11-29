// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.ide.CutProvider;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.PasteProvider;
import com.intellij.ide.actions.UndoRedoAction;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.ide.ui.UISettingsUtils;
import com.intellij.internal.inspector.PropertyBean;
import com.intellij.internal.inspector.UiInspectorPreciseContextProvider;
import com.intellij.internal.inspector.UiInspectorUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.*;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actions.CaretStop;
import com.intellij.openapi.editor.actions.CaretStopPolicy;
import com.intellij.openapi.editor.actions.EditorActionUtil;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.ex.util.EditorUIUtil;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.EditorsSplittersKt;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.ui.TypingTarget;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DirtyUI;
import com.intellij.ui.Grayer;
import com.intellij.ui.components.Magnificator;
import com.intellij.ui.paint.PaintUtil;
import com.intellij.ui.paint.PaintUtil.RoundingMode;
import com.intellij.util.ArrayUtil;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.ui.EdtInvocationManager;
import com.intellij.util.ui.JBSwingUtilities;
import com.intellij.util.ui.accessibility.AccessibleContextDelegateWithContextMenu;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;
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
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@DirtyUI
public final class EditorComponentImpl extends JTextComponent implements Scrollable, UiCompatibleDataProvider, Queryable, TypingTarget, Accessible,
                                                                         UISettingsListener, UiInspectorPreciseContextProvider {
  private static final Logger LOG = Logger.getInstance(EditorComponentImpl.class);

  private final EditorImpl editor;

  public EditorComponentImpl(@NotNull EditorImpl editor) {
    this.editor = editor;
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
        if (EditorComponentImpl.this.editor.isDisposed()) return at;
        VisualPosition magnificationPosition = EditorComponentImpl.this.editor.xyToVisualPosition(at);
        float currentSize = EditorComponentImpl.this.editor.getColorsScheme().getEditorFontSize2D();
        boolean isChangePersistent = EditorSettingsExternalizable.getInstance().isWheelFontChangePersistent();
        float defaultFontSize;
        if (isChangePersistent) {
          defaultFontSize = UISettings.getInstance().getFontSize();
        }
        else {
          defaultFontSize = EditorColorsManager.getInstance().getGlobalScheme().getEditorFontSize2D();
        }

        float size = Math.max((float)(currentSize * scale), defaultFontSize);
        EditorComponentImpl.this.editor.setFontSize(size);
        if (isChangePersistent) {
          EditorComponentImpl.this.editor.adjustGlobalFontSize(UISettingsUtils.scaleFontSize(size, 1 / UISettingsUtils.getInstance().getCurrentIdeScale()));
        }

        return EditorComponentImpl.this.editor.visualPositionToXY(magnificationPosition);
      }
    });
    putClientProperty(UndoRedoAction.IGNORE_SWING_UNDO_MANAGER, Boolean.TRUE);

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

    setupEditorSwingCaretUpdatesCourierIfRequired();
  }

  @Override
  public void uiSettingsChanged(@NotNull UISettings uiSettings) {
    UISettingsUtils settingsUtils = UISettingsUtils.with(uiSettings);
    if (uiSettings.getPresentationMode() && editor.getFontSize() != settingsUtils.getPresentationModeFontSize()) {
      editor.setFontSize(settingsUtils.getPresentationModeFontSize());
    }
  }

  @DirtyUI
  @Override
  public void paint(@NotNull Graphics g) {
    if (!isEnabled()) {
      g = new Grayer((Graphics2D)g, getBackground());
    }
    super.paint(g);
  }

  public @NotNull EditorImpl getEditor() {
    return editor;
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    if (editor.isDisposed()) return;
    if (editor.isRendererMode()) return;

    sink.set(CommonDataKeys.EDITOR, editor);
    sink.set(CommonDataKeys.CARET, editor.getCaretModel().getCurrentCaret());

    LogicalPosition location = editor.myLastMousePressedLocation;
    if (location == null) {
        location = editor.getCaretModel().getLogicalPosition();
    }
    sink.set(CommonDataKeys.EDITOR_VIRTUAL_SPACE, EditorCoreUtil.inVirtualSpace(editor, location));
  }

  @DirtyUI
  @Override
  public Color getBackground() {
    return editor.getBackgroundColor();
  }

  @DirtyUI
  @Override
  public Dimension getPreferredSize() {
    return editor.getPreferredSize();
  }

  @Override
  public void setCursor(Cursor cursor) {
    super.setCursor(cursor);
    editor.myCursorSetExternally = true;
    if (LOG.isDebugEnabled()) {
      LOG.debug("Mouse cursor set to " + cursor + " in " + editor, new Throwable());
    }
  }

  void fireResized() {
    processComponentEvent(new ComponentEvent(this, ComponentEvent.COMPONENT_RESIZED));
  }

  @Override
  protected void processInputMethodEvent(InputMethodEvent e) {
    if (EditorImpl.EVENT_LOG.isDebugEnabled()) {
      EditorImpl.EVENT_LOG.debug(e.toString());
    }
    // Don't dispatch to super first; now that EditorComponentImpl is a JTextComponent,
    // this would have the side effect of invoking Swing document machinery which relies
    // on creating Document positions etc (and won't update the document in an IntelliJ
    // safe way, such as running through all the carets etc.).
    // First try to handle the event using the default editor logic, then dispatch to
    // `super.processInputMethodEvent(e)`, which in turn will call the listeners and
    // if still not consumed, handle the event by the default JTextComponent logic.
    //    super.processInputMethodEvent(e);

    if (!e.isConsumed() && !editor.isDisposed()) {
      InputMethodListener listener = editor.getInputMethodSupport().getListener();
      switch (e.getID()) {
        case InputMethodEvent.INPUT_METHOD_TEXT_CHANGED:
          listener.inputMethodTextChanged(e);
          break;
        case InputMethodEvent.CARET_POSITION_CHANGED:
          listener.caretPositionChanged(e);
          break;
      }
    }

    super.processInputMethodEvent(e);
  }

  @Override
  public boolean isEditable() {
    // Prevent `javax.swing.text.JTextComponent.processInputMethodEvent` from handling
    // input method events by default Swing logic for viewer editors.
    return !editor.isViewer();
  }

  @Override
  public ActionCallback type(String text) {
    ActionCallback result = new ActionCallback();
    EdtInvocationManager.invokeLaterIfNeeded(() -> WriteIntentReadAction.run((Runnable)() -> editor.type(text).notify(result)));
    return result;
  }

  @Override
  public @Nullable InputMethodRequests getInputMethodRequests() {
    return IdeEventQueue.getInstance().isInputMethodEnabled() ? editor.getInputMethodSupport().getInputMethodRequestsSwingWrapper() : null;
  }

  @DirtyUI
  @Override
  protected Graphics getComponentGraphics(Graphics graphics) {
    return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(graphics));
  }

  @DirtyUI
  @Override
  public void paintComponent(Graphics g) {
    editor.measureTypingLatency();

    Graphics2D gg = (Graphics2D)g;
    if (editor.useEditorAntialiasing()) {
      EditorUIUtil.setupAntialiasing(gg);
    }
    else {
      UISettings.setupAntialiasing(gg);
    }
    gg.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, UISettings.getEditorFractionalMetricsHint());
    AffineTransform origTx = PaintUtil.alignTxToInt(gg, PaintUtil.insets2offset(getInsets()), true, false, RoundingMode.FLOOR);
    editor.paint(gg);
    if (origTx != null) {
      gg.setTransform(origTx);
    }

    Project project = editor.getProject();
    if (project != null) {
      EditorsSplittersKt.stopOpenFilesActivity(project);
    }
  }

  public void repaintEditorComponent(int x, int y, int width, int height) {
    int topOverhang = Math.max(0, editor.myView.getTopOverhang());
    int bottomOverhang = Math.max(0, editor.myView.getBottomOverhang());
    repaint(x, y - topOverhang, width, height + topOverhang + bottomOverhang);
  }

  //--implementation of Scrollable interface--------------------------------------
  @DirtyUI
  @Override
  public Dimension getPreferredScrollableViewportSize() {
    return editor.getPreferredSize();
  }

  @DirtyUI
  @Override
  public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
    return ReadAction.compute(() -> {
      if (orientation == SwingConstants.VERTICAL) {
        return editor.getLineHeight();
      }
      // if orientation == SwingConstants.HORIZONTAL
      return EditorUtil.getSpaceWidth(Font.PLAIN, editor);
    });
  }

  @DirtyUI
  @Override
  public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
    if (orientation == SwingConstants.VERTICAL) {
      int lineHeight = editor.getLineHeight();
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
  public void putInfo(@NotNull Map<? super String, ? super String> info) {
    editor.putInfo(info);
  }

  @Override
  public @NonNls String toString() {
    return "EditorComponent file=" + editor.getVirtualFile();
  }


  // -----------------------------------------------------------------------------------------------
  // Fixes behavior of JTextComponent caret API.
  // Without this, changes of caret(s) position(s) are not reported to the caret listeners added
  //   via JTextComponent.addCaretListener.
  // This is required for proper working of JBR-2460.
  // -----------------------------------------------------------------------------------------------
  private EditorSwingCaretUpdatesCourier myEditorSwingCaretUpdatesCourier = null;

  @SuppressWarnings("UnusedReturnValue")
  @RequiresEdt
  private boolean setupEditorSwingCaretUpdatesCourierIfRequired() {
    if ((myEditorSwingCaretUpdatesCourier != null) || (editor == null) || (editor.isDisposed()) ) {
      return false;
    }

    if (ArrayUtil.isEmpty(getCaretListeners())) {
      return false;
    }

    myEditorSwingCaretUpdatesCourier = EditorSwingCaretUpdatesCourier.create(this);

    return true;
  }

  @Override
  public void addCaretListener(javax.swing.event.CaretListener listener) {
    super.addCaretListener(listener);
    setupEditorSwingCaretUpdatesCourierIfRequired();
  }


  /**
   * How it works:<br>
   * 1. if {@link #beforeAllCaretsAction} / {@link #beforeDocumentChange(Document)} gets called, then {@link #fireCaretUpdate}
   *    will be called ONLY after {@link #afterAllCaretsAction} / {@link #afterDocumentChange(Document)} will have been called respectively
   *    (if the primary caret position will have been changed)<br>
   * 2. otherwise, if any of {@link #documentChanged}, {@link #caretPositionChanged}, {@link #caretAdded}, {@link #caretRemoved} gets called,
   *    {@link #fireCaretUpdate} will be called as well (again, if the primary caret position will have been changed)
   *
   * <p/>
   *
   * Why we need all these listeners:<br>
   * -  if we don't install {@link CaretListener}, we'll miss some caret updates which arrive outside of {@link CaretActionListener} events,
   *    e.g. the test {@link com.intellij.openapi.editor.impl.EditorComponentCaretListenerTest#testCaretNotificationsCausedByUndo testCaretNotificationsCausedByUndo}
   *    won't get a notification after pasting a text into the editor;<br>
   * -  if we don't install {@link CaretActionListener}, we'll sometimes get incorrect position for {@link javax.swing.event.CaretEvent#getMark()},
   *    because position of the caret can be updated a bit earlier than the selection.
   *    E.g. the test {@link com.intellij.openapi.editor.impl.EditorComponentCaretListenerTest#testCaretNotificationsOfSelectionMovementsWithoutTextModificationsFromTopLeft testCaretNotificationsOfSelectionMovementsWithoutTextModificationsFromTopLeft}
   *    will get the wrong position of the selection at the first moving of the caret to the right;<br>
   * -  if we don't install {@link DocumentListener}, we'll miss caret movements caused by document changes. See {@link CaretListener#caretPositionChanged} for more info.
   *    E.g. the test {@link com.intellij.openapi.editor.impl.EditorComponentCaretListenerTest#testCaretNotificationsCausedByUndo testCaretNotificationsCausedByUndo}
   *    won't get a notification after undoing the pasting
   */
  private final class EditorSwingCaretUpdatesCourier implements CaretListener, CaretActionListener, BulkAwareDocumentListener.Simple {
    /** true if {@link #beforeAllCaretsAction} has been called, but {@link #afterAllCaretsAction} - has still not */
    private boolean isInsideCaretsAction = false;
    private boolean isInsideBulkDocumentUpdate = false;
    private @NotNull WeakReference<Caret> myLastKnownPrimaryCaret;
    private int myPrimaryCaretLastKnownDot;
    private int myPrimaryCaretLastKnownMark;

    @RequiresEdt
    private static @Nullable EditorSwingCaretUpdatesCourier create(@NotNull EditorComponentImpl parent) {
      if ((parent.editor == null) || (parent.editor.isDisposed()) ) {
        return null;
      }

      return parent.new EditorSwingCaretUpdatesCourier();
    }

    /** Don't use it directly, use {@link #create(EditorComponentImpl)} instead */
    @RequiresEdt
    private EditorSwingCaretUpdatesCourier() {
      assert(editor != null);
      assert(!editor.isDisposed());

      final @NotNull var caretModel = editor.getCaretModel();
      final @NotNull var primaryCaret = caretModel.getPrimaryCaret();

      myLastKnownPrimaryCaret = new WeakReference<>(primaryCaret);
      myPrimaryCaretLastKnownDot = primaryCaret.getOffset();
      myPrimaryCaretLastKnownMark = primaryCaret.getLeadSelectionOffset();

      caretModel.addCaretActionListener(this, editor.getDisposable());
      caretModel.addCaretListener(this, editor.getDisposable());
      editor.getDocument().addDocumentListener(this, editor.getDisposable());
    }


    // ---- CaretActionListener ----

    @Override
    @RequiresEdt
    public void beforeAllCaretsAction() {
      isInsideCaretsAction = true;
    }

    @Override
    @RequiresEdt
    public void afterAllCaretsAction() {
      isInsideCaretsAction = false;

      if (isInsideBulkUpdate()) {
        return;
      }

      final var currentPrimaryCaret = editor.getCaretModel().getPrimaryCaret();
      primaryCaretPositionPossiblyChanged(currentPrimaryCaret);
    }


    // ---- CaretListener ----

    @Override
    @RequiresEdt
    public void caretPositionChanged(@NotNull CaretEvent event) {
      if (isInsideBulkUpdate()) {
        return;
      }

      final Caret changedCaret = event.getCaret();
      final Caret currentPrimaryCaret = editor.getCaretModel().getPrimaryCaret();

      if (changedCaret != currentPrimaryCaret) {
        // Filter out changes of secondary carets
        return;
      }

      primaryCaretPositionPossiblyChanged(currentPrimaryCaret);
    }

    @Override
    @RequiresEdt
    public void caretAdded(@NotNull CaretEvent event) {
      // Adding a caret may cause a change of the primary caret instance

      if (isInsideBulkUpdate()) {
        return;
      }

      final Caret addedCaret = event.getCaret();
      final Caret currentPrimaryCaret = editor.getCaretModel().getPrimaryCaret();

      if (addedCaret != currentPrimaryCaret) {
        // The added caret hasn't become primary, so we're not interested in its position
        return;
      }

      primaryCaretPositionPossiblyChanged(currentPrimaryCaret);
    }

    @Override
    @RequiresEdt
    public void caretRemoved(@NotNull CaretEvent event) {
      // Removing a caret may cause a switching of the primary caret

      if (isInsideBulkUpdate()) {
        return;
      }

      final var currentPrimaryCaret = editor.getCaretModel().getPrimaryCaret();

      if (myLastKnownPrimaryCaret.refersTo(currentPrimaryCaret)) {
        // The removal of a caret didn't cause a switching of the primary caret, so its position isn't supposed to have changed
        return;
      }

      primaryCaretPositionPossiblyChanged(currentPrimaryCaret);
    }


    // ---- BulkAwareDocumentListener ----

    @Override
    @RequiresEdt
    public void beforeDocumentChange(@NotNull DocumentEvent event) {
      isInsideBulkDocumentUpdate = true;
    }

    @Override
    @RequiresEdt
    public void afterDocumentChange(@NotNull Document document) {
      isInsideBulkDocumentUpdate = false;

      if (isInsideBulkUpdate()) {
        return;
      }

      final Caret currentPrimaryCaret = editor.getCaretModel().getPrimaryCaret();
      primaryCaretPositionPossiblyChanged(currentPrimaryCaret);
    }


    // ---- implementation details ----

    @RequiresEdt
    private boolean isInsideBulkUpdate() {
      return isInsideCaretsAction || isInsideBulkDocumentUpdate;
    }

    @RequiresEdt // if you're going to remove this requirement, don't forget to make access to the fields thread-safe
    private void primaryCaretPositionPossiblyChanged(final @NotNull Caret currentPrimaryCaret) {
      final int currentPrimaryCaretDot = currentPrimaryCaret.getOffset();
      final int currentPrimaryCaretMark = currentPrimaryCaret.getLeadSelectionOffset();

      if (!myLastKnownPrimaryCaret.refersTo(currentPrimaryCaret)) {
        myLastKnownPrimaryCaret.clear();
        myLastKnownPrimaryCaret = new WeakReference<>(currentPrimaryCaret);
      }

      if (currentPrimaryCaretDot < 0) {
        LOG.error(
          "currentPrimaryCaretDot < 0",
          String.format("currentPrimaryCaretDot == %d", currentPrimaryCaretDot),
          String.format("currentPrimaryCaret == %s", currentPrimaryCaret)
        );
      }
      else if (currentPrimaryCaretMark < 0) { // I don't think it worth adding almost the same error twice, so it's an else-if
        LOG.error(
          "currentPrimaryCaretMark < 0",
          String.format("currentPrimaryCaretMark == %d", currentPrimaryCaretMark),
          String.format("currentPrimaryCaret == %s", currentPrimaryCaret)
        );
      }

      if ((myPrimaryCaretLastKnownDot == currentPrimaryCaretDot) && (myPrimaryCaretLastKnownMark == currentPrimaryCaretMark)) {
        // The position hasn't changed
        return;
      }

      myPrimaryCaretLastKnownDot = currentPrimaryCaretDot;
      myPrimaryCaretLastKnownMark = currentPrimaryCaretMark;

      fireCaretUpdate(new javax.swing.event.CaretEvent(EditorComponentImpl.this) {
        @Override
        public int getDot() {
          return currentPrimaryCaretDot;
        }

        @Override
        public int getMark() {
          return currentPrimaryCaretMark;
        }
      });
    }
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
      accessibleContext = new EditorAccessibleContextDelegate();
    }
    return accessibleContext;
  }

  private void setupJTextComponentContext() {
    setDocument(new EditorAccessibilityDocument());
    setCaret(new EditorAccessibilityCaret());
  }

  /**
   * @deprecated We're inheriting method now in order to support accessibility, but you
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
    return ReadAction.compute(() -> editor.getCaretModel().getOffset());
  }

  @DirtyUI
  @Override
  public void updateUI() {
    ReadAction.run(() -> {
      // Don't use the default TextUI, BaseTextUI, which does a lot of unnecessary
      // work. We do however need to provide a TextUI implementation since some
      // screen reader support code will invoke it
      setUI(new EditorAccessibilityTextUI());
      UISettings.setupEditorAntialiasing(this);
      // myEditor is null when updateUI() is called from parent's constructor
      putClientProperty(RenderingHints.KEY_FRACTIONALMETRICS, UISettings.getEditorFractionalMetricsHint());
      invalidate();
    });
  }

  @Override
  public String getToolTipText(MouseEvent event) {
    // Undo effect of JTextComponent superclass: this is the default JComponent implementation
    return this.getToolTipText();
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

      @Override
      public @Nullable ElementChange getChange(Element element) {
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
  private final class EditorAccessibilityDocument implements javax.swing.text.Document, javax.swing.text.Element {
    private List<javax.swing.event.DocumentListener> myListeners;

    public @Nullable List<javax.swing.event.DocumentListener> getListeners() {
      return myListeners;
    }

    @Override
    public int getLength() {
      return editor.getDocument().getTextLength();
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
    public @Nullable Object getProperty(Object o) {
      return null;
    }

    @Override
    public void putProperty(Object o, Object o1) {
    }

    @Override
    public void remove(final int offset, final int length) {
      editDocumentSafely(offset, length, null);
    }

    @Override
    public void insertString(final int offset, final String text, AttributeSet attributeSet) {
      editDocumentSafely(offset, 0, text);
    }

    @Override
    public String getText(final int offset, final int length) {
      return ReadAction
        .compute(() -> editor.getDocument().getText(new TextRange(offset, offset + length)));
    }

    @Override
    public void getText(int offset, int length, Segment segment) {
      char[] s = getText(offset, length).toCharArray();
      segment.array = s;
      segment.offset = 0;
      segment.count = s.length;
    }

    @Override
    public @Nullable Position getStartPosition() {
      notSupported();
      return null;
    }

    @Override
    public @Nullable Position getEndPosition() {
      notSupported();
      return null;
    }

    @Override
    public @Nullable Position createPosition(int i) {
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

    @Override
    public @Nullable Element getParentElement() {
      return null;
    }

    @Override
    public @Nullable String getName() {
      return null;
    }

    @Override
    public @Nullable AttributeSet getAttributes() {
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
      Document document = editor.getDocument();
      return document.getLineNumber(i);
    }

    @Override
    public int getElementCount() {
      Document document = editor.getDocument();
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

        @Override
        public @Nullable String getName() {
          return null;
        }

        @Override
        public @Nullable AttributeSet getAttributes() {
          return null;
        }

        @Override
        public int getStartOffset() {
          Document document = editor.getDocument();
          return document.getLineStartOffset(i);
        }

        @Override
        public int getEndOffset() {
          Document document = editor.getDocument();
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

        @Override
        public @Nullable Element getElement(int i) {
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
  public String getSelectedText() {
    return editor.getSelectionModel().getSelectedText(true);
  }

  @Override
  public void select(int startOffset, int endOffset) {
    // This method is called by the JDK on macOS, when the IME asks us to replace already committed text.
    // Doing this is a bit complicated, since we have to account for multi-caret.

    String selectedText = editor.getDocument().getText(new TextRange(startOffset, endOffset));

    int length = endOffset - startOffset;
    int offsetRelativeToCurrentCaret = startOffset - editor.getCaretModel().getCurrentCaret().getOffset();

    boolean allCaretsHaveTheSameText = true;

    for (var caret : editor.getCaretModel().getAllCarets()) {
      var caretStart = offsetRelativeToCurrentCaret + caret.getOffset();
      var caretEnd = caretStart + length;
      String caretText = editor.getDocument().getText(new TextRange(caretStart, caretEnd));
      if (!selectedText.equals(caretText)) {
        allCaretsHaveTheSameText = false;
        break;
      }
    }

    if (allCaretsHaveTheSameText) {
      for (var caret : editor.getCaretModel().getAllCarets()) {
        var caretStart = offsetRelativeToCurrentCaret + caret.getOffset();
        var caretEnd = caretStart + length;
        caret.setSelection(caretStart, caretEnd);
      }
    } else {
      // Fallback to the default implementation
      editor.getSelectionModel().setSelection(startOffset, endOffset);
    }
  }

  @DirtyUI
  @Override
  public void setText(String text) {
    editDocumentSafely(0, editor.getDocument().getTextLength(), text);
  }

  /** Inserts, removes or replaces the given text at the given offset */
  private void editDocumentSafely(final int offset, final int length, final @Nullable String text) {
    Document document = editor.getDocument();
    RangeMarker marker = document.createRangeMarker(offset, offset + length);

    TransactionGuard.submitTransaction(editor.getDisposable(), () -> {
      Project project = editor.getProject();
      if (!marker.isValid() || !FileDocumentManager.getInstance().requestWriting(document, project)) {
        marker.dispose();
        return;
      }

      CommandProcessor.getInstance().executeCommand(project, () -> WriteAction.run(() -> {
        document.startGuardedBlockChecking();
        try {
          document.replaceString(marker.getStartOffset(), marker.getEndOffset(), StringUtil.notNullize(text));
        }
        catch (ReadOnlyFragmentModificationException e) {
          EditorActionManager.getInstance().getReadonlyFragmentModificationHandler(document).handle(e);
        }
        finally {
          document.stopGuardedBlockChecking();
          marker.dispose();
        }
      }), "", document, UndoConfirmationPolicy.DEFAULT, document);
    });
  }

  /** {@linkplain DefaultCaret} does a lot of work we don't want (listening
   * for focus events etc). This exists simply to be able to send caret events to the screen reader. */
  private final class EditorAccessibilityCaret implements javax.swing.text.Caret {
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

    @Override
    public @Nullable Point getMagicCaretPosition() {
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
      return ReadAction.compute(() -> editor.getCaretModel().getOffset());
    }

    @Override
    public int getMark() {
      return ReadAction.compute(() -> editor.getSelectionModel().getSelectionStart());
    }

    @Override
    public void setDot(int offset) {
      if (!editor.isDisposed()) {
        editor.getCaretModel().moveToOffset(offset);
      }
    }

    @Override
    public void moveDot(int offset) {
      if (!editor.isDisposed()) {
        editor.getCaretModel().moveToOffset(offset);
      }
    }
  }

  @Override
  public @Nullable UiInspectorPreciseContextProvider.UiInspectorInfo getUiInspectorContext(@NotNull MouseEvent event) {
    Point point = event.getPoint();
    Inlay<?> inlay = editor.getInlayModel().getElementAt(point);
    if (inlay != null) {
      List<PropertyBean> result = new ArrayList<>();
      result.add(new PropertyBean("Inlay Renderer", inlay.getRenderer(), true));
      result.add(new PropertyBean("Inlay Renderer Class", UiInspectorUtil.getClassPresentation(inlay.getRenderer()), true));
      if (inlay.getGutterIconRenderer() != null) {
        result.add(new PropertyBean("Inlay Gutter Renderer", inlay.getGutterIconRenderer(), true));
      }
      result.add(new PropertyBean("Inlay Properties", inlay.getProperties()));
      return new UiInspectorInfo("EditorInlay", result, null);
    }
    return null;
  }

  /**
   * Specialized TextUI intended *only* for accessibility usage. Not all the methods are called; only viewToModel, not modelToView.
   */
  private final class EditorAccessibilityTextUI extends TextUI {
    @Override
    public @NotNull Rectangle modelToView(JTextComponent tc, int offset) {
      return modelToView(tc, offset, Position.Bias.Forward);
    }

    @Override
    public int viewToModel(JTextComponent tc, Point pt) {
      LogicalPosition logicalPosition = editor.xyToLogicalPosition(pt);
      return editor.logicalPositionToOffset(logicalPosition);
    }

    @Override
    public @NotNull Rectangle modelToView(JTextComponent tc, int offset, Position.Bias bias) {
      LogicalPosition pos = editor.offsetToLogicalPosition(offset).leanForward(bias == Position.Bias.Forward);
      LogicalPosition posNext = editor.offsetToLogicalPosition(bias == Position.Bias.Forward ? offset + 1 : offset - 1)
        .leanForward(bias != Position.Bias.Forward);
      Point point = editor.logicalPositionToXY(pos);
      Point pointNext = editor.logicalPositionToXY(posNext);
      return point.y == pointNext.y
             ? new Rectangle(Math.min(point.x, pointNext.x), point.y, Math.abs(point.x - pointNext.x), editor.getLineHeight())
             : new Rectangle(point.x, point.y, 0, editor.getLineHeight());
    }

    @Override
    public int viewToModel(JTextComponent tc, Point pt, Position.Bias[] ignored) {
      return viewToModel(tc, pt);
    }

    @Override
    public int getNextVisualPositionFrom(JTextComponent t, int pos, Position.Bias b,
                                         int direction,
                                         Position.Bias[] biasRet) {
      notSupported();
      return 0;
    }

    @Override
    public void damageRange(JTextComponent t, int p0, int p1) {
      editor.repaint(p0, p1);
    }

    @Override
    public void damageRange(JTextComponent t, int p0, int p1, Position.Bias ignored1, Position.Bias ignored2) {
      damageRange(t, p0, p1);
    }

    @Override
    public @NotNull EditorKit getEditorKit(JTextComponent t) {
      notSupported();
      return null;
    }

    @Override
    public @NotNull View getRootView(JTextComponent t) {
      notSupported();
      return null;
    }
  }

  private static final class TextAccessibleRole extends AccessibleRole {
    // Can't use AccessibleRole.TEXT: The screen reader verbally refers to it as a text field
    // and doesn't do multi-line iteration. (This is hardcoded into the sun/lwawt/macosx implementation.)
    // As you can see from JavaAccessibilityUtilities.m, we should use the exact key "textarea" to get
    // proper text area handling.
    // Note: This is true for MacOS only. For other platform, we need to return the "regular"
    // TEXT role to ensure screen readers behave as expected.
    @SuppressWarnings("SpellCheckingInspection")
    private static final AccessibleRole TEXT_AREA = new TextAccessibleRole("textarea");

    private TextAccessibleRole(@NonNls String key) {
      super(key);
    }
  }

  private final class AccessibleEditorComponentImpl extends AccessibleJComponent
      implements AccessibleText, AccessibleEditableText, AccessibleExtendedText, AccessibleAction,
                 CaretListener, DocumentListener {

    AccessibleEditorComponentImpl() {
      if (editor.isDisposed()) return;

      editor.getCaretModel().addCaretListener(this, editor.getDisposable());
      editor.getDocument().addDocumentListener(this);

      Disposer.register(editor.getDisposable(), new Disposable() {
        @Override
        public void dispose() {
          editor.getDocument().removeDocumentListener(AccessibleEditorComponentImpl.this);
        }
      });
    }

    // ---- Implements CaretListener ----

    private int myCaretPos;
    private int myPreviousCaretPos;


    @Override
    public void caretPositionChanged(@NotNull CaretEvent e) {
      Caret caret = e.getCaret();
      if (caret != editor.getCaretModel().getPrimaryCaret()) {
        return;
      }
      int dot = caret.getOffset();
      int mark = caret.getLeadSelectionOffset();
      if (myCaretPos != dot) {
        ThreadingAssertions.assertEventDispatchThread();
        firePropertyChange(ACCESSIBLE_CARET_PROPERTY,
                           Integer.valueOf(myCaretPos), Integer.valueOf(dot));
        myPreviousCaretPos = myCaretPos;
        myCaretPos = dot;
      }

      if (mark != dot) {
        ThreadingAssertions.assertEventDispatchThread();
        firePropertyChange(ACCESSIBLE_SELECTION_PROPERTY, null,
                           getSelectedText());
      }
    }

    // ---- Implements DocumentListener ----

    @Override
    public void documentChanged(final @NotNull DocumentEvent event) {
      final Integer pos = event.getOffset();
      if (ApplicationManager.getApplication().isDispatchThread()) {
        firePropertyChange(ACCESSIBLE_TEXT_PROPERTY, null, pos);
        // Fire caret changed event when the document changes because caretPositionChanged might not be called in some cases
        // (e.g., when deleting text or adding/removing tab indentation, see CaretListener#caretPositionChanged).
        firePropertyChange(ACCESSIBLE_CARET_PROPERTY, null, pos);
        if (SystemInfo.isMac) {
          // For MacOSX we also need to fire a JTextComponent event to anyone listening
          // to our Document, since *that* rather than the accessible property
          // change is the only way to trigger a speech update
          fireJTextComponentDocumentChange(event);
        }
      } else {
        ApplicationManager.getApplication().invokeLater(() -> {
          firePropertyChange(ACCESSIBLE_TEXT_PROPERTY, null, pos);
          firePropertyChange(ACCESSIBLE_CARET_PROPERTY, null, pos);
          fireJTextComponentDocumentChange(event);
        });
      }
    }

    // ---- Implements AccessibleContext ----

    @Override
    public @Nullable String getAccessibleName() {
      if (accessibleName != null) {
        return accessibleName;
      }

      VirtualFile file = editor.getVirtualFile();
      if (file != null) {
        return EditorBundle.message("editor.for.file.accessible.name", file.getName());
      }
      return EditorBundle.message("editor.accessible.name");
    }

    @Override
    public String getAccessibleDescription() {
      String description = super.getAccessibleDescription();
      if (description == null && StringUtil.isEmpty(getText())) {
        //noinspection HardCodedStringLiteral
        CharSequence emptyText = getEditor().getPlaceholder();
        if (emptyText != null && !emptyText.isEmpty()) {
          return AccessibleContextUtil.getUniqueDescription(this, emptyText.toString());
        }
      }
      return description;
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
      if (editor.isDisposed()) return null;
      return this;
    }

    @Override
    public AccessibleEditableText getAccessibleEditableText() {
      if (editor.isDisposed()) return null;
      return this;
    }

    @Override
    public AccessibleStateSet getAccessibleStateSet() {
      AccessibleStateSet states = super.getAccessibleStateSet();
      if (editor.getDocument().isWritable()) {
        states.add(AccessibleState.EDITABLE);
      }
      states.add(AccessibleState.MULTI_LINE);
      return states;
    }

    // ---- Implements AccessibleText ----

    @Override
    public int getIndexAtPoint(Point point) {
      LogicalPosition logicalPosition = editor.xyToLogicalPosition(point);
      return editor.logicalPositionToOffset(logicalPosition);
    }

    @Override
    public Rectangle getCharacterBounds(int offset) {
      // Since we report the very end of the document as being 1 character past the document
      // length, we need to validate the offset passed back by the screen reader.
      if (offset < 0 || offset > editor.getDocument().getTextLength() - 1) {
        return null;
      }
      LogicalPosition pos = editor.offsetToLogicalPosition(offset);
      Point point = editor.logicalPositionToXY(pos);
      FontMetrics fontMetrics = editor.getFontMetrics(Font.PLAIN);
      char c = editor.getDocument().getCharsSequence().subSequence(offset, offset + 1).charAt(0);
      return new Rectangle(point.x, point.y, fontMetrics.charWidth(c), fontMetrics.getHeight());
    }

    @Override
    public int getCharCount() {
      return editor.getDocument().getTextLength();
    }

    @Override
    public int getCaretPosition() {
      return editor.getCaretModel().getOffset();
    }

    @Override
    public @Nullable String getAtIndex(
      @MagicConstant(intValues = {
        AccessibleText.CHARACTER,
        AccessibleText.WORD,
        AccessibleText.SENTENCE})
      int part,
      int index) {
      return getTextAtOffset(part, index, HERE);
    }

    @Override
    public @Nullable String getAfterIndex(
      @MagicConstant(intValues = {AccessibleText.CHARACTER, AccessibleText.WORD, AccessibleText.SENTENCE})
      int part,
      int index) {
      return getTextAtOffset(part, index, AFTER);
    }

    @Override
    public @Nullable String getBeforeIndex(
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
      return ReadAction.compute(() -> editor.getSelectionModel().getSelectionStart());
    }

    @Override
    public int getSelectionEnd() {
      return ReadAction.compute(() -> editor.getSelectionModel().getSelectionEnd());
    }

    @Override
    public @Nullable String getSelectedText() {
      return ReadAction.compute(() -> editor.getSelectionModel().getSelectedText());
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
      return editor.getDocument().getCharsSequence().subSequence(startIndex, endIndex).toString();
    }

    @Override
    public void delete(int startIndex, int endIndex) {
      editDocumentSafely(startIndex, endIndex - startIndex, null);
    }

    @Override
    public void cut(int startIndex, int endIndex) {
      editor.getSelectionModel().setSelection(startIndex, endIndex);
      DataContext dataContext = DataManager.getInstance().getDataContext(EditorComponentImpl.this);
      CutProvider cutProvider = editor.getCutProvider();
      if (cutProvider.isCutEnabled(dataContext)) {
        cutProvider.performCut(dataContext);
      }
    }

    @Override
    public void paste(int startIndex) {
      editor.getCaretModel().moveToOffset(startIndex);
      DataContext dataContext = DataManager.getInstance().getDataContext(EditorComponentImpl.this);
      PasteProvider pasteProvider = editor.getPasteProvider();
      if (pasteProvider.isPasteEnabled(dataContext)) {
        pasteProvider.performPaste(dataContext);
      }
    }

    @Override
    public void replaceText(int startIndex, int endIndex, String s) {
      editDocumentSafely(startIndex, endIndex - startIndex, s);
    }

    @Override
    public void selectText(int startIndex, int endIndex) {
      editor.getSelectionModel().setSelection(startIndex, endIndex);
      editor.getCaretModel().moveToOffset(endIndex);
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

    @Override
    public @Nullable AccessibleTextSequence getTextSequenceAt(
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

    @Override
    public @Nullable AccessibleTextSequence getTextSequenceAfter(
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

    @Override
    public @Nullable AccessibleTextSequence getTextSequenceBefore(
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
    public @Nullable Rectangle getTextBounds(int startIndex, int endIndex) {
      LogicalPosition startPos = editor.offsetToLogicalPosition(startIndex);
      Point startPoint = editor.logicalPositionToXY(startPos);
      Rectangle rectangle = new Rectangle(startPoint);

      LogicalPosition endPos = editor.offsetToLogicalPosition(endIndex);
      Point endPoint = editor.logicalPositionToXY(endPos);
      FontMetrics fontMetrics = editor.getFontMetrics(Font.PLAIN);
      char c = editor.getDocument().getCharsSequence().subSequence(endIndex - 1, endIndex).charAt(0);
      endPoint.x += fontMetrics.charWidth(c);
      endPoint.y += fontMetrics.getHeight();
      rectangle.add(endPoint);

      return rectangle;
    }

    // ---- Implements AccessibleAction ----

    @Override
    public int getAccessibleActionCount(){
      return 0;
    }

    @Override
    public String getAccessibleActionDescription(int i){
      return null;
    }

    @Override
    public boolean doAccessibleAction(int i) {
      return false;
    }

    private @Nullable String getTextAtOffset(
      @MagicConstant(intValues = {AccessibleText.CHARACTER, AccessibleText.WORD, AccessibleText.SENTENCE})
      int type,
      int offset,
      @MagicConstant(intValues = {BEFORE, HERE, AFTER})
      int direction) {
      DocumentEx document = editor.getDocument();
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
          var word = getWordOrLexeme(offset, direction);
          return word == null ? null : word.text;
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
    private @Nullable AccessibleTextSequence getSequenceAtIndex(
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

      DocumentEx document = editor.getDocument();
      if (offset < 0 || offset >= document.getTextLength()) {
        return null;
      }

      switch (type) {
        case AccessibleText.CHARACTER -> {
          AccessibleTextSequence charSequence = null;
          if (offset + direction < document.getTextLength() &&
              offset + direction >= 0) {
            int startOffset = offset + direction;
            charSequence = new AccessibleTextSequence(startOffset, startOffset + 1,
                                                      document.getCharsSequence().subSequence(startOffset, startOffset + 1).toString());
          }
          return charSequence;
        }
        case AccessibleExtendedText.ATTRIBUTE_RUN, AccessibleText.WORD -> {
          return getWordOrLexeme(offset, direction);
        }
        case AccessibleExtendedText.LINE, AccessibleText.SENTENCE -> {
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
      Document document = editor.getDocument();
      if (offset == 0) {
        return 0;
      }
      int lineNumber = editor.offsetToLogicalPosition(offset).line;
      return document.getLineStartOffset(lineNumber);
    }

    private int moveLineOffset(int offset, @MagicConstant(intValues = {BEFORE, HERE, AFTER}) int direction) {
      if (direction == AFTER) {
        int lineNumber = editor.offsetToLogicalPosition(offset).line;
        lineNumber++;
        Document document = editor.getDocument();
        if (lineNumber == document.getLineCount()) {
          return -1;
        }
        return document.getLineStartOffset(lineNumber);
      } else if (direction == BEFORE) {
        int lineNumber = editor.offsetToLogicalPosition(offset).line;
        lineNumber--;
        if (lineNumber < 0) {
          return -1;
        }
        Document document = editor.getDocument();
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
      Document document = editor.getDocument();
      if (offset == 0) {
        return 0;
      }
      int lineNumber = editor.offsetToLogicalPosition(offset).line;
      return document.getLineEndOffset(lineNumber);
    }

    private int getLineAtOffsetEnd(int offset, @MagicConstant(intValues = {BEFORE, HERE, AFTER}) int direction) {
      offset = moveLineOffset(offset, direction);
      if (offset == -1) {
        return -1;
      }

      return getLineAtOffsetEnd(offset);
    }

    private CaretStopPolicy resolveCaretStopPolicy(@MagicConstant(intValues = {BEFORE, HERE, AFTER}) int direction) {
      var caretStopOptions = EditorSettingsExternalizable.getInstance().getCaretStopOptions();
      switch (direction) {
        case AFTER -> {
          return caretStopOptions.getForwardPolicy();
        }
        case BEFORE -> {
          return caretStopOptions.getBackwardPolicy();
        }
        case HERE -> {
          return myCaretPos - myPreviousCaretPos >= 0 ? caretStopOptions.getForwardPolicy() : caretStopOptions.getBackwardPolicy();
        }
      }
      return caretStopOptions.getForwardPolicy();
    }

    private AccessibleTextSequence getWordOrLexeme(int offset, @MagicConstant(intValues = {BEFORE, HERE, AFTER}) int direction) {
      boolean isCamel = editor.getSettings().isCamelWords();
      var caretStopPolicy = resolveCaretStopPolicy(direction);
      offset = moveWordOffset(offset, direction, caretStopPolicy, isCamel);
      var wordStop = caretStopPolicy.getWordStop();
      int wordStart = getWordAtOffsetStart(offset, wordStop, isCamel);
      int wordEnd = getWordAtOffsetEnd(offset, wordStop, isCamel);
      if (wordStart == -1 || wordEnd == -1 || wordStart > wordEnd) {
        return null;
      }
      return new AccessibleTextSequence(wordStart, wordEnd,
                                        editor.getDocument().getCharsSequence().subSequence(wordStart, wordEnd).toString());
    }


    private int moveWordOffset(int offset,
                               @MagicConstant(intValues = {BEFORE, HERE, AFTER}) int direction,
                               CaretStopPolicy stopPolicy,
                               boolean isCamel) {
      if (direction == AFTER) {
        return EditorActionUtil.getNextCaretStopOffset(editor, stopPolicy, isCamel);
      }
      if (direction == BEFORE) {
        return EditorActionUtil.getPreviousCaretStopOffset(editor, stopPolicy, isCamel);
      }
      return offset;
    }

    private int getWordAtOffsetStart(int offset, CaretStop wordStop, boolean isCamel) {
      if (offset == 0) {
        return 0;
      }
      if (wordStop.isAtEnd() && !wordStop.isAtStart()) {
        if (EditorActionUtil.isWordOrLexemeEnd(editor, offset, isCamel)) {
          return getWordAtOffsetStart(offset - 1, isCamel);
        }
        if (offset == getLineAtOffsetStart(offset)) {
          return offset - 1;
        }
      }
      if (Character.isWhitespace(editor.getDocument().getText().charAt(offset))) {
        return offset;
      }
      return getWordAtOffsetStart(offset, isCamel);
    }

    // Based on CaretImpl#getWordAtCaretStart
    private int getWordAtOffsetStart(int offset, boolean isCamel) {
      Document document = editor.getDocument();
      if (offset == 0) {
        return 0;
      }
      int lineNumber = editor.offsetToLogicalPosition(offset).line;
      int newOffset = offset;
      int minOffset = lineNumber > 0 ? document.getLineEndOffset(lineNumber - 1) : 0;
      for (; newOffset > minOffset; newOffset--) {
        if (EditorActionUtil.isWordOrLexemeStart(editor, newOffset, isCamel)) {
          break;
        }
      }

      return newOffset;
    }

    private int getWordAtOffsetEnd(int offset, CaretStop stopWord, boolean isCamel) {
      if (stopWord.isAtStart()) {
        if (Character.isWhitespace(editor.getDocument().getText().charAt(offset))) {
          return offset + 1;
        }
        return getWordAtOffsetEnd(offset + 1, isCamel);
      }
      if (offset == getLineAtOffsetStart(offset)) {
        return offset;
      }
      return getWordAtOffsetEnd(offset, isCamel);
    }


    // Based on CaretImpl#getWordAtCaretEnd
    private int getWordAtOffsetEnd(int offset, boolean isCamel) {
      Document document = editor.getDocument();
      if (offset >= document.getTextLength() - 1 || document.getLineCount() == 0) {
        return offset;
      }
      int newOffset = offset;
      int lineNumber = editor.offsetToLogicalPosition(offset).line;
      int maxOffset = document.getLineEndOffset(lineNumber);
      if (newOffset > maxOffset) {
        if (lineNumber + 1 >= document.getLineCount()) {
          return offset;
        }
        maxOffset = document.getLineEndOffset(lineNumber + 1);
      }
      for (; newOffset < maxOffset; newOffset++) {
        if (EditorActionUtil.isWordOrLexemeEnd(editor, newOffset, isCamel)) {
          break;
        }
      }

      return newOffset;
    }
  }

  private final class EditorAccessibleContextDelegate extends AccessibleContextDelegateWithContextMenu implements AccessibleText {
    public EditorAccessibleContextDelegate() { super(new AccessibleEditorComponentImpl()); }

    @Override
    protected void doShowContextMenu() {
      ActionManager.getInstance().tryToExecute(ActionManager.getInstance().getAction("ShowPopupMenu"), null, EditorComponentImpl.this.getEditor().getContentComponent(), null, true);
    }

    @Override
    protected Container getDelegateParent() {
      return getParent();
    }

    @Override
    public int getIndexAtPoint(Point point) {
      return ((AccessibleText) getDelegate()).getIndexAtPoint(point);
    }

    @Override
    public Rectangle getCharacterBounds(int i) {
      return ((AccessibleText) getDelegate()).getCharacterBounds(i);
    }

    @Override
    public int getCharCount() {
      return ((AccessibleText) getDelegate()).getCharCount();
    }

    @Override
    public int getCaretPosition() {
      return ((AccessibleText) getDelegate()).getCaretPosition();
    }

    @Override
    public String getAtIndex(int part, int index) {
      return ((AccessibleText) getDelegate()).getAtIndex(part, index);
    }

    @Override
    public String getAfterIndex(int part, int index) {
      return ((AccessibleText) getDelegate()).getAfterIndex(part, index);
    }

    @Override
    public String getBeforeIndex(int part, int index) {
      return ((AccessibleText) getDelegate()).getBeforeIndex(part, index);
    }

    @Override
    public AttributeSet getCharacterAttribute(int i) {
      return ((AccessibleText) getDelegate()).getCharacterAttribute(i);
    }

    @Override
    public int getSelectionStart() {
      return ((AccessibleText) getDelegate()).getSelectionStart();
    }

    @Override
    public int getSelectionEnd() {
      return ((AccessibleText) getDelegate()).getSelectionEnd();
    }

    @Override
    public String getSelectedText() {
      return ((AccessibleText) getDelegate()).getSelectedText();
    }
  }
}
