// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.diagnostic.PluginException;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.actions.UndoRedoAction;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.ide.ui.UISettingsUtils;
import com.intellij.internal.inspector.PropertyBean;
import com.intellij.internal.inspector.UiInspectorContextProvider;
import com.intellij.internal.inspector.UiInspectorPreciseContextProvider;
import com.intellij.internal.inspector.UiInspectorUtil;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.UiCompatibleDataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.WriteIntentReadAction;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diagnostic.ThrottledLogger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorCoreUtil;
import com.intellij.openapi.editor.EditorThreading;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ReadOnlyFragmentModificationException;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actions.ChangeEditorFontSizeStrategy;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.ex.util.EditorUIUtil;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.EditorsSplittersKt;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.ui.TypingTarget;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.DirtyUI;
import com.intellij.ui.Grayer;
import com.intellij.ui.components.Magnificator;
import com.intellij.ui.paint.PaintUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.ui.EdtInvocationManager;
import com.intellij.util.ui.JBSwingUtilities;
import com.intellij.util.ui.accessibility.ScreenReader;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.text.JTextComponent;
import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusListener;
import java.awt.event.InputMethodEvent;
import java.awt.event.InputMethodListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.im.InputMethodRequests;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@DirtyUI
public final class EditorComponentImpl extends JTextComponent implements Scrollable, UiCompatibleDataProvider, Queryable, TypingTarget, Accessible,
                                                                         UISettingsListener, UiInspectorPreciseContextProvider,
                                                                         UiInspectorContextProvider {
  private static final Logger LOG = Logger.getInstance(EditorComponentImpl.class);
  private static final ThrottledLogger THROTTLED_LOGGER = new ThrottledLogger(LOG, TimeUnit.HOURS.toMillis(1));
  private static final ThreadLocal<PluginDescriptor> currentDescriptor = ThreadLocal.withInitial(() -> null);

  private final EditorImpl editor;
  private final EditorAccessibleText myAccessibleText;

  private @NotNull Point2D alignment = new Point2D.Double();

  public EditorComponentImpl(@NotNull EditorImpl editor) {
    this.editor = editor;
    myAccessibleText = new EditorAccessibleText(editor);
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
        boolean isChangePersistent = EditorComponentImpl.this.editor.getSettings().isWheelFontChangePersistent();
        float defaultFontSize;
        if (isChangePersistent) {
          defaultFontSize = UISettings.getInstance().getFontSize();
        }
        else {
          defaultFontSize = EditorColorsManager.getInstance().getGlobalScheme().getEditorFontSize2D();
        }

        float size = Math.max((float)(currentSize * scale), defaultFontSize);

        var strategy = EditorComponentImpl.this.editor.getUserData(ChangeEditorFontSizeStrategy.KEY);
        if (strategy != null) {
          strategy.setFontSize(size);
          var zoomPoint = strategy.preferredZoomPointRelative(EditorComponentImpl.this.editor);
          var area = EditorComponentImpl.this.editor.getScrollingModel().getVisibleArea();
          return new Point(area.x + zoomPoint.x, area.y + zoomPoint.y);
        }

        EditorComponentImpl.this.editor.setFontSize(size);
        if (isChangePersistent) {
          // Has to be performed with the correct modality because there's an
          // EditorColorsManagerImpl.dropPsiCaches call inside, and it requires a write-safe context.
          ApplicationManager.getApplication().invokeLater(() -> {
            EditorComponentImpl.this.editor.adjustGlobalFontSize(UISettingsUtils.scaleFontSize(size, 1 / UISettingsUtils.getInstance().getCurrentIdeScale()));
          }, ModalityState.stateForComponent(EditorComponentImpl.this));
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

    LogicalPosition pressedLocation = editor.myLastMousePressedLocation;
    LogicalPosition location = pressedLocation != null ? pressedLocation : editor.getCaretModel().getLogicalPosition();
    boolean virtualSpace = EditorCoreUtil.inVirtualSpace(editor, location);
    // IJPL-52267: log whenever EDITOR_VIRTUAL_SPACE is derived from a retained mouse-press location instead
    // of the caret. When the press location is virtual but the caret is not, this is precisely the value that
    // wrongly disables keyboard Go To Declaration / Find Usages — correlate the "press #N" with the
    // GotoDeclarationAction:trace "reason=virtualSpace" line and the focusLost trace for the same press.
    if (pressedLocation != null && EditorImpl.MOUSE_PRESS_LOG.isTraceEnabled()) {
      LogicalPosition caret = editor.getCaretModel().getLogicalPosition();
      EditorImpl.MOUSE_PRESS_LOG.trace("[press #" + editor.myMousePressSeq + "] EDITOR_VIRTUAL_SPACE served from press location=" +
                                       pressedLocation + " virtualSpace=" + virtualSpace +
                                       " caret=" + caret + " caretVirtualSpace=" + EditorCoreUtil.inVirtualSpace(editor, caret) +
                                       " ageMs=" + (System.nanoTime() - editor.myMousePressTimestampNanos) / 1_000_000);
    }
    sink.set(CommonDataKeys.EDITOR_VIRTUAL_SPACE, virtualSpace);
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
    WriteIntentReadAction.run(() -> {
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
    });

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
    EdtInvocationManager.invokeLaterIfNeeded(() -> WriteIntentReadAction.run(() -> editor.type(text).notify(result)));
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
    EditorUIUtil.setupEditorPainting(gg, editor.useEditorAntialiasing());
    AffineTransform origTx = PaintUtil.alignTxToInt(gg, PaintUtil.insets2offset(getInsets()), true, false, PaintUtil.RoundingMode.FLOOR);
    if (!editor.isStickyLinePainting() && !editor.isPaintingDumbBuffer()) { // sticky lines and the offscreen dumb buffer might have a different alignment from the main area
      alignment = ObjectUtils.notNull(PaintUtil.getUserSpacePixelOffset(gg), Point2D.Double::new);
    }

    ApplicationManagerEx.getApplicationEx().withLocksSoftlyProhibited(
      "The Read/Write lock is disallowed during paint. Usage of the R/W lock can lead to UI freezes.\n" +
      "Consider using `PsiVersioningService.freezePsiVersion` for accessing PSI trees"
      , t -> {
        PluginDescriptor violatingDescriptor = currentDescriptor.get();
        Throwable exception;
        if (violatingDescriptor != null) {
          exception = new PluginException(t, violatingDescriptor.getPluginId());
        } else {
          exception = t;
        }
        THROTTLED_LOGGER.error(exception);
      }, () -> {
      editor.paint(gg);
      return null;
    });

    if (origTx != null) {
      gg.setTransform(origTx);
    }

    Project project = editor.getProject();
    if (project != null) {
      EditorsSplittersKt.stopOpenFilesActivity(project);
    }
  }

  @ApiStatus.Internal
  public static void withStoredDescriptor(@Nullable PluginDescriptor descriptor, @NotNull Runnable action) {
    PluginDescriptor previousValue = currentDescriptor.get();
    currentDescriptor.set(descriptor);
    try {
      action.run();
    } finally {
      currentDescriptor.set(previousValue);
    }
  }

  /**
   * Returns the offset from the nearest device pixel coordinate to the editor painting origin.
   * <p>
   *   Note that the current implementation always aligns X to an integer coordinate, so only the Y can be non-zero.
   *   But as this can change at any moment, it's best to use both coordinates.
   * </p>
   * @return the unscaled (user space) offset used for the last painting operation, zero if no painting was performed yet or no fractional scaling is used
   * @see PaintUtil#getUserSpacePixelOffset(Graphics2D)
   */
  @ApiStatus.Internal
  public @NotNull Point2D getCurrentAlignment() {
    return alignment;
  }

  public void repaintEditorComponent(int x, int y, int width, int height) {
    int topOverhang = Math.max(0, editor.myView.getTopOverhang());
    int bottomOverhang = Math.max(0, editor.myView.getBottomOverhang());
    repaint(x, y - topOverhang, width, height + topOverhang + bottomOverhang);
  }

  @Override
  public void repaint(long tm, int x, int y, int width, int height) {
    ApplicationManager.getApplication().invokeLater(() -> {
      editor.invalidateAnimationCaches(new Rectangle(x, y, width, height));
    });
    super.repaint(tm, x, y, width, height);
  }

  @ApiStatus.Internal
  public void repaintCaret(int x, int y, int width, int height) {
    super.repaint(0L, x, y, width, height);
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
    return EditorThreading.compute(
      () -> editor.getScrollableIncrementProvider().getScrollableUnitIncrement(editor, visibleRect, orientation, direction)
    );
  }

  @DirtyUI
  @Override
  public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
    return editor.getScrollableIncrementProvider().getScrollableBlockIncrement(editor, visibleRect, orientation, direction);
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

    myEditorSwingCaretUpdatesCourier = new EditorSwingCaretUpdatesCourier(this);

    return true;
  }

  @Override
  public void addCaretListener(CaretListener listener) {
    super.addCaretListener(listener);
    setupEditorSwingCaretUpdatesCourierIfRequired();
  }

  void fireSwingCaretUpdate(@NotNull CaretEvent event) {
    fireCaretUpdate(event);
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
      accessibleContext = new EditorAccessibleContextDelegate(this);
    }
    return accessibleContext;
  }

  private void setupJTextComponentContext() {
    setDocument(new EditorAccessibilityDocument(editor, myAccessibleText));
    setCaret(new EditorAccessibilityCaret(editor));
  }

  @NotNull EditorAccessibleText getEditorAccessibleText() {
    return myAccessibleText;
  }

  @NotNull EditorAccessibilityDocument getAccessibilityDocument() {
    return (EditorAccessibilityDocument)getDocument();
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
    return EditorThreading.compute(() -> myAccessibleText.getCaretOffset(editor.getCaretModel().getCurrentCaret()));
  }

  @DirtyUI
  @Override
  public void updateUI() {
    EditorThreading.run(() -> {
      // Don't use the default TextUI, BaseTextUI, which does a lot of unnecessary
      // work. We do however need to provide a TextUI implementation since some
      // screen reader support code will invoke it
      setUI(new EditorAccessibilityTextUI(this));
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
    editDocumentSafely(editor, 0, editor.getDocument().getTextLength(), text);
  }

  /** Inserts, removes or replaces the given text at the given offset */
  static void editDocumentSafely(@NotNull EditorImpl editor, final int offset, final int length, final @Nullable String text) {
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
      return new UiInspectorInfo("EmbeddedEditorInlay", result, null);
    }
    return null;
  }

  @Override
  public @NotNull List<PropertyBean> getUiInspectorContext() {
    return List.of(new PropertyBean("Creation/Disposal Trace", editor.getDisposalStackTrace(), editor.isDisposed()));
  }
}
