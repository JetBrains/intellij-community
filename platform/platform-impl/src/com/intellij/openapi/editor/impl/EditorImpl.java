/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl;

import com.intellij.application.options.EditorFontsConstants;
import com.intellij.codeInsight.hint.DocumentFragmentTooltipRenderer;
import com.intellij.codeInsight.hint.EditorFragmentComponent;
import com.intellij.codeInsight.hint.TooltipController;
import com.intellij.codeInsight.hint.TooltipGroup;
import com.intellij.concurrency.JobScheduler;
import com.intellij.diagnostic.Dumpable;
import com.intellij.ide.*;
import com.intellij.ide.dnd.DnDManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.application.TransactionGuardImpl;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.*;
import com.intellij.openapi.editor.colors.*;
import com.intellij.openapi.editor.colors.impl.DelegateColorScheme;
import com.intellij.openapi.editor.colors.impl.FontPreferencesImpl;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.*;
import com.intellij.openapi.editor.ex.util.EditorUIUtil;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.ex.util.EmptyEditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterClient;
import com.intellij.openapi.editor.impl.event.MarkupModelListener;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapAppliancePlaces;
import com.intellij.openapi.editor.impl.view.EditorView;
import com.intellij.openapi.editor.markup.GutterDraggableObject;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.fileEditor.impl.EditorsSplitters;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.AbstractPainter;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeGlassPane;
import com.intellij.openapi.wm.IdeGlassPaneUtil;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.impl.IdeBackgroundUtil;
import com.intellij.openapi.wm.impl.IdeGlassPaneImpl;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLayeredPane;
import com.intellij.ui.components.JBScrollBar;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.mac.MacGestureSupportForEditor;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.ui.*;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.intellij.lang.annotations.JdkConstants;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.Border;
import javax.swing.plaf.ScrollBarUI;
import javax.swing.plaf.ScrollPaneUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.*;
import java.awt.font.TextHitInfo;
import java.awt.geom.Point2D;
import java.awt.im.InputMethodRequests;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.lang.reflect.Field;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.text.CharacterIterator;
import java.util.*;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class EditorImpl extends UserDataHolderBase implements EditorEx, HighlighterClient, Queryable, Dumpable {
  private static final int MIN_FONT_SIZE = 8;
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.impl.EditorImpl");
  private static final Key DND_COMMAND_KEY = Key.create("DndCommand");
  @NonNls
  public static final Object IGNORE_MOUSE_TRACKING = "ignore_mouse_tracking";
  private static final Key<JComponent> PERMANENT_HEADER = Key.create("PERMANENT_HEADER");
  public static final Key<Boolean> DO_DOCUMENT_UPDATE_TEST = Key.create("DoDocumentUpdateTest");
  public static final Key<Boolean> FORCED_SOFT_WRAPS = Key.create("forced.soft.wraps");
  public static final Key<Boolean> SOFT_WRAPS_EXIST = Key.create("soft.wraps.exist");
  private static final Key<Boolean> DISABLE_CARET_POSITION_KEEPING = Key.create("editor.disable.caret.position.keeping");
  static final Key<Boolean> DISABLE_CARET_SHIFT_ON_WHITESPACE_INSERTION = Key.create("editor.disable.caret.shift.on.whitespace.insertion");
  private static final boolean HONOR_CAMEL_HUMPS_ON_TRIPLE_CLICK = Boolean.parseBoolean(System.getProperty("idea.honor.camel.humps.on.triple.click"));
  private static final Key<BufferedImage> BUFFER = Key.create("buffer");
  @NotNull private final DocumentEx myDocument;

  private final JPanel myPanel;
  @NotNull private final JScrollPane myScrollPane;
  @NotNull private final EditorComponentImpl myEditorComponent;
  @NotNull private final EditorGutterComponentImpl myGutterComponent;
  private final TraceableDisposable myTraceableDisposable = new TraceableDisposable(true);

  private static final Cursor EMPTY_CURSOR;

  static {
    Cursor emptyCursor = null;
    if (!GraphicsEnvironment.isHeadless()) {
      try {
        emptyCursor = Toolkit.getDefaultToolkit().createCustomCursor(UIUtil.createImage(1, 1, BufferedImage.TYPE_INT_ARGB),
                                                                     new Point(),
                                                                     "Empty cursor");
      }
      catch (Exception e){
        LOG.warn("Couldn't create an empty cursor", e);
      }
    }
    EMPTY_CURSOR = emptyCursor;
  }

  private final CommandProcessor myCommandProcessor;
  @NotNull private final MyScrollBar myVerticalScrollBar;

  private final List<EditorMouseListener> myMouseListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  @NotNull private final List<EditorMouseMotionListener> myMouseMotionListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  private boolean myIsInsertMode = true;

  @NotNull private final CaretCursor myCaretCursor;
  private final ScrollingTimer myScrollingTimer = new ScrollingTimer();

  @SuppressWarnings("RedundantStringConstructorCall")
  private final Object MOUSE_DRAGGED_GROUP = new String("MouseDraggedGroup");

  @NotNull private final SettingsImpl mySettings;

  private boolean isReleased;

  @Nullable private MouseEvent myMousePressedEvent;
  @Nullable private MouseEvent myMouseMovedEvent;
  
  private final MouseListener myMouseListener = new MyMouseAdapter();
  private final MouseMotionListener myMouseMotionListener = new MyMouseMotionListener();

  /**
   * Holds information about area where mouse was pressed.
   */
  @Nullable private EditorMouseEventArea myMousePressArea;
  private int mySavedSelectionStart = -1;
  private int mySavedSelectionEnd   = -1;

  private final PropertyChangeSupport myPropertyChangeSupport = new PropertyChangeSupport(this);
  private MyEditable myEditable;

  @NotNull
  private EditorColorsScheme myScheme;
  private boolean myIsViewer;
  @NotNull private final SelectionModelImpl mySelectionModel;
  @NotNull private final EditorMarkupModelImpl myMarkupModel;
  @NotNull private final EditorFilteringMarkupModelEx myDocumentMarkupModel;
  @NotNull private final MarkupModelListener myMarkupModelListener;

  @NotNull private final FoldingModelImpl myFoldingModel;
  @NotNull private final ScrollingModelImpl myScrollingModel;
  @NotNull private final CaretModelImpl myCaretModel;
  @NotNull private final SoftWrapModelImpl mySoftWrapModel;
  @NotNull private final InlayModelImpl myInlayModel;

  @NotNull private static final RepaintCursorCommand ourCaretBlinkingCommand = new RepaintCursorCommand();
  private final DocumentBulkUpdateListener myBulkUpdateListener;

  @MouseSelectionState
  private int myMouseSelectionState;
  @Nullable private FoldRegion myMouseSelectedRegion;

  @MagicConstant(intValues = {MOUSE_SELECTION_STATE_NONE, MOUSE_SELECTION_STATE_LINE_SELECTED, MOUSE_SELECTION_STATE_WORD_SELECTED})
  private @interface MouseSelectionState {}
  private static final int MOUSE_SELECTION_STATE_NONE          = 0;
  private static final int MOUSE_SELECTION_STATE_WORD_SELECTED = 1;
  private static final int MOUSE_SELECTION_STATE_LINE_SELECTED = 2;

  private volatile EditorHighlighter myHighlighter; // updated in EDT, but can be accessed from other threads (under read action)
  private Disposable myHighlighterDisposable = Disposer.newDisposable();
  private final TextDrawingCallback myTextDrawingCallback = new MyTextDrawingCallback();

  @MagicConstant(intValues = {VERTICAL_SCROLLBAR_LEFT, VERTICAL_SCROLLBAR_RIGHT})
  private int         myScrollBarOrientation;
  private boolean     myMousePressedInsideSelection;

  private boolean myUpdateCursor;
  private int myCaretUpdateVShift;

  @Nullable
  private final Project myProject;
  private long myMouseSelectionChangeTimestamp;
  private int mySavedCaretOffsetForDNDUndoHack;
  private final List<FocusChangeListener> myFocusListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  private MyInputMethodHandler myInputMethodRequestsHandler;
  private InputMethodRequests myInputMethodRequestsSwingWrapper;
  private boolean myIsOneLineMode;
  private boolean myIsRendererMode;
  private VirtualFile myVirtualFile;
  private boolean myIsColumnMode;
  @Nullable private Color myForcedBackground;
  @Nullable private Dimension myPreferredSize;

  private final Alarm myMouseSelectionStateAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
  private Runnable myMouseSelectionStateResetRunnable;

  private boolean myEmbeddedIntoDialogWrapper;
  private int myDragOnGutterSelectionStartLine = -1;
  private RangeMarker myDraggedRange;

  @NotNull private final JPanel myHeaderPanel;

  @Nullable private MouseEvent myInitialMouseEvent;
  private boolean myIgnoreMouseEventsConsecutiveToInitial;

  private EditorDropHandler myDropHandler;

  private Condition<RangeHighlighter> myHighlightingFilter;

  @NotNull private final IndentsModel myIndentsModel;

  @Nullable
  private CharSequence myPlaceholderText;
  @Nullable private TextAttributes myPlaceholderAttributes;
  private boolean myShowPlaceholderWhenFocused;

  private boolean myStickySelection;
  private int myStickySelectionStart;
  private boolean myScrollToCaret = true;

  private boolean myPurePaintingMode;
  private boolean myPaintSelection;

  private final EditorSizeAdjustmentStrategy mySizeAdjustmentStrategy = new EditorSizeAdjustmentStrategy();
  private final Disposable myDisposable = Disposer.newDisposable();

  private List<CaretState> myCaretStateBeforeLastPress;
  LogicalPosition myLastMousePressedLocation;
  private VisualPosition myTargetMultiSelectionPosition;
  private boolean myMultiSelectionInProgress;
  private boolean myRectangularSelectionInProgress;
  private boolean myLastPressCreatedCaret;
  // Set when the selection (normal or block one) initiated by mouse drag becomes noticeable (at least one character is selected).
  // Reset on mouse press event.
  private boolean myCurrentDragIsSubstantial;

  private CaretImpl myPrimaryCaret;

  public final boolean myDisableRtl = Registry.is("editor.disable.rtl");
  final EditorView myView;

  private boolean myCharKeyPressed;
  private boolean myNeedToSelectPreviousChar;

  private boolean myDocumentChangeInProgress;
  private boolean myErrorStripeNeedsRepaint;

  private String myContextMenuGroupId = IdeActions.GROUP_BASIC_EDITOR_POPUP;

  private boolean myUseEditorAntialiasing = true;

  private final ImmediatePainter myImmediatePainter;

  static {
    ourCaretBlinkingCommand.start();
  }

  private volatile int myExpectedCaretOffset = -1;

  private boolean myBackgroundImageSet;

  private final EditorKind myKind;

  EditorImpl(@NotNull Document document, boolean viewer, @Nullable Project project, @NotNull EditorKind kind) {
    assertIsDispatchThread();
    myProject = project;
    myDocument = (DocumentEx)document;
    myScheme = createBoundColorSchemeDelegate(null);
    myScrollPane = new MyScrollPane(); // create UI after scheme initialization
    myIsViewer = viewer;
    myKind = kind;
    mySettings = new SettingsImpl(this, project, kind);
    if (!mySettings.isUseSoftWraps() && shouldSoftWrapsBeForced()) {
      mySettings.setUseSoftWrapsQuiet();
      putUserData(FORCED_SOFT_WRAPS, Boolean.TRUE);
    }

    MarkupModelEx documentMarkup = (MarkupModelEx)DocumentMarkupModel.forDocument(myDocument, myProject, true);

    mySelectionModel = new SelectionModelImpl(this);
    myMarkupModel = new EditorMarkupModelImpl(this);
    myDocumentMarkupModel = new EditorFilteringMarkupModelEx(this, documentMarkup);
    myFoldingModel = new FoldingModelImpl(this);
    myCaretModel = new CaretModelImpl(this);
    myCaretModel.initCarets();
    myScrollingModel = new ScrollingModelImpl(this);
    myInlayModel = new InlayModelImpl(this);
    Disposer.register(myCaretModel, myInlayModel);
    mySoftWrapModel = new SoftWrapModelImpl(this);

    myCommandProcessor = CommandProcessor.getInstance();

    myImmediatePainter = new ImmediatePainter(this);

    if (myDocument instanceof DocumentImpl) {
      myBulkUpdateListener = new EditorDocumentBulkUpdateAdapter();
      ((DocumentImpl)myDocument).addInternalBulkModeListener(myBulkUpdateListener);
    }
    else {
      myBulkUpdateListener = null;
    }

    myMarkupModelListener = new MarkupModelListener() {
      private boolean areRenderersInvolved(@NotNull RangeHighlighterEx highlighter) {
        return highlighter.getCustomRenderer() != null ||
               highlighter.getGutterIconRenderer() != null ||
               highlighter.getLineMarkerRenderer() != null ||
               highlighter.getLineSeparatorRenderer() != null;
      }
      @Override
      public void afterAdded(@NotNull RangeHighlighterEx highlighter) {
        attributesChanged(highlighter, areRenderersInvolved(highlighter),
                          EditorUtil.attributesImpactFontStyleOrColor(highlighter.getTextAttributes()));
      }

      @Override
      public void beforeRemoved(@NotNull RangeHighlighterEx highlighter) {
        attributesChanged(highlighter, areRenderersInvolved(highlighter),
                          EditorUtil.attributesImpactFontStyleOrColor(highlighter.getTextAttributes()));
      }

      @Override
      public void attributesChanged(@NotNull RangeHighlighterEx highlighter, boolean renderersChanged, boolean fontStyleOrColorChanged) {
        if (myDocument.isInBulkUpdate()) return; // bulkUpdateFinished() will repaint anything

        if (renderersChanged) {
          updateGutterSize();
        }

        boolean errorStripeNeedsRepaint = renderersChanged || highlighter.getErrorStripeMarkColor() != null;
        if (myDocumentChangeInProgress) {
          // postpone repaint request, as folding model can be in inconsistent state and so coordinate
          // conversions might give incorrect results
          myErrorStripeNeedsRepaint |= errorStripeNeedsRepaint;
          return;
        }

        int textLength = myDocument.getTextLength();

        int start = Math.min(Math.max(highlighter.getAffectedAreaStartOffset(), 0), textLength);
        int end = Math.min(Math.max(highlighter.getAffectedAreaEndOffset(), 0), textLength);

        int startLine = start == -1 ? 0 : myDocument.getLineNumber(start);
        int endLine = end == -1 ? myDocument.getLineCount() : myDocument.getLineNumber(end);
        if (start != end && fontStyleOrColorChanged) {
          myView.invalidateRange(start, end);
        }
        if (!myFoldingModel.isInBatchFoldingOperation()) { // at the end of batch folding operation everything is repainted
          repaintLines(Math.max(0, startLine - 1), Math.min(endLine + 1, getDocument().getLineCount()));
        }

        // optimization: there is no need to repaint error stripe if the highlighter is invisible on it
        if (errorStripeNeedsRepaint) {
          if (myFoldingModel.isInBatchFoldingOperation()) {
            myErrorStripeNeedsRepaint = true;
          }
          else {
            myMarkupModel.repaint(start, end);
          }
        }

        updateCaretCursor();
      }
    };

    getFilteredDocumentMarkupModel().addMarkupModelListener(myCaretModel, myMarkupModelListener);
    getMarkupModel().addMarkupModelListener(myCaretModel, myMarkupModelListener);

    myDocument.addDocumentListener(myFoldingModel, myCaretModel);
    myDocument.addDocumentListener(myCaretModel, myCaretModel);

    myDocument.addDocumentListener(new EditorDocumentAdapter(), myCaretModel);
    myDocument.addDocumentListener(mySoftWrapModel, myCaretModel);

    myFoldingModel.addListener(mySoftWrapModel, myCaretModel);

    myInlayModel.addListener(myCaretModel, myCaretModel);

    myIndentsModel = new IndentsModelImpl(this);
    myCaretModel.addCaretListener(new CaretListener() {
      @Nullable private LightweightHint myCurrentHint;
      @Nullable private IndentGuideDescriptor myCurrentCaretGuide;

      @Override
      public void caretPositionChanged(CaretEvent e) {
        if (myStickySelection) {
          int selectionStart = Math.min(myStickySelectionStart, getDocument().getTextLength() - 1);
          mySelectionModel.setSelection(selectionStart, myCaretModel.getVisualPosition(), myCaretModel.getOffset());
        }

        final IndentGuideDescriptor newGuide = myIndentsModel.getCaretIndentGuide();
        if (!Comparing.equal(myCurrentCaretGuide, newGuide)) {
          repaintGuide(newGuide);
          repaintGuide(myCurrentCaretGuide);
          myCurrentCaretGuide = newGuide;

          if (myCurrentHint != null) {
            myCurrentHint.hide();
            myCurrentHint = null;
          }

          if (newGuide != null) {
            final Rectangle visibleArea = getScrollingModel().getVisibleArea();
            final int line = newGuide.startLine;
            if (logicalLineToY(line) < visibleArea.y) {
              TextRange textRange = new TextRange(myDocument.getLineStartOffset(line), myDocument.getLineEndOffset(line));

              myCurrentHint = EditorFragmentComponent.showEditorFragmentHint(EditorImpl.this, textRange, false, false);
            }
          }
        }
      }

      @Override
      public void caretAdded(CaretEvent e) {
        if (myPrimaryCaret != null) {
          myPrimaryCaret.updateVisualPosition(); // repainting old primary caret's row background
        }
        repaintCaretRegion(e);
        myPrimaryCaret = myCaretModel.getPrimaryCaret();
      }

      @Override
      public void caretRemoved(CaretEvent e) {
        repaintCaretRegion(e);
        myPrimaryCaret = myCaretModel.getPrimaryCaret(); // repainting new primary caret's row background
        myPrimaryCaret.updateVisualPosition();
      }
    });

    myCaretCursor = new CaretCursor();

    myFoldingModel.flushCaretShift();
    myScrollBarOrientation = VERTICAL_SCROLLBAR_RIGHT;

    mySoftWrapModel.addSoftWrapChangeListener(new SoftWrapChangeListenerAdapter() {
      @Override
      public void recalculationEnds() {
        if (myCaretModel.isUpToDate()) {
          myCaretModel.updateVisualPosition();
        }
      }
    });

    EditorHighlighter highlighter = new NullEditorHighlighter();
    setHighlighter(highlighter);

    myEditorComponent = new EditorComponentImpl(this);
    myScrollPane.putClientProperty(JBScrollPane.BRIGHTNESS_FROM_VIEW, true);
    myVerticalScrollBar = (MyScrollBar)myScrollPane.getVerticalScrollBar();
    myVerticalScrollBar.setOpaque(false);
    myPanel = new JPanel();

    UIUtil.putClientProperty(
      myPanel, UIUtil.NOT_IN_HIERARCHY_COMPONENTS, (Iterable<JComponent>)() -> {
        JComponent component = getPermanentHeaderComponent();
        if (component != null && component.getParent() == null) {
          return Collections.singleton(component).iterator();
        }
        return ContainerUtil.emptyIterator();
      });

    myHeaderPanel = new MyHeaderPanel();
    myGutterComponent = new EditorGutterComponentImpl(this);
    initComponent();

    myView = new EditorView(this);
    myView.reinitSettings();

    myInlayModel.addListener(new InlayModel.SimpleAdapter() {
      @Override
      public void onUpdated(@NotNull Inlay inlay) {
        if (myDocument.isInEventsHandling() || myDocument.isInBulkUpdate()) return;
        validateSize();
        repaint(inlay.getOffset(), inlay.getOffset(), false);
      }
    }, myCaretModel);

    if (UISettings.getInstance().getPresentationMode()) {
      setFontSize(UISettings.getInstance().getPresentationModeFontSize());
    }

    myGutterComponent.updateSize();
    Dimension preferredSize = getPreferredSize();
    myEditorComponent.setSize(preferredSize);

    updateCaretCursor();

    if (SystemInfo.isJavaVersionAtLeast("1.8") && SystemInfo.isMacIntel64 && SystemInfo.isJetBrainsJvm && Registry.is("ide.mac.forceTouch")) {
      new MacGestureSupportForEditor(getComponent());
    }
  }

  /**
   * This method is intended to control a blit-accelerated scrolling, because transparent scrollbars suppress it.
   * Blit-acceleration copies as much of the rendered area as possible and then repaints only newly exposed region.
   * It is possible to disable blit-acceleration using by the registry key {@code editor.transparent.scrollbar=true}.
   * Also, when there's a background image, blit-acceleration cannot be used (because of the static overlay).
   * In such cases this method returns {@code false} to use transparent scrollbars as designed.
   * Enabled blit-acceleration improves scrolling performance and reduces CPU usage
   * (especially if drawing is compute-intensive).
   * <p>
   * To have both the hardware acceleration and the background image
   * we need to completely redesign JViewport machinery to support independent layers,
   * which is (probably) possible, but it's a rather cumbersome task.
   * Smooth scrolling still works event without the blit-acceleration,
   * but with suboptimal performance and CPU usage.
   *
   * @return {@code true} if a scrollbar should be opaque, {@code false} otherwise
   */
  boolean shouldScrollBarBeOpaque() {
    return !myBackgroundImageSet && !Registry.is("editor.transparent.scrollbar");
  }

  public boolean shouldSoftWrapsBeForced() {
    if (myProject != null && PsiDocumentManager.getInstance(myProject).isDocumentBlockedByPsi(myDocument)) {
      // Disable checking for files in intermediate states - e.g. for files during refactoring.
      return false;
    }
    int lineWidthLimit = Registry.intValue("editor.soft.wrap.force.limit");
    for (int i = 0; i < myDocument.getLineCount(); i++) {
      if (myDocument.getLineEndOffset(i) - myDocument.getLineStartOffset(i) > lineWidthLimit) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  static Color adjustThumbColor(@NotNull Color base, boolean dark) {
    return dark ? ColorUtil.withAlpha(ColorUtil.shift(base, 1.35), 0.5)
                : ColorUtil.withAlpha(ColorUtil.shift(base, 0.68), 0.4);
  }

  boolean isDarkEnough() {
    return ColorUtil.isDark(getBackgroundColor());
  }

  private void repaintCaretRegion(CaretEvent e) {
    CaretImpl caretImpl = (CaretImpl)e.getCaret();
    if (caretImpl != null) {
      caretImpl.updateVisualPosition();
      if (caretImpl.hasSelection()) {
        repaint(caretImpl.getSelectionStart(), caretImpl.getSelectionEnd(), false);
      }
    }
  }

  @NotNull
  @Override
  public EditorColorsScheme createBoundColorSchemeDelegate(@Nullable final EditorColorsScheme customGlobalScheme) {
    return new MyColorSchemeDelegate(customGlobalScheme);
  }

  private void repaintGuide(@Nullable IndentGuideDescriptor guide) {
    if (guide != null) {
      repaintLines(guide.startLine, guide.endLine);
    }
  }

  @Override
  public int getPrefixTextWidthInPixels() {
    return (int)myView.getPrefixTextWidthInPixels();
  }

  @Override
  public void setPrefixTextAndAttributes(@Nullable String prefixText, @Nullable TextAttributes attributes) {
    mySoftWrapModel.recalculate();
    myView.setPrefix(prefixText, attributes);
  }

  @Override
  public boolean isPurePaintingMode() {
    return myPurePaintingMode;
  }

  @Override
  public void setPurePaintingMode(boolean enabled) {
    myPurePaintingMode = enabled;
  }

  @Override
  public void registerScrollBarRepaintCallback(@Nullable ButtonlessScrollBarUI.ScrollbarRepaintCallback callback) {
    myVerticalScrollBar.registerRepaintCallback(callback);
  }

  @Override
  public int getExpectedCaretOffset() {
    int expectedCaretOffset = myExpectedCaretOffset;
    return expectedCaretOffset == -1 ? getCaretModel().getOffset() : expectedCaretOffset;
  }

  @Override
  public void setContextMenuGroupId(@Nullable String groupId) {
    myContextMenuGroupId = groupId;
  }

  @Nullable
  @Override
  public String getContextMenuGroupId() {
    return myContextMenuGroupId;
  }

  @Override
  public void setViewer(boolean isViewer) {
    myIsViewer = isViewer;
  }

  @Override
  public boolean isViewer() {
    return myIsViewer || myIsRendererMode;
  }

  @Override
  public boolean isRendererMode() {
    return myIsRendererMode;
  }

  @Override
  public void setRendererMode(boolean isRendererMode) {
    myIsRendererMode = isRendererMode;
  }

  @Override
  public void setFile(VirtualFile vFile) {
    myVirtualFile = vFile;
    reinitSettings();
  }

  @Override
  public VirtualFile getVirtualFile() {
    return myVirtualFile;
  }

  @Override
  public void setSoftWrapAppliancePlace(@NotNull SoftWrapAppliancePlaces place) {
    mySettings.setSoftWrapAppliancePlace(place);
  }

  @Override
  @NotNull
  public SelectionModelImpl getSelectionModel() {
    return mySelectionModel;
  }

  @Override
  @NotNull
  public MarkupModelEx getMarkupModel() {
    return myMarkupModel;
  }

  @Override
  @NotNull
  public MarkupModelEx getFilteredDocumentMarkupModel() {
    return myDocumentMarkupModel;
  }

  @Override
  @NotNull
  public FoldingModelImpl getFoldingModel() {
    return myFoldingModel;
  }

  @Override
  @NotNull
  public CaretModelImpl getCaretModel() {
    return myCaretModel;
  }

  @Override
  @NotNull
  public ScrollingModelEx getScrollingModel() {
    return myScrollingModel;
  }

  @Override
  @NotNull
  public SoftWrapModelImpl getSoftWrapModel() {
    return mySoftWrapModel;
  }

  @NotNull
  @Override
  public InlayModelImpl getInlayModel() {
    return myInlayModel;
  }

  @NotNull
  @Override
  public EditorKind getEditorKind() {
    return myKind;
  }

  @Override
  @NotNull
  public EditorSettings getSettings() {
    assertReadAccess();
    return mySettings;
  }

  public void resetSizes() {
    myView.reset();
  }

  @Override
  public void reinitSettings() {
    assertIsDispatchThread();

    for (EditorColorsScheme scheme = myScheme; scheme instanceof DelegateColorScheme; scheme = ((DelegateColorScheme)scheme).getDelegate()) {
      if (scheme instanceof MyColorSchemeDelegate) {
        ((MyColorSchemeDelegate)scheme).updateGlobalScheme();
        break;
      }
    }

    boolean softWrapsUsedBefore = mySoftWrapModel.isSoftWrappingEnabled();

    mySettings.reinitSettings();
    mySoftWrapModel.reinitSettings();
    myCaretModel.reinitSettings();
    mySelectionModel.reinitSettings();
    ourCaretBlinkingCommand.setBlinkCaret(mySettings.isBlinkCaret());
    ourCaretBlinkingCommand.setBlinkPeriod(mySettings.getCaretBlinkPeriod());
    myView.reinitSettings();
    myFoldingModel.rebuild();
    myInlayModel.reinitSettings();

    if (softWrapsUsedBefore ^ mySoftWrapModel.isSoftWrappingEnabled()) {
      validateSize();
    }

    myHighlighter.setColorScheme(myScheme);
    myFoldingModel.refreshSettings();

    myGutterComponent.reinitSettings();
    myGutterComponent.revalidate();

    myEditorComponent.repaint();

    updateCaretCursor();

    if (myInitialMouseEvent != null) {
      myIgnoreMouseEventsConsecutiveToInitial = true;
    }

    myCaretModel.updateVisualPosition();

    // make sure carets won't appear at invalid positions (e.g. on Tab width change)
    for (Caret caret : getCaretModel().getAllCarets()) {
      caret.moveToOffset(caret.getOffset());
    }

    if (myVirtualFile != null && myProject != null) EditorNotifications.getInstance(myProject).updateNotifications(myVirtualFile);
  }

  /**
   * To be called when editor was not disposed while it should
   */
  void throwEditorNotDisposedError(@NonNls @NotNull final String msg) {
    myTraceableDisposable.throwObjectNotDisposedError(msg);
  }

  /**
   * In case of "editor not disposed error" use {@link #throwEditorNotDisposedError(String)}
   */
  public void throwDisposalError(@NonNls @NotNull String msg) {
    myTraceableDisposable.throwDisposalError(msg);
  }

  // EditorFactory.releaseEditor should be used to release editor
  void release() {
    assertIsDispatchThread();
    if (isReleased) {
      throwDisposalError("Double release of editor:");
    }
    myTraceableDisposable.kill(null);

    isReleased = true;
    mySizeAdjustmentStrategy.cancelAllRequests();

    myFoldingModel.dispose();
    mySoftWrapModel.release();
    myMarkupModel.dispose();

    myScrollingModel.dispose();
    myGutterComponent.dispose();
    myMousePressedEvent = null;
    myMouseMovedEvent = null;
    Disposer.dispose(myCaretModel);
    Disposer.dispose(mySoftWrapModel);
    Disposer.dispose(myView);
    clearCaretThread();

    myFocusListeners.clear();
    myMouseListeners.clear();
    myMouseMotionListeners.clear();

    myEditorComponent.removeMouseListener(myMouseListener);
    myGutterComponent.removeMouseListener(myMouseListener);
    myEditorComponent.removeMouseMotionListener(myMouseMotionListener);
    myGutterComponent.removeMouseMotionListener(myMouseMotionListener);

    if (myBulkUpdateListener != null) {
      ((DocumentImpl)myDocument).removeInternalBulkModeListener(myBulkUpdateListener);
    }
    Disposer.dispose(myDisposable);
    myVerticalScrollBar.setUI(null); // clear error panel's cached image
  }

  private void clearCaretThread() {
    synchronized (ourCaretBlinkingCommand) {
      if (ourCaretBlinkingCommand.myEditor == this) {
        ourCaretBlinkingCommand.myEditor = null;
      }
    }
  }

  private void initComponent() {
    myPanel.setLayout(new BorderLayout());

    myPanel.add(myHeaderPanel, BorderLayout.NORTH);

    myGutterComponent.setOpaque(true);

    myScrollPane.setViewportView(myEditorComponent);
    //myScrollPane.setBorder(null);
    myScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
    myScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);

    myScrollPane.setRowHeaderView(myGutterComponent);

    myEditorComponent.setTransferHandler(new MyTransferHandler());
    myEditorComponent.setAutoscrolls(true);

    if (mayShowToolbar()) {
      JLayeredPane layeredPane = new JBLayeredPane() {
        @Override
        public void doLayout() {
          final Component[] components = getComponents();
          final Rectangle r = getBounds();
          for (Component c : components) {
            if (c instanceof JScrollPane) {
              c.setBounds(0, 0, r.width, r.height);
            }
            else {
              final Dimension d = c.getPreferredSize();
              int rightInsets = getVerticalScrollBar().getWidth() + (isMirrored() ? myGutterComponent.getWidth() : 0);
              c.setBounds(r.width - d.width - rightInsets - 20, 20, d.width, d.height);
            }
          }
        }

        @Override
        public Dimension getPreferredSize() {
          return myScrollPane.getPreferredSize();
        }
      };

      layeredPane.add(myScrollPane, JLayeredPane.DEFAULT_LAYER);
      myPanel.add(layeredPane);

      new ContextMenuImpl(layeredPane, myScrollPane, this);
    }
    else {
      myPanel.add(myScrollPane);
    }

    myEditorComponent.addKeyListener(new KeyListener() {
      @Override
      public void keyPressed(@NotNull KeyEvent e) {
        if (e.getKeyCode() >= KeyEvent.VK_A && e.getKeyCode() <= KeyEvent.VK_Z) {
          myCharKeyPressed = true;
        }
      }

      @Override
      public void keyTyped(@NotNull KeyEvent event) {
        myNeedToSelectPreviousChar = false;
        if (event.isConsumed()) {
          return;
        }
        if (processKeyTyped(event)) {
          event.consume();
        }
      }

      @Override
      public void keyReleased(KeyEvent e) {
        myCharKeyPressed = false;
      }
    });

    myEditorComponent.addMouseListener(myMouseListener);
    myGutterComponent.addMouseListener(myMouseListener);
    myEditorComponent.addMouseMotionListener(myMouseMotionListener);
    myGutterComponent.addMouseMotionListener(myMouseMotionListener);

    myEditorComponent.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(@NotNull FocusEvent e) {
        myCaretCursor.activate();
        for (Caret caret : myCaretModel.getAllCarets()) {
          int caretLine = caret.getLogicalPosition().line;
          repaintLines(caretLine, caretLine);
        }
        fireFocusGained();
      }

      @Override
      public void focusLost(@NotNull FocusEvent e) {
        clearCaretThread();
        for (Caret caret : myCaretModel.getAllCarets()) {
          int caretLine = caret.getLogicalPosition().line;
          repaintLines(caretLine, caretLine);
        }
        fireFocusLost();
      }
    });

    UiNotifyConnector connector = new UiNotifyConnector(myEditorComponent, new Activatable.Adapter() {
      @Override
      public void showNotify() {
        myGutterComponent.updateSizeOnShowNotify();
      }
    });
    Disposer.register(getDisposable(), connector);

    try {
      final DropTarget dropTarget = myEditorComponent.getDropTarget();
      if (dropTarget != null) { // might be null in headless environment
        dropTarget.addDropTargetListener(new DropTargetAdapter() {
          @Override
          public void drop(@NotNull DropTargetDropEvent e) {
          }

          @Override
          public void dragOver(@NotNull DropTargetDragEvent e) {
            Point location = e.getLocation();

            getCaretModel().moveToVisualPosition(getTargetPosition(location.x, location.y, true));
            getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
            requestFocus();
          }
        });
      }
    }
    catch (TooManyListenersException e) {
      LOG.error(e);
    }
    // update area available for soft wrapping on component shown/hidden
    myPanel.addHierarchyListener(e -> mySoftWrapModel.getApplianceManager().updateAvailableArea());

    myPanel.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(@NotNull ComponentEvent e) {
        myMarkupModel.recalcEditorDimensions();
        myMarkupModel.repaint(-1, -1);
      }
    });
  }

  private boolean mayShowToolbar() {
    return !isEmbeddedIntoDialogWrapper() && !isOneLineMode() && ContextMenuImpl.mayShowToolbar(myDocument);
  }

  @Override
  public void setFontSize(final int fontSize) {
    setFontSize(fontSize, null);
  }

  /**
   * Changes editor font size, attempting to keep a given point unmoved. If point is not given, top left screen corner is assumed.
   *
   * @param fontSize new font size
   * @param zoomCenter zoom point, relative to viewport
   */
  private void setFontSize(int fontSize, @Nullable Point zoomCenter) {
    int oldFontSize = myScheme.getEditorFontSize();

    Rectangle visibleArea = myScrollingModel.getVisibleArea();
    Point zoomCenterRelative = zoomCenter == null ? new Point() : zoomCenter;
    Point zoomCenterAbsolute = new Point(visibleArea.x + zoomCenterRelative.x, visibleArea.y + zoomCenterRelative.y);
    LogicalPosition zoomCenterLogical = xyToLogicalPosition(zoomCenterAbsolute).withoutVisualPositionInfo();
    int oldLineHeight = getLineHeight();
    int intraLineOffset = zoomCenterAbsolute.y % oldLineHeight;

    myScheme.setEditorFontSize(fontSize);
    fontSize = myScheme.getEditorFontSize(); // resulting font size might be different due to applied min/max limits
    myPropertyChangeSupport.firePropertyChange(PROP_FONT_SIZE, oldFontSize, fontSize);
    // Update vertical scroll bar bounds if necessary (we had a problem that use increased editor font size and it was not possible
    // to scroll to the bottom of the document).
    myScrollPane.getViewport().invalidate();

    Point shiftedZoomCenterAbsolute = logicalPositionToXY(zoomCenterLogical);
    myScrollingModel.disableAnimation();
    try {
      myScrollingModel.scroll(visibleArea.x == 0 ? 0 : shiftedZoomCenterAbsolute.x - zoomCenterRelative.x, // stick to left border if it's visible
                                       shiftedZoomCenterAbsolute.y - zoomCenterRelative.y + (intraLineOffset * getLineHeight() + oldLineHeight / 2) / oldLineHeight);
    } finally {
      myScrollingModel.enableAnimation();
    }
  }

  public int getFontSize() {
    return myScheme.getEditorFontSize();
  }

  @NotNull
  public ActionCallback type(@NotNull final String text) {
    final ActionCallback result = new ActionCallback();

    for (int i = 0; i < text.length(); i++) {
      if (!processKeyTyped(text.charAt(i))) {
        result.setRejected();
        return result;
      }
    }

    result.setDone();

    return result;
  }

  private boolean processKeyTyped(char c) {
    // [vova] This is patch for Mac OS X. Under Mac "input methods"
    // is handled before our EventQueue consume upcoming KeyEvents.
    IdeEventQueue queue = IdeEventQueue.getInstance();
    if (queue.shouldNotTypeInEditor() || ProgressManager.getInstance().hasModalProgressIndicator()) {
      return false;
    }
    FileDocumentManager manager = FileDocumentManager.getInstance();
    final VirtualFile file = manager.getFile(myDocument);
    if (file != null && !file.isValid()) {
      return false;
    }

    DataContext context = getDataContext();

    Graphics graphics = GraphicsUtil.safelyGetGraphics(myEditorComponent);
    if (graphics != null) { // editor component is not showing
      processKeyTypedImmediately(c, graphics, context);
      graphics.dispose();
    }

    ActionManagerEx.getInstanceEx().fireBeforeEditorTyping(c, context);
    EditorUIUtil.hideCursorInEditor(this);
    processKeyTypedNormally(c, context);

    return true;
  }

  void processKeyTypedImmediately(char c, Graphics graphics, DataContext dataContext) {
    EditorActionPlan plan = new EditorActionPlan(this);
    EditorActionManager.getInstance().getTypedAction().beforeActionPerformed(this, c, dataContext, plan);
    myImmediatePainter.paint(graphics, plan);
  }

  void processKeyTypedNormally(char c, DataContext dataContext) {
    EditorActionManager.getInstance().getTypedAction().actionPerformed(this, c, dataContext);
  }

  private void fireFocusLost() {
    for (FocusChangeListener listener : myFocusListeners) {
      listener.focusLost(this);
    }
  }

  private void fireFocusGained() {
    for (FocusChangeListener listener : myFocusListeners) {
      listener.focusGained(this);
    }
  }

  @Override
  public void setHighlighter(@NotNull final EditorHighlighter highlighter) {
    if (isReleased) return; // do not set highlighter to the released editor
    assertIsDispatchThread();
    final Document document = getDocument();
    Disposer.dispose(myHighlighterDisposable);

    document.addDocumentListener(highlighter);
    myHighlighterDisposable = () -> document.removeDocumentListener(highlighter);
    Disposer.register(myDisposable, myHighlighterDisposable);
    highlighter.setEditor(this);
    highlighter.setText(document.getImmutableCharSequence());
    if (!(highlighter instanceof EmptyEditorHighlighter)) {
      EditorHighlighterCache.rememberEditorHighlighterForCachesOptimization(document, highlighter);
    }
    myHighlighter = highlighter;

    if (myPanel != null) {
      reinitSettings();
    }
  }

  @NotNull
  @Override
  public EditorHighlighter getHighlighter() {
    assertReadAccess();
    return myHighlighter;
  }

  @Override
  @NotNull
  public EditorComponentImpl getContentComponent() {
    return myEditorComponent;
  }

  @NotNull
  @Override
  public EditorGutterComponentImpl getGutterComponentEx() {
    return myGutterComponent;
  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
    myPropertyChangeSupport.addPropertyChangeListener(listener);
  }
  @Override
  public void addPropertyChangeListener(@NotNull final PropertyChangeListener listener, @NotNull Disposable parentDisposable) {
    addPropertyChangeListener(listener);
    Disposer.register(parentDisposable, () -> removePropertyChangeListener(listener));
  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
    myPropertyChangeSupport.removePropertyChangeListener(listener);
  }

  @Override
  public void setInsertMode(boolean mode) {
    assertIsDispatchThread();
    boolean oldValue = myIsInsertMode;
    myIsInsertMode = mode;
    myPropertyChangeSupport.firePropertyChange(PROP_INSERT_MODE, oldValue, mode);
    myCaretCursor.repaint();
  }

  @Override
  public boolean isInsertMode() {
    return myIsInsertMode;
  }

  @Override
  public void setColumnMode(boolean mode) {
    assertIsDispatchThread();
    boolean oldValue = myIsColumnMode;
    myIsColumnMode = mode;
    myPropertyChangeSupport.firePropertyChange(PROP_COLUMN_MODE, oldValue, mode);
  }

  @Override
  public boolean isColumnMode() {
    return myIsColumnMode;
  }

  public int yToVisibleLine(int y) {
    return myView.yToVisualLine(y);
  }

  @Override
  @NotNull
  public VisualPosition xyToVisualPosition(@NotNull Point p) {
    return myView.xyToVisualPosition(p);
  }

  @Override
  @NotNull
  public VisualPosition xyToVisualPosition(@NotNull Point2D p) {
    return myView.xyToVisualPosition(p);
  }

  @Override
  @NotNull
  public Point2D offsetToPoint2D(int offset, boolean leanTowardsLargerOffsets, boolean beforeSoftWrap) {
    return myView.offsetToXY(offset, leanTowardsLargerOffsets, beforeSoftWrap);
  }

  @Override
  @NotNull
  public Point offsetToXY(int offset, boolean leanForward, boolean beforeSoftWrap) {
    Point2D point2D = offsetToPoint2D(offset, leanForward, beforeSoftWrap);
    return new Point((int)point2D.getX(), (int)point2D.getY());
  }

  @Override
  @NotNull
  public VisualPosition offsetToVisualPosition(int offset) {
    return offsetToVisualPosition(offset, false, false);
  }

  @Override
  @NotNull
  public VisualPosition offsetToVisualPosition(int offset, boolean leanForward, boolean beforeSoftWrap) {
    return myView.offsetToVisualPosition(offset, leanForward, beforeSoftWrap);
  }

  @Override
  @NotNull
  public LogicalPosition offsetToLogicalPosition(int offset) {
    return myView.offsetToLogicalPosition(offset);
  }

  @TestOnly
  public void setCaretActive() {
    synchronized (ourCaretBlinkingCommand) {
      ourCaretBlinkingCommand.myEditor = this;
    }
  }

  // optimization: do not do column calculations here since we are interested in line number only
  int offsetToVisualLine(int offset) {
    return myView.offsetToVisualLine(offset, false);
  }
  
  public int visualLineStartOffset(int visualLine) {
    return myView.visualLineToOffset(visualLine);
  }

  @Override
  @NotNull
  public LogicalPosition xyToLogicalPosition(@NotNull Point p) {
    Point pp = p.x >= 0 && p.y >= 0 ? p : new Point(Math.max(p.x, 0), Math.max(p.y, 0));
    return visualToLogicalPosition(xyToVisualPosition(pp));
  }

  private int logicalLineToY(int line) {
    int visualLine = line < myDocument.getLineCount() ? offsetToVisualLine(myDocument.getLineStartOffset(line)) :
                     logicalToVisualPosition(new LogicalPosition(line, 0)).line;
    return visibleLineToY(visualLine);
  }

  @Override
  @NotNull
  public Point logicalPositionToXY(@NotNull LogicalPosition pos) {
    VisualPosition visible = logicalToVisualPosition(pos);
    return visualPositionToXY(visible);
  }

  @Override
  @NotNull
  public Point visualPositionToXY(@NotNull VisualPosition visible) {
    Point2D point2D = myView.visualPositionToXY(visible);
    return new Point((int)point2D.getX(), (int)point2D.getY());
  }

  @Override
  @NotNull
  public Point2D visualPositionToPoint2D(@NotNull VisualPosition visible) {
    return myView.visualPositionToXY(visible);
  }

  /**
   * Returns how much current line height bigger than the normal (16px)
   * This method is used to scale editors elements such as gutter icons, folding elements, and others
   */
  public float getScale() {
    if (!Registry.is("editor.scale.gutter.icons")) return 1f;
    float normLineHeight = getLineHeight() / myScheme.getLineSpacing(); // normalized, as for 1.0f line spacing
    return normLineHeight / JBUI.scale(16f);
  }

  public int findNearestDirectionBoundary(int offset, boolean lookForward) {
    return myView.findNearestDirectionBoundary(offset, lookForward);
  }

  public int visibleLineToY(int line) {
    return myView.visualLineToY(line);
  }

  @Override
  public void repaint(int startOffset, int endOffset) {
    repaint(startOffset, endOffset, true);
  }
  
  void repaint(int startOffset, int endOffset, boolean invalidateTextLayout) {
    if (myDocument.isInBulkUpdate()) {
      return;
    }
    assertIsDispatchThread();
    endOffset = Math.min(endOffset, myDocument.getTextLength());

    if (invalidateTextLayout) {
      myView.invalidateRange(startOffset, endOffset);
    }

    if (!isShowing()) {
      return;
    }
    // We do repaint in case of equal offsets because there is a possible case that there is a soft wrap at the same offset and
    // it does occupy particular amount of visual space that may be necessary to repaint.
    if (startOffset <= endOffset) {
      int startLine = myDocument.getLineNumber(startOffset);
      int endLine = myDocument.getLineNumber(endOffset);
      repaintLines(startLine, endLine);
    }
  }

  private boolean isShowing() {
    return myGutterComponent.isShowing();
  }

  private void repaintToScreenBottom(int startLine) {
    Rectangle visibleArea = getScrollingModel().getVisibleArea();
    int yStartLine = logicalLineToY(startLine);
    int yEndLine = visibleArea.y + visibleArea.height;

    myEditorComponent.repaintEditorComponent(visibleArea.x, yStartLine, visibleArea.x + visibleArea.width, yEndLine - yStartLine);
    myGutterComponent.repaint(0, yStartLine, myGutterComponent.getWidth(), yEndLine - yStartLine);
    ((EditorMarkupModelImpl)getMarkupModel()).repaint(-1, -1);
  }

  /**
   * Asks to repaint all logical lines from the given {@code [start; end]} range.
   *
   * @param startLine start logical line to repaint (inclusive)
   * @param endLine   end logical line to repaint (inclusive)
   */
  private void repaintLines(int startLine, int endLine) {
    if (!isShowing()) return;

    Rectangle visibleArea = getScrollingModel().getVisibleArea();
    int yStartLine = logicalLineToY(startLine);
    int endVisLine = myDocument.getTextLength() <= 0
                     ? 0
                     : offsetToVisualLine(myDocument.getLineEndOffset(Math.min(myDocument.getLineCount() - 1, endLine)));
    int height = endVisLine * getLineHeight() - yStartLine + getLineHeight() + 2;

    myEditorComponent.repaintEditorComponent(visibleArea.x, yStartLine, visibleArea.x + visibleArea.width, height);
    myGutterComponent.repaint(0, yStartLine, myGutterComponent.getWidth(), height);
  }

  private void bulkUpdateStarted() {
    myView.getPreferredSize(); // make sure size is calculated (in case it will be required while bulk mode is active)

    myScrollingModel.onBulkDocumentUpdateStarted();
    
    saveCaretRelativePosition();

    myCaretModel.onBulkDocumentUpdateStarted();
    mySoftWrapModel.onBulkDocumentUpdateStarted();
    myFoldingModel.onBulkDocumentUpdateStarted();
  }

  private void bulkUpdateFinished() {
    myFoldingModel.onBulkDocumentUpdateFinished();
    mySoftWrapModel.onBulkDocumentUpdateFinished();
    myView.reset();
    myCaretModel.onBulkDocumentUpdateFinished();

    setMouseSelectionState(MOUSE_SELECTION_STATE_NONE);

    validateSize();

    updateGutterSize();
    repaintToScreenBottom(0);
    updateCaretCursor();

    if (!Boolean.TRUE.equals(getUserData(DISABLE_CARET_POSITION_KEEPING))) {
      restoreCaretRelativePosition();
    }
  }

  private void beforeChangedUpdate() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    
    myDocumentChangeInProgress = true;
    if (isStickySelection()) {
      setStickySelection(false);
    }
    if (myDocument.isInBulkUpdate()) {
      // Assuming that the job is done at bulk listener callback methods.
      return;
    }

    saveCaretRelativePosition();
  }

  void invokeDelayedErrorStripeRepaint() {
    if (myErrorStripeNeedsRepaint) {
      myMarkupModel.repaint(-1, -1);
      myErrorStripeNeedsRepaint = false;
    }
  }

  private void changedUpdate(DocumentEvent e) {
    myDocumentChangeInProgress = false;
    if (myDocument.isInBulkUpdate()) return;

    if (myErrorStripeNeedsRepaint) {
      myMarkupModel.repaint(e.getOffset(), e.getOffset() + e.getNewLength());
      myErrorStripeNeedsRepaint = false;
    }

    setMouseSelectionState(MOUSE_SELECTION_STATE_NONE);

    validateSize();

    int startLine = offsetToLogicalLine(e.getOffset());
    int endLine = offsetToLogicalLine(e.getOffset() + e.getNewLength());

    boolean painted = false;
    if (myDocument.getTextLength() > 0) {
      if (startLine != endLine || StringUtil.indexOf(e.getOldFragment(), '\n') != -1) {
        myGutterComponent.clearLineToGutterRenderersCache();
      }

      if (countLineFeeds(e.getOldFragment()) != countLineFeeds(e.getNewFragment())) {
        // Lines removed. Need to repaint till the end of the screen
        repaintToScreenBottom(startLine);
        painted = true;
      }
    }

    updateCaretCursor();
    if (!painted) {
      repaintLines(startLine, endLine);
    }

    if (!Boolean.TRUE.equals(getUserData(DISABLE_CARET_POSITION_KEEPING)) &&
        (getCaretModel().getOffset() < e.getOffset() || getCaretModel().getOffset() > e.getOffset() + e.getNewLength())) {
      restoreCaretRelativePosition();
    }
  }

  public void hideCursor() {
    if (!myIsViewer && Registry.is("ide.hide.cursor.when.typing") &&
        EMPTY_CURSOR != null && EMPTY_CURSOR != myEditorComponent.getCursor()) {
      myEditorComponent.setCursor(EMPTY_CURSOR);
    }
  }

  private void saveCaretRelativePosition() {
    Rectangle visibleArea = getScrollingModel().getVisibleArea();
    Point pos = visualPositionToXY(getCaretModel().getVisualPosition());
    myCaretUpdateVShift = pos.y - visibleArea.y;
  }

  private void restoreCaretRelativePosition() {
    Point caretLocation = visualPositionToXY(getCaretModel().getVisualPosition());
    int scrollOffset = caretLocation.y - myCaretUpdateVShift;
    getScrollingModel().disableAnimation();
    getScrollingModel().scrollVertically(scrollOffset);
    getScrollingModel().enableAnimation();
  }

  public boolean isScrollToCaret() {
    return myScrollToCaret;
  }

  public void setScrollToCaret(boolean scrollToCaret) {
    myScrollToCaret = scrollToCaret;
  }

  @NotNull
  public Disposable getDisposable() {
    return myDisposable;
  }

  private static int countLineFeeds(@NotNull CharSequence c) {
    return StringUtil.countNewLines(c);
  }

  private boolean updatingSize; // accessed from EDT only
  private void updateGutterSize() {
    assertIsDispatchThread();
    if (!updatingSize) {
      updatingSize = true;
      ApplicationManager.getApplication().invokeLater(() -> {
        try {
          if (!isDisposed()) {
            myGutterComponent.updateSize();
          }
        }
        finally {
          updatingSize = false;
        }
      }, ModalityState.any(), __->isDisposed());
    }
  }

  void validateSize() {
    if (isReleased) return;
    
    Dimension dim = getPreferredSize();

    if (!dim.equals(myPreferredSize) && !myDocument.isInBulkUpdate()) {
      dim = mySizeAdjustmentStrategy.adjust(dim, myPreferredSize, this);
      if (!dim.equals(myPreferredSize)) {
        myPreferredSize = dim;

        updateGutterSize();

        myEditorComponent.setSize(dim);
        myEditorComponent.fireResized();

        myMarkupModel.recalcEditorDimensions();
        myMarkupModel.repaint(-1, -1);
      }
    }
  }

  void recalculateSizeAndRepaint() {
    validateSize();
    myEditorComponent.repaintEditorComponent();
  }

  @Override
  @NotNull
  public DocumentEx getDocument() {
    return myDocument;
  }

  @Override
  @NotNull
  public JComponent getComponent() {
    return myPanel;
  }

  @Override
  public void addEditorMouseListener(@NotNull EditorMouseListener listener) {
    myMouseListeners.add(listener);
  }

  @Override
  public void removeEditorMouseListener(@NotNull EditorMouseListener listener) {
    boolean success = myMouseListeners.remove(listener);
    LOG.assertTrue(success || isReleased);
  }

  @Override
  public void addEditorMouseMotionListener(@NotNull EditorMouseMotionListener listener) {
    myMouseMotionListeners.add(listener);
  }

  @Override
  public void removeEditorMouseMotionListener(@NotNull EditorMouseMotionListener listener) {
    boolean success = myMouseMotionListeners.remove(listener);
    LOG.assertTrue(success || isReleased);
  }

  @Override
  public boolean isStickySelection() {
    return myStickySelection;
  }

  @Override
  public void setStickySelection(boolean enable) {
    myStickySelection = enable;
    if (enable) {
      myStickySelectionStart = getCaretModel().getOffset();
    }
    else {
      mySelectionModel.removeSelection();
    }
  }

  @Override
  public boolean isDisposed() {
    return isReleased;
  }

  public void stopDumbLater() {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    ApplicationManager.getApplication().invokeLater(this::stopDumb, ModalityState.current(), __ -> isDisposed());
  }

  private void stopDumb() {
    putUserData(BUFFER, null);
  }

  /**
   * {@link #stopDumbLater} or {@link #stopDumb} must be performed in finally
   */
  public void startDumb() {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    Rectangle rect = ((JViewport)myEditorComponent.getParent()).getViewRect();
    BufferedImage image = UIUtil.createImage(myEditorComponent, rect.width, rect.height, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = image.createGraphics();
    graphics.translate(-rect.x, -rect.y);
    graphics.setClip(rect.x, rect.y, rect.width, rect.height);
    myEditorComponent.paintComponent(graphics);
    graphics.dispose();
    putUserData(BUFFER, image);
  }

  void paint(@NotNull Graphics2D g) {
    Rectangle clip = g.getClipBounds();

    if (clip == null) {
      return;
    }

    if (Registry.is("editor.dumb.mode.available")) {
      final BufferedImage buffer = getUserData(BUFFER);
      if (buffer != null) {
        final Rectangle rect = getContentComponent().getVisibleRect();
        UIUtil.drawImage(g, buffer, null, rect.x, rect.y);
        return;
      }
    }

    if (isReleased) {
      g.setColor(getDisposedBackground());
      g.fillRect(clip.x, clip.y, clip.width, clip.height);
      return;
    }
    if (myUpdateCursor) {
      setCursorPosition();
      myUpdateCursor = false;
    }
    if (myProject != null && myProject.isDisposed()) return;

    myView.paint(g);

    boolean isBackgroundImageSet = IdeBackgroundUtil.isEditorBackgroundImageSet(myProject);
    if (myBackgroundImageSet != isBackgroundImageSet) {
      myBackgroundImageSet = isBackgroundImageSet;
      updateOpaque(myScrollPane.getHorizontalScrollBar());
      updateOpaque(myScrollPane.getVerticalScrollBar());
    }
  }

  Color getDisposedBackground() {
    return new JBColor(new Color(128, 255, 128), new Color(128, 255, 128));
  }

  @NotNull
  @Override
  public IndentsModel getIndentsModel() {
    return myIndentsModel;
  }

  @Override
  public void setHeaderComponent(JComponent header) {
    myHeaderPanel.removeAll();
    header = header == null ? getPermanentHeaderComponent() : header;
    if (header != null) {
      myHeaderPanel.add(header);
    }

    myHeaderPanel.revalidate();
    myHeaderPanel.repaint();
  }

  @Override
  public boolean hasHeaderComponent() {
    JComponent header = getHeaderComponent();
    return header != null && header != getPermanentHeaderComponent();
  }

  @Override
  @Nullable
  public JComponent getPermanentHeaderComponent() {
    return getUserData(PERMANENT_HEADER);
  }

  @Override
  public void setPermanentHeaderComponent(@Nullable JComponent component) {
    putUserData(PERMANENT_HEADER, component);
  }

  @Override
  @Nullable
  public JComponent getHeaderComponent() {
    if (myHeaderPanel.getComponentCount() > 0) {
      return (JComponent)myHeaderPanel.getComponent(0);
    }
    return null;
  }

  @Override
  public void setBackgroundColor(Color color) {
    myScrollPane.setBackground(color);

    if (getBackgroundIgnoreForced().equals(color)) {
      myForcedBackground = null;
      return;
    }
    myForcedBackground = color;
  }

  @NotNull
  Color getForegroundColor() {
    return myScheme.getDefaultForeground();
  }

  @NotNull
  @Override
  public Color getBackgroundColor() {
    if (myForcedBackground != null) return myForcedBackground;

    return getBackgroundIgnoreForced();
  }

  @NotNull
  @Override
  public TextDrawingCallback getTextDrawingCallback() {
    return myTextDrawingCallback;
  }

  @Override
  public void setPlaceholder(@Nullable CharSequence text) {
    myPlaceholderText = text;
  }

  @Override
  public void setPlaceholderAttributes(@Nullable TextAttributes attributes) {
    myPlaceholderAttributes = attributes;
  }

  public TextAttributes getPlaceholderAttributes() {
    return myPlaceholderAttributes;
  }

  public CharSequence getPlaceholder() {
    return myPlaceholderText;
  }
  
  @Override
  public void setShowPlaceholderWhenFocused(boolean show) {
    myShowPlaceholderWhenFocused = show;
  }

  public boolean getShowPlaceholderWhenFocused() {
    return myShowPlaceholderWhenFocused;
  }

  Color getBackgroundColor(@NotNull final TextAttributes attributes) {
    final Color attrColor = attributes.getBackgroundColor();
    return Comparing.equal(attrColor, myScheme.getDefaultBackground()) ? getBackgroundColor() : attrColor;
  }

  @NotNull
  private Color getBackgroundIgnoreForced() {
    Color color = myScheme.getDefaultBackground();
    if (myDocument.isWritable()) {
      return color;
    }
    Color readOnlyColor = myScheme.getColor(EditorColors.READONLY_BACKGROUND_COLOR);
    return readOnlyColor != null ? readOnlyColor : color;
  }

  @Nullable
  public TextRange getComposedTextRange() {
    return myInputMethodRequestsHandler == null || myInputMethodRequestsHandler.composedText == null ?
           null : myInputMethodRequestsHandler.composedTextRange;
  }

  @Override
  public int getMaxWidthInRange(int startOffset, int endOffset) {
    return myView.getMaxWidthInRange(startOffset, endOffset);
  }

  public boolean isPaintSelection() {
    return myPaintSelection || !isOneLineMode() || IJSwingUtilities.hasFocus(getContentComponent());
  }

  public void setPaintSelection(boolean paintSelection) {
    myPaintSelection = paintSelection;
  }

  @Override
  @NotNull
  @NonNls
  public String dumpState() {
    return "allow caret inside tab: " + mySettings.isCaretInsideTabs()
           + ", allow caret after line end: " + mySettings.isVirtualSpace()
           + ", soft wraps: " + (mySoftWrapModel.isSoftWrappingEnabled() ? "on" : "off")
           + ", caret model: " + getCaretModel().dumpState()
           + ", soft wraps data: " + getSoftWrapModel().dumpState()
           + "\n\nfolding data: " + getFoldingModel().dumpState()
           + (myDocument instanceof DocumentImpl ? "\n\ndocument info: " + ((DocumentImpl)myDocument).dumpState() : "")
           + "\nfont preferences: " + myScheme.getFontPreferences()
           + "\npure painting mode: " + myPurePaintingMode
           + "\ninsets: " + myEditorComponent.getInsets()
           + (myView == null ? "" : "\nview: " + myView.dumpState());
  }

  @Nullable
  public CaretRectangle[] getCaretLocations(boolean onlyIfShown) {
    return myCaretCursor.getCaretLocations(onlyIfShown);
  }

  public int getAscent() {
    return myView.getAscent();
  }

  @Override
  public int getLineHeight() {
    return myView.getLineHeight();
  }

  public int getDescent() {
    return myView.getDescent();
  }

  public int getCharHeight() {
    return myView.getCharHeight();
  }

  @NotNull
  public FontMetrics getFontMetrics(@JdkConstants.FontStyle int fontType) {
    EditorFontType ft;
    if (fontType == Font.PLAIN) ft = EditorFontType.PLAIN;
    else if (fontType == Font.BOLD) ft = EditorFontType.BOLD;
    else if (fontType == Font.ITALIC) ft = EditorFontType.ITALIC;
    else if (fontType == (Font.BOLD | Font.ITALIC)) ft = EditorFontType.BOLD_ITALIC;
    else {
      LOG.error("Unknown font type: " + fontType);
      ft = EditorFontType.PLAIN;
    }

    return myEditorComponent.getFontMetrics(myScheme.getFont(ft));
  }

  public int getPreferredHeight() {
    return isReleased ? 0 : myView.getPreferredHeight();
  }

  public Dimension getPreferredSize() {
    return isReleased ? new Dimension()
                      : Registry.is("idea.true.smooth.scrolling.dynamic.scrollbars")
                        ? new Dimension(getPreferredWidthOfVisibleLines(), myView.getPreferredHeight())
                        : myView.getPreferredSize();
  }

  /* When idea.true.smooth.scrolling=true, this method is used to compute width of currently visible line range
     rather than width of the whole document.

     As transparent scrollbars, by definition, prevent blit-acceleration of scrolling, and we really need blit-acceleration
     because not all hardware can render pixel-by-pixel scrolling with acceptable FPS without it (we now have 4K-5K displays, you know).
     To have both the hardware acceleration and the transparent scrollbars we need to completely redesign JViewport machinery to support
     independent layers, which is (probably) possible, but it's a rather cumbersome task.

     Another approach is to make scrollbars opaque, but only in the editor (as editor is a slow-to-draw component with large screen area).
     This is what "true smooth scrolling" option currently does. Interestingly, making the vertical scrollbar opaque might actually be
     a good thing because on modern displays (size, aspect ratio) code rarely extends beyond the right screen edge, and even
     when it does, its mixing with the navigation bar only reduces intelligibility of both the navigation bar and the code itself.

     Horizontal scrollbar is another story - a single long line of text forces horizontal scrollbar in the whole document,
     and in that case "transparent" scrollbar has some merits. However, instead of using transparency, we can hide horizontal
     scrollbar altogether when it's not needed for currently visible content. In a sense, this approach is superior,
     as even "transparent" scrollbar is only semi-transparent (thus we may prefer "on-demand" scrollbar in the general case).

     Hiding the horizontal scrollbar also solves another issue - when both scrollbars are visible, vertical scrolling with
     a high-precision touchpad can result in unintentional horizontal shifts (because of the touchpad sensitivity).
     When visible content fully fits horizontally (i.e. in most cases), hiding the unneeded scrollbar
     reliably prevents the horizontal  "jitter".

     Keep in mind that this functionality is experimental and may need more polishing.

     In principle, we can apply this method to other components by defining, for example,
     VariableWidth interface and supporting it in JBScrollPane. */
  private int getPreferredWidthOfVisibleLines() {
    Rectangle area = getScrollingModel().getVisibleArea();
    VisualPosition begin = xyToVisualPosition(area.getLocation());
    VisualPosition end = xyToVisualPosition(new Point(area.x + area.width, area.y + area.height));
    return Math.max(myView.getPreferredWidth(begin.line, end.line), getScrollingWidth());
  }

  /* Returns the width of current horizontal scrolling state.
     Complements the getPreferredWidthOfVisibleLines() method to allows to retain horizontal
     scrolling position that is beyond the width of currently visible lines. */
  private int getScrollingWidth() {
    JScrollBar scrollbar = myScrollPane.getHorizontalScrollBar();
    if (scrollbar != null) {
      BoundedRangeModel model = scrollbar.getModel();
      if (model != null) {
        return model.getValue() + model.getExtent();
      }
    }
    return 0;
  }

  @NotNull
  @Override
  public Dimension getContentSize() {
    return myView.getPreferredSize();
  }

  @NotNull
  @Override
  public JScrollPane getScrollPane() {
    return myScrollPane;
  }

  @Override
  public void setBorder(Border border) {
    myScrollPane.setBorder(border);
  }

  @Override
  public Insets getInsets() {
    return myScrollPane.getInsets();
  }

  @Override
  public int logicalPositionToOffset(@NotNull LogicalPosition pos) {
    return myView.logicalPositionToOffset(pos);
  }

  /**
   * @return information about total number of lines that can be viewed by user. I.e. this is a number of all document
   *         lines (considering that single logical document line may be represented on multiple visual lines because of
   *         soft wraps appliance) minus number of folded lines
   */
  public int getVisibleLineCount() {
    return getVisibleLogicalLinesCount() + getSoftWrapModel().getSoftWrapsIntroducedLinesNumber();
  }

  /**
   * @return number of visible logical lines. Generally, that is a total logical lines number minus number of folded lines
   */
  private int getVisibleLogicalLinesCount() {
    return getDocument().getLineCount() - myFoldingModel.getTotalNumberOfFoldedLines();
  }

  @Override
  @NotNull
  public VisualPosition logicalToVisualPosition(@NotNull LogicalPosition logicalPos) {
    return myView.logicalToVisualPosition(logicalPos, false);
  }

  @Override
  @NotNull
  public LogicalPosition visualToLogicalPosition(@NotNull VisualPosition visiblePos) {
    return myView.visualToLogicalPosition(visiblePos);
  }

  private int offsetToLogicalLine(int offset) {
    int textLength = myDocument.getTextLength();
    if (textLength == 0) return 0;

    if (offset > textLength || offset < 0) {
      throw new IndexOutOfBoundsException("Wrong offset: " + offset + " textLength: " + textLength);
    }

    int lineIndex = myDocument.getLineNumber(offset);
    LOG.assertTrue(lineIndex >= 0 && lineIndex < myDocument.getLineCount());

    return lineIndex;
  }

  @Override
  public int calcColumnNumber(int offset, int lineIndex) {
    return myView.offsetToLogicalPosition(offset).column;
  }

  private VisualPosition getTargetPosition(int x, int y, boolean trimToLineWidth) {
    if (myDocument.getLineCount() == 0) {
      return new VisualPosition(0, 0);
    }
    if (x < 0) {
      x = 0;
    }
    if (y < 0) {
      y = 0;
    }
    int visualLineCount = getVisibleLineCount();
    if (yToVisibleLine(y) >= visualLineCount) {
      y = visibleLineToY(Math.max(0, visualLineCount - 1));
    }
    VisualPosition visualPosition = xyToVisualPosition(new Point(x, y));
    if (trimToLineWidth && !mySettings.isVirtualSpace()) {
      LogicalPosition logicalPosition = visualToLogicalPosition(visualPosition);
      LogicalPosition lineEndPosition = offsetToLogicalPosition(myDocument.getLineEndOffset(logicalPosition.line));
      if (logicalPosition.column > lineEndPosition.column) {
        visualPosition = logicalToVisualPosition(lineEndPosition.leanForward(true));
      }
      else if (mySoftWrapModel.isInsideSoftWrap(visualPosition)) {
        VisualPosition beforeSoftWrapPosition = myView.logicalToVisualPosition(logicalPosition, true);
        if (visualPosition.line == beforeSoftWrapPosition.line) {
          visualPosition = beforeSoftWrapPosition;
        }
        else {
          visualPosition = myView.logicalToVisualPosition(logicalPosition, false);
        }
      }
    }
    return visualPosition;
  }

  private boolean checkIgnore(@NotNull MouseEvent e) {
    if (!myIgnoreMouseEventsConsecutiveToInitial) {
      myInitialMouseEvent = null;
      return false;
    }

    if (myInitialMouseEvent!= null && (e.getComponent() != myInitialMouseEvent.getComponent() || !e.getPoint().equals(myInitialMouseEvent.getPoint()))) {
      myIgnoreMouseEventsConsecutiveToInitial = false;
      myInitialMouseEvent = null;
      return false;
    }

    myIgnoreMouseEventsConsecutiveToInitial = false;
    myInitialMouseEvent = null;

    e.consume();

    return true;
  }

  private void processMouseReleased(@NotNull MouseEvent e) {
    if (checkIgnore(e)) return;

    if (e.getSource() == myGutterComponent && !(myMousePressedEvent != null && myMousePressedEvent.isConsumed())) {
      myGutterComponent.mouseReleased(e);
    }

    if (getMouseEventArea(e) != EditorMouseEventArea.EDITING_AREA || e.getY() < 0 || e.getX() < 0) {
      return;
    }

//    if (myMousePressedInsideSelection) getSelectionModel().removeSelection();
    final FoldRegion region = getFoldingModel().getFoldingPlaceholderAt(e.getPoint());
    if (e.getX() >= 0 && e.getY() >= 0 && region != null && region == myMouseSelectedRegion) {
      getFoldingModel().runBatchFoldingOperation(() -> {
        myFoldingModel.flushCaretShift();
        region.setExpanded(true);
      });

      // The call below is performed because gutter's height is not updated sometimes, i.e. it sticks to the value that corresponds
      // to the situation when fold region is collapsed. That causes bottom of the gutter to not be repainted and that looks really ugly.
      myGutterComponent.updateSize();
    }

    // The general idea is to check if the user performed 'caret position change click' (left click most of the time) inside selection
    // and, in the case of the positive answer, clear selection. Please note that there is a possible case that mouse click
    // is performed inside selection but it triggers context menu. We don't want to drop the selection then.
    if (myMousePressedEvent != null && myMousePressedEvent.getClickCount() == 1 && myMousePressedInsideSelection
        && !myMousePressedEvent.isShiftDown()
        && !myMousePressedEvent.isPopupTrigger()
        && !isToggleCaretEvent(myMousePressedEvent)
        && !isCreateRectangularSelectionEvent(myMousePressedEvent)) {
      getSelectionModel().removeSelection();
    }
  }

  @NotNull
  @Override
  public DataContext getDataContext() {
    return getProjectAwareDataContext(DataManager.getInstance().getDataContext(getContentComponent()));
  }

  @NotNull
  private DataContext getProjectAwareDataContext(@NotNull final DataContext original) {
    if (CommonDataKeys.PROJECT.getData(original) == myProject) return original;

    return dataId -> {
      if (CommonDataKeys.PROJECT.is(dataId)) {
        return myProject;
      }
      return original.getData(dataId);
    };
  }

  private boolean isInsideGutterWhitespaceArea(@NotNull MouseEvent e) {
    EditorMouseEventArea area = getMouseEventArea(e);
    return area == EditorMouseEventArea.FOLDING_OUTLINE_AREA &&
           myGutterComponent.convertX(e.getX()) > myGutterComponent.getWhitespaceSeparatorOffset();
  }

  @Override
  public EditorMouseEventArea getMouseEventArea(@NotNull MouseEvent e) {
    if (myGutterComponent != e.getSource()) return EditorMouseEventArea.EDITING_AREA;

    int x = myGutterComponent.convertX(e.getX());

    return myGutterComponent.getEditorMouseAreaByOffset(x);
  }

  private void requestFocus() {
    final IdeFocusManager focusManager = IdeFocusManager.getInstance(myProject);
    if (focusManager.getFocusOwner() != myEditorComponent) { //IDEA-64501
      focusManager.requestFocus(myEditorComponent, true);
    }
  }

  private void validateMousePointer(@NotNull MouseEvent e) {
    if (e.getSource() == myGutterComponent) {
      myGutterComponent.validateMousePointer(e);
    }
    else {
      myGutterComponent.setActiveFoldRegion(null);
      if (getSelectionModel().hasSelection() && (e.getModifiersEx() & (InputEvent.BUTTON1_DOWN_MASK | InputEvent.BUTTON2_DOWN_MASK)) == 0) {
        int offset = logicalPositionToOffset(xyToLogicalPosition(e.getPoint()));
        if (getSelectionModel().getSelectionStart() <= offset && offset < getSelectionModel().getSelectionEnd()) {
          UIUtil.setCursor(myEditorComponent, Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
          return;
        }
      }
      if (!IdeGlassPaneImpl.hasPreProcessedCursor(myEditorComponent)) {
        UIUtil.setCursor(myEditorComponent, UIUtil.getTextCursor(getBackgroundColor()));
      }
    }
  }

  private void runMouseDraggedCommand(@NotNull final MouseEvent e) {
    if (myCommandProcessor == null || myMousePressedEvent != null && myMousePressedEvent.isConsumed()) {
      return;
    }
    myCommandProcessor.executeCommand(myProject, () -> processMouseDragged(e), "", MOUSE_DRAGGED_GROUP, UndoConfirmationPolicy.DEFAULT, getDocument());
  }

  private void processMouseDragged(@NotNull MouseEvent e) {
    if (!JBSwingUtilities.isLeftMouseButton(e) && !JBSwingUtilities.isMiddleMouseButton(e)) {
      return;
    }

    EditorMouseEventArea eventArea = getMouseEventArea(e);
    if (eventArea == EditorMouseEventArea.ANNOTATIONS_AREA) return;
    if (eventArea == EditorMouseEventArea.LINE_MARKERS_AREA ||
        eventArea == EditorMouseEventArea.FOLDING_OUTLINE_AREA && !isInsideGutterWhitespaceArea(e)) {
      // The general idea is that we don't want to change caret position on gutter marker area click (e.g. on setting a breakpoint)
      // but do want to allow bulk selection on gutter marker mouse drag. However, when a drag is performed, the first event is
      // a 'mouse pressed' event, that's why we remember target line on 'mouse pressed' processing and use that information on
      // further dragging (if any).
      if (myDragOnGutterSelectionStartLine >= 0) {
        mySelectionModel.removeSelection();
        myCaretModel.moveToOffset(myDragOnGutterSelectionStartLine < myDocument.getLineCount()
                                  ? myDocument.getLineStartOffset(myDragOnGutterSelectionStartLine) : myDocument.getTextLength());
      }
      myDragOnGutterSelectionStartLine = - 1;
    }

    boolean columnSelectionDragEvent = isColumnSelectionDragEvent(e);
    boolean toggleCaretEvent = isToggleCaretEvent(e);
    boolean addRectangularSelectionEvent = isAddRectangularSelectionEvent(e);
    boolean columnSelectionDrag = isColumnMode() && !myLastPressCreatedCaret || columnSelectionDragEvent;
    if (!columnSelectionDragEvent && toggleCaretEvent && !myLastPressCreatedCaret) {
      return; // ignoring drag after removing a caret
    }

    Rectangle visibleArea = getScrollingModel().getVisibleArea();

    int x = e.getX();

    if (e.getSource() == myGutterComponent) {
      x = 0;
    }

    int dx = 0;
    if (x < visibleArea.x && visibleArea.x > 0) {
      dx = x - visibleArea.x;
    }
    else {
      if (x > visibleArea.x + visibleArea.width) {
        dx = x - visibleArea.x - visibleArea.width;
      }
    }

    int dy = 0;
    int y = e.getY();
    if (y < visibleArea.y && visibleArea.y > 0) {
      dy = y - visibleArea.y;
    }
    else {
      if (y > visibleArea.y + visibleArea.height) {
        dy = y - visibleArea.y - visibleArea.height;
      }
    }
    if (dx == 0 && dy == 0) {
      myScrollingTimer.stop();

      SelectionModel selectionModel = getSelectionModel();
      Caret leadCaret = getLeadCaret();
      int oldSelectionStart = leadCaret.getLeadSelectionOffset();
      VisualPosition oldVisLeadSelectionStart = leadCaret.getLeadSelectionPosition();
      int oldCaretOffset = getCaretModel().getOffset();
      boolean multiCaretSelection = columnSelectionDrag || toggleCaretEvent;
      VisualPosition newVisualCaret = getTargetPosition(x, y, !multiCaretSelection);
      LogicalPosition newLogicalCaret = visualToLogicalPosition(newVisualCaret);
      if (multiCaretSelection) {
        myMultiSelectionInProgress = true;
        myRectangularSelectionInProgress = columnSelectionDrag || addRectangularSelectionEvent;
        myTargetMultiSelectionPosition = xyToVisualPosition(new Point(Math.max(x, 0), Math.max(y, 0)));
      }
      else {
        getCaretModel().moveToVisualPosition(newVisualCaret);
      }

      int newCaretOffset = getCaretModel().getOffset();
      newVisualCaret = getCaretModel().getVisualPosition();
      int caretShift = newCaretOffset - mySavedSelectionStart;

      if (myMousePressedEvent != null && getMouseEventArea(myMousePressedEvent) != EditorMouseEventArea.EDITING_AREA &&
          getMouseEventArea(myMousePressedEvent) != EditorMouseEventArea.LINE_NUMBERS_AREA) {
        selectionModel.setSelection(oldSelectionStart, newCaretOffset);
      }
      else {
        if (multiCaretSelection) {
          if (myLastMousePressedLocation != null && (myCurrentDragIsSubstantial || !newLogicalCaret.equals(myLastMousePressedLocation))) {
            createSelectionTill(newLogicalCaret);
            blockActionsIfNeeded(e, myLastMousePressedLocation, newLogicalCaret);
          }
        }
        else {
          if (getMouseSelectionState() != MOUSE_SELECTION_STATE_NONE) {
            if (caretShift < 0) {
              int newSelection = newCaretOffset;
              if (getMouseSelectionState() == MOUSE_SELECTION_STATE_WORD_SELECTED) {
                newSelection = myCaretModel.getWordAtCaretStart();
              }
              else {
                if (getMouseSelectionState() == MOUSE_SELECTION_STATE_LINE_SELECTED) {
                  newSelection =
                    logicalPositionToOffset(visualToLogicalPosition(new VisualPosition(getCaretModel().getVisualPosition().line, 0)));
                }
              }
              if (newSelection < 0) newSelection = newCaretOffset;
              selectionModel.setSelection(mySavedSelectionEnd, newSelection);
              getCaretModel().moveToOffset(newSelection);
            }
            else {
              int newSelection = newCaretOffset;
              if (getMouseSelectionState() == MOUSE_SELECTION_STATE_WORD_SELECTED) {
                newSelection = myCaretModel.getWordAtCaretEnd();
              }
              else {
                if (getMouseSelectionState() == MOUSE_SELECTION_STATE_LINE_SELECTED) {
                  newSelection =
                    logicalPositionToOffset(visualToLogicalPosition(new VisualPosition(getCaretModel().getVisualPosition().line + 1, 0)));
                }
              }
              if (newSelection < 0) newSelection = newCaretOffset;
              selectionModel.setSelection(mySavedSelectionStart, newSelection);
              getCaretModel().moveToOffset(newSelection);
            }
            cancelAutoResetForMouseSelectionState();
            return;
          }

          if (!myMousePressedInsideSelection) {
            // There is a possible case that lead selection position should be adjusted in accordance with the mouse move direction.
            // E.g. consider situation when user selects the whole line by clicking at 'line numbers' area. 'Line end' is considered
            // to be lead selection point then. However, when mouse is dragged down we want to consider 'line start' to be
            // lead selection point.
            if ((myMousePressArea == EditorMouseEventArea.LINE_NUMBERS_AREA
                 || myMousePressArea == EditorMouseEventArea.LINE_MARKERS_AREA)
                && selectionModel.hasSelection()) {
              if (newCaretOffset >= selectionModel.getSelectionEnd()) {
                oldSelectionStart = selectionModel.getSelectionStart();
                oldVisLeadSelectionStart = selectionModel.getSelectionStartPosition();
              }
              else if (newCaretOffset <= selectionModel.getSelectionStart()) {
                oldSelectionStart = selectionModel.getSelectionEnd();
                oldVisLeadSelectionStart = selectionModel.getSelectionEndPosition();
              }
            }
            if (oldVisLeadSelectionStart != null) {
              setSelectionAndBlockActions(e, oldVisLeadSelectionStart, oldSelectionStart, newVisualCaret, newCaretOffset);
            }
            else {
              setSelectionAndBlockActions(e, oldSelectionStart, newCaretOffset);
            }
            cancelAutoResetForMouseSelectionState();
          }
          else {
            if (caretShift != 0) {
              if (myMousePressedEvent != null) {
                if (mySettings.isDndEnabled()) {
                  boolean isCopy = UIUtil.isControlKeyDown(e) || isViewer() || !getDocument().isWritable();
                  mySavedCaretOffsetForDNDUndoHack = oldCaretOffset;
                  getContentComponent().getTransferHandler().exportAsDrag(getContentComponent(), e, isCopy ? TransferHandler.COPY 
                                                                                                           : TransferHandler.MOVE);
                }
                else {
                  selectionModel.removeSelection();
                }
              }
            }
          }
        }
      }
    }
    else {
      myScrollingTimer.start(dx, dy);
      onSubstantialDrag(e);
    }
  }

  private void clearDnDContext() {
    if (myDraggedRange != null) {
      myDraggedRange.dispose();
      myDraggedRange = null;
    }
    myGutterComponent.myDnDInProgress = false;
  }

  private void createSelectionTill(@NotNull LogicalPosition targetPosition) {
    List<CaretState> caretStates = new ArrayList<>(myCaretStateBeforeLastPress);
    if (myRectangularSelectionInProgress) {
      caretStates.addAll(EditorModificationUtil.calcBlockSelectionState(this, myLastMousePressedLocation, targetPosition));
    }
    else {
      LogicalPosition selectionStart = myLastMousePressedLocation;
      LogicalPosition selectionEnd = targetPosition;
      if (getMouseSelectionState() != MOUSE_SELECTION_STATE_NONE) {
        int newCaretOffset = logicalPositionToOffset(targetPosition);
        if (newCaretOffset < mySavedSelectionStart) {
          selectionStart = offsetToLogicalPosition(mySavedSelectionEnd);
          if (getMouseSelectionState() == MOUSE_SELECTION_STATE_LINE_SELECTED) {
            targetPosition = selectionEnd = visualToLogicalPosition(new VisualPosition(offsetToVisualLine(newCaretOffset), 0));
          }
        }
        else {
          selectionStart = offsetToLogicalPosition(mySavedSelectionStart);
          int selectionEndOffset = Math.max(newCaretOffset, mySavedSelectionEnd);
          if (getMouseSelectionState() == MOUSE_SELECTION_STATE_WORD_SELECTED) {
            targetPosition = selectionEnd = offsetToLogicalPosition(selectionEndOffset);
          }
          else if (getMouseSelectionState() == MOUSE_SELECTION_STATE_LINE_SELECTED) {
            targetPosition = selectionEnd = visualToLogicalPosition(new VisualPosition(offsetToVisualLine(selectionEndOffset) + 1, 0));
          }
        }
        cancelAutoResetForMouseSelectionState();
      }
      caretStates.add(new CaretState(targetPosition, selectionStart, selectionEnd));
    }
    myCaretModel.setCaretsAndSelections(caretStates);
  }

  private Caret getLeadCaret() {
    List<Caret> allCarets = myCaretModel.getAllCarets();
    Caret firstCaret = allCarets.get(0);
    if (firstCaret == myCaretModel.getPrimaryCaret()) {
      return allCarets.get(allCarets.size() - 1);
    }
    return firstCaret;
  }

  private void setSelectionAndBlockActions(@NotNull MouseEvent mouseDragEvent, int startOffset, int endOffset) {
    mySelectionModel.setSelection(startOffset, endOffset);
    if (myCurrentDragIsSubstantial || startOffset != endOffset) {
      onSubstantialDrag(mouseDragEvent);
    }
  }

  private void setSelectionAndBlockActions(@NotNull MouseEvent mouseDragEvent, VisualPosition startPosition, int startOffset, VisualPosition endPosition, int endOffset) {
    mySelectionModel.setSelection(startPosition, startOffset, endPosition, endOffset);
    if (myCurrentDragIsSubstantial || startOffset != endOffset || !Comparing.equal(startPosition, endPosition)) {
      onSubstantialDrag(mouseDragEvent);
    }
  }

  private void blockActionsIfNeeded(@NotNull MouseEvent mouseDragEvent, @NotNull LogicalPosition startPosition, @NotNull LogicalPosition endPosition) {
    if (myCurrentDragIsSubstantial || !startPosition.equals(endPosition)) {
      onSubstantialDrag(mouseDragEvent);
    }
  }

  private void onSubstantialDrag(@NotNull MouseEvent mouseDragEvent) {
    IdeEventQueue.getInstance().blockNextEvents(mouseDragEvent, IdeEventQueue.BlockMode.ACTIONS);
    myCurrentDragIsSubstantial = true;
  }

  private static class RepaintCursorCommand implements Runnable {
    private long mySleepTime = 500;
    private boolean myIsBlinkCaret = true;
    @Nullable private EditorImpl myEditor;
    private ScheduledFuture<?> mySchedulerHandle;

    public void start() {
      if (mySchedulerHandle != null) {
        mySchedulerHandle.cancel(false);
      }
      mySchedulerHandle = EdtExecutorService.getScheduledExecutorInstance().scheduleWithFixedDelay(this, mySleepTime, mySleepTime, TimeUnit.MILLISECONDS);
    }

    private void setBlinkPeriod(int blinkPeriod) {
      mySleepTime = blinkPeriod > 10 ? blinkPeriod : 10;
      start();
    }

    private void setBlinkCaret(boolean value) {
      myIsBlinkCaret = value;
    }

    @Override
    public void run() {
      if (myEditor != null) {
        CaretCursor activeCursor = myEditor.myCaretCursor;

        long time = System.currentTimeMillis();
        time -= activeCursor.myStartTime;

        if (time > mySleepTime) {
          boolean toRepaint = true;
          if (myIsBlinkCaret) {
            activeCursor.myIsShown = !activeCursor.myIsShown;
          }
          else {
            toRepaint = !activeCursor.myIsShown;
            activeCursor.myIsShown = true;
          }

          if (toRepaint) {
            activeCursor.repaint();
          }
        }
      }
    }
  }

  void updateCaretCursor() {
    myUpdateCursor = true;
  }

  private void setCursorPosition() {
    final List<CaretRectangle> caretPoints = new ArrayList<>();
    for (Caret caret : getCaretModel().getAllCarets()) {
      boolean isRtl = caret.isAtRtlLocation();
      VisualPosition caretPosition = caret.getVisualPosition();
      Point pos1 = visualPositionToXY(caretPosition.leanRight(!isRtl));
      Point pos2 = visualPositionToXY(new VisualPosition(caretPosition.line, Math.max(0, caretPosition.column + (isRtl ? -1 : 1)), isRtl));
      int width = Math.abs(pos2.x - pos1.x);
      if (!isRtl && myInlayModel.hasInlineElementAt(caretPosition)) {
        width = Math.min(width, myView.getPlainSpaceWidth());
      }
      caretPoints.add(new CaretRectangle(pos1, width, caret, isRtl));
    }
    myCaretCursor.setPositions(caretPoints.toArray(new CaretRectangle[caretPoints.size()]));
  }

  @Override
  public boolean setCaretVisible(boolean b) {
    boolean old = myCaretCursor.isActive();
    if (b) {
      myCaretCursor.activate();
    }
    else {
      myCaretCursor.passivate();
    }
    return old;
  }

  @Override
  public boolean setCaretEnabled(boolean enabled) {
    boolean old = myCaretCursor.isEnabled();
    myCaretCursor.setEnabled(enabled);
    return old;
  }

  @Override
  public void addFocusListener(@NotNull FocusChangeListener listener) {
    myFocusListeners.add(listener);
  }

  @Override
  public void addFocusListener(@NotNull FocusChangeListener listener, @NotNull Disposable parentDisposable) {
    ContainerUtil.add(listener, myFocusListeners, parentDisposable);
  }

  @Override
  @Nullable
  public Project getProject() {
    return myProject;
  }

  @Override
  public boolean isOneLineMode() {
    return myIsOneLineMode;
  }

  @Override
  public boolean isEmbeddedIntoDialogWrapper() {
    return myEmbeddedIntoDialogWrapper;
  }

  @Override
  public void setEmbeddedIntoDialogWrapper(boolean b) {
    assertIsDispatchThread();

    myEmbeddedIntoDialogWrapper = b;
    myScrollPane.setFocusable(!b);
    myEditorComponent.setFocusCycleRoot(!b);
    myEditorComponent.setFocusable(b);
  }

  @Override
  public void setOneLineMode(boolean isOneLineMode) {
    myIsOneLineMode = isOneLineMode;
    getScrollPane().setInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, null);
    reinitSettings();
  }

  public static class CaretRectangle {
    public final Point myPoint;
    public final int myWidth;
    public final Caret myCaret;
    public final boolean myIsRtl;

    private CaretRectangle(Point point, int width, Caret caret, boolean isRtl) {
      myPoint = point;
      myWidth = Math.max(width, 2);
      myCaret = caret;
      myIsRtl = isRtl;
    }
  }

  class CaretCursor {
    private CaretRectangle[] myLocations;
    private boolean myEnabled;

    @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
    private boolean myIsShown;
    private long myStartTime;

    private CaretCursor() {
      myLocations = new CaretRectangle[] {new CaretRectangle(new Point(0, 0), 0, null, false)};
      setEnabled(true);
    }

    public boolean isEnabled() {
      return myEnabled;
    }

    public void setEnabled(boolean enabled) {
      myEnabled = enabled;
    }

    private void activate() {
      final boolean blink = mySettings.isBlinkCaret();
      final int blinkPeriod = mySettings.getCaretBlinkPeriod();
      synchronized (ourCaretBlinkingCommand) {
        ourCaretBlinkingCommand.myEditor = EditorImpl.this;
        ourCaretBlinkingCommand.setBlinkCaret(blink);
        ourCaretBlinkingCommand.setBlinkPeriod(blinkPeriod);
        myIsShown = true;
      }
    }

    public boolean isActive() {
      synchronized (ourCaretBlinkingCommand) {
        return myIsShown;
      }
    }

    private void passivate() {
      synchronized (ourCaretBlinkingCommand) {
        myIsShown = false;
      }
    }

    private void setPositions(CaretRectangle[] locations) {
      myStartTime = System.currentTimeMillis();
      myLocations = locations;
      myIsShown = true;
    }

    private void repaint() {
      myView.repaintCarets();
    }

    @Nullable
    CaretRectangle[] getCaretLocations(boolean onlyIfShown) {
      if (onlyIfShown && (!isEnabled() || !myIsShown || isRendererMode() || !IJSwingUtilities.hasFocus(getContentComponent()))) return null;
      return myLocations;
    }
  }

  private class ScrollingTimer {
    private Timer myTimer;
    private static final int TIMER_PERIOD = 100;
    private static final int CYCLE_SIZE = 20;
    private int myXCycles;
    private int myYCycles;
    private int myDx;
    private int myDy;
    private int xPassedCycles;
    private int yPassedCycles;

    private void start(int dx, int dy) {
      myDx = 0;
      myDy = 0;
      if (dx > 0) {
        myXCycles = CYCLE_SIZE / dx + 1;
        myDx = 1 + dx / CYCLE_SIZE;
      }
      else {
        if (dx < 0) {
          myXCycles = -CYCLE_SIZE / dx + 1;
          myDx = -1 + dx / CYCLE_SIZE;
        }
      }

      if (dy > 0) {
        myYCycles = CYCLE_SIZE / dy + 1;
        myDy = 1 + dy / CYCLE_SIZE;
      }
      else {
        if (dy < 0) {
          myYCycles = -CYCLE_SIZE / dy + 1;
          myDy = -1 + dy / CYCLE_SIZE;
        }
      }

      if (myTimer != null) {
        return;
      }


      myTimer = UIUtil.createNamedTimer("Editor scroll timer", TIMER_PERIOD, e -> {
        if (isDisposed()) {
          stop();
          return;
        }
        myCommandProcessor.executeCommand(myProject, new DocumentRunnable(myDocument, myProject) {
          @Override
          public void run() {
            int oldSelectionStart = mySelectionModel.getLeadSelectionOffset();
            VisualPosition caretPosition = myMultiSelectionInProgress ? myTargetMultiSelectionPosition : getCaretModel().getVisualPosition();
            int column = caretPosition.column;
            xPassedCycles++;
            if (xPassedCycles >= myXCycles) {
              xPassedCycles = 0;
              column += myDx;
            }

            int line = caretPosition.line;
            yPassedCycles++;
            if (yPassedCycles >= myYCycles) {
              yPassedCycles = 0;
              line += myDy;
            }

            line = Math.max(0, line);
            column = Math.max(0, column);
            VisualPosition pos = new VisualPosition(line, column);
            if (!myMultiSelectionInProgress) {
              getCaretModel().moveToVisualPosition(pos);
              getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
            }

            int newCaretOffset = getCaretModel().getOffset();
            int caretShift = newCaretOffset - mySavedSelectionStart;

            if (getMouseSelectionState() != MOUSE_SELECTION_STATE_NONE) {
              if (caretShift < 0) {
                int newSelection = newCaretOffset;
                if (getMouseSelectionState() == MOUSE_SELECTION_STATE_WORD_SELECTED) {
                  newSelection = myCaretModel.getWordAtCaretStart();
                }
                else {
                  if (getMouseSelectionState() == MOUSE_SELECTION_STATE_LINE_SELECTED) {
                    newSelection =
                      logicalPositionToOffset(visualToLogicalPosition(new VisualPosition(getCaretModel().getVisualPosition().line, 0)));
                  }
                }
                if (newSelection < 0) newSelection = newCaretOffset;
                mySelectionModel.setSelection(validateOffset(mySavedSelectionEnd), newSelection);
                getCaretModel().moveToOffset(newSelection);
              }
              else {
                int newSelection = newCaretOffset;
                if (getMouseSelectionState() == MOUSE_SELECTION_STATE_WORD_SELECTED) {
                  newSelection = myCaretModel.getWordAtCaretEnd();
                }
                else {
                  if (getMouseSelectionState() == MOUSE_SELECTION_STATE_LINE_SELECTED) {
                    newSelection = logicalPositionToOffset(
                      visualToLogicalPosition(new VisualPosition(getCaretModel().getVisualPosition().line + 1, 0)));
                  }
                }
                if (newSelection < 0) newSelection = newCaretOffset;
                mySelectionModel.setSelection(validateOffset(mySavedSelectionStart), newSelection);
                getCaretModel().moveToOffset(newSelection);
              }
              return;
            }

            if (myMultiSelectionInProgress && myLastMousePressedLocation != null) {
              myTargetMultiSelectionPosition = pos;
              LogicalPosition newLogicalPosition = visualToLogicalPosition(pos);
              getScrollingModel().scrollTo(newLogicalPosition, ScrollType.RELATIVE);
              createSelectionTill(newLogicalPosition);
            }
            else {
              mySelectionModel.setSelection(oldSelectionStart, getCaretModel().getOffset());
            }
          }
        }, EditorBundle.message("move.cursor.command.name"), DocCommandGroupId.noneGroupId(getDocument()), UndoConfirmationPolicy.DEFAULT,
                                          getDocument());
      });
      myTimer.start();
    }

    private void stop() {
      if (myTimer != null) {
        myTimer.stop();
        myTimer = null;
      }
    }

    private int validateOffset(int offset) {
      if (offset < 0) return 0;
      if (offset > myDocument.getTextLength()) return myDocument.getTextLength();
      return offset;
    }
  }

  private static void updateOpaque(JScrollBar bar) {
    if (bar instanceof OpaqueAwareScrollBar) {
      bar.setOpaque(((OpaqueAwareScrollBar)bar).myOpaque);
    }
  }

  private class OpaqueAwareScrollBar extends JBScrollBar {
    private boolean myOpaque;

    private OpaqueAwareScrollBar(@JdkConstants.AdjustableOrientation int orientation) {
      super(orientation);
      addPropertyChangeListener("opaque", event -> {
        revalidate();
        repaint();
      });
    }

    @Override
    public void setOpaque(boolean opaque) {
      myOpaque = opaque;
      super.setOpaque(opaque || shouldScrollBarBeOpaque());
    }

    @Override
    public boolean isOptimizedDrawingEnabled() {
      return !myBackgroundImageSet;
    }
  }

  private static final Field decrButtonField = ReflectionUtil.getDeclaredField(BasicScrollBarUI.class, "decrButton");
  private static final Field incrButtonField = ReflectionUtil.getDeclaredField(BasicScrollBarUI.class, "incrButton");

  class MyScrollBar extends OpaqueAwareScrollBar {
    @NonNls private static final String APPLE_LAF_AQUA_SCROLL_BAR_UI_CLASS = "apple.laf.AquaScrollBarUI";
    private ScrollBarUI myPersistentUI;

    private MyScrollBar(@JdkConstants.AdjustableOrientation int orientation) {
      super(orientation);
      setPersistentUI(createEditorScrollbarUI(EditorImpl.this));
    }

    void setPersistentUI(ScrollBarUI ui) {
      myPersistentUI = ui;
      setUI(ui);
    }

    @Override
    public void setUI(ScrollBarUI ui) {
      if (myPersistentUI == null) myPersistentUI = ui;
      super.setUI(myPersistentUI);
    }

    /**
     * This is helper method. It returns height of the top (decrease) scroll bar
     * button. Please note, that it's possible to return real height only if scroll bar
     * is instance of BasicScrollBarUI. Otherwise it returns fake (but good enough :) )
     * value.
     */
    int getDecScrollButtonHeight() {
      ScrollBarUI barUI = getUI();
      Insets insets = getInsets();
      int top = Math.max(0, insets.top);
      if (barUI instanceof ButtonlessScrollBarUI) {
        return top + ((ButtonlessScrollBarUI)barUI).getDecrementButtonHeight();
      }
      if (barUI instanceof BasicScrollBarUI) {
        try {
          JButton decrButtonValue = (JButton)decrButtonField.get(barUI);
          LOG.assertTrue(decrButtonValue != null);
          return top + decrButtonValue.getHeight();
        }
        catch (Exception exc) {
          throw new IllegalStateException(exc);
        }
      }
      return top + 15;
    }

    /**
     * This is helper method. It returns height of the bottom (increase) scroll bar
     * button. Please note, that it's possible to return real height only if scroll bar
     * is instance of BasicScrollBarUI. Otherwise it returns fake (but good enough :) )
     * value.
     */
    int getIncScrollButtonHeight() {
      ScrollBarUI barUI = getUI();
      Insets insets = getInsets();
      if (barUI instanceof ButtonlessScrollBarUI) {
        return insets.top + ((ButtonlessScrollBarUI)barUI).getIncrementButtonHeight();
      }
      if (barUI instanceof BasicScrollBarUI) {
        try {
          JButton incrButtonValue = (JButton)incrButtonField.get(barUI);
          LOG.assertTrue(incrButtonValue != null);
          return insets.bottom + incrButtonValue.getHeight();
        }
        catch (Exception exc) {
          throw new IllegalStateException(exc);
        }
      }
      if (APPLE_LAF_AQUA_SCROLL_BAR_UI_CLASS.equals(barUI.getClass().getName())) {
        return insets.bottom + 30;
      }
      return insets.bottom + 15;
    }

    @Override
    public int getUnitIncrement(int direction) {
      JViewport vp = myScrollPane.getViewport();
      Rectangle vr = vp.getViewRect();
      return myEditorComponent.getScrollableUnitIncrement(vr, SwingConstants.VERTICAL, direction);
    }

    @Override
    public int getBlockIncrement(int direction) {
      JViewport vp = myScrollPane.getViewport();
      Rectangle vr = vp.getViewRect();
      return myEditorComponent.getScrollableBlockIncrement(vr, SwingConstants.VERTICAL, direction);
    }

    private void registerRepaintCallback(@Nullable ButtonlessScrollBarUI.ScrollbarRepaintCallback callback) {
      if (myPersistentUI instanceof ButtonlessScrollBarUI) {
        ((ButtonlessScrollBarUI)myPersistentUI).registerRepaintCallback(callback);
      }
    }
  }

  static BasicScrollBarUI createEditorScrollbarUI(@NotNull EditorImpl editor) {
    return new EditorScrollBarUI(editor);
  }

  private static class EditorScrollBarUI extends ButtonlessScrollBarUI.Transparent {
    @NotNull private final EditorImpl myEditor;

    EditorScrollBarUI(@NotNull EditorImpl editor) {
      myEditor = editor;
    }

    @Override
    protected boolean isDark() {
      return myEditor.isDarkEnough();
    }

    @Override
    protected Color adjustColor(Color c) {
      return isMacOverlayScrollbar() ? super.adjustColor(c) : adjustThumbColor(super.adjustColor(c), isDark());
    }
  }

  private MyEditable getViewer() {
    if (myEditable == null) {
      myEditable = new MyEditable();
    }
    return myEditable;
  }

  @Override
  public CopyProvider getCopyProvider() {
    return getViewer();
  }

  @Override
  public CutProvider getCutProvider() {
    return getViewer();
  }

  @Override
  public PasteProvider getPasteProvider() {

    return getViewer();
  }

  @Override
  public DeleteProvider getDeleteProvider() {
    return getViewer();
  }

  private class MyEditable implements CutProvider, CopyProvider, PasteProvider, DeleteProvider, DumbAware {
    @Override
    public void performCopy(@NotNull DataContext dataContext) {
      executeAction(IdeActions.ACTION_EDITOR_COPY, dataContext);
    }

    @Override
    public boolean isCopyEnabled(@NotNull DataContext dataContext) {
      return true;
    }

    @Override
    public boolean isCopyVisible(@NotNull DataContext dataContext) {
      return getSelectionModel().hasSelection(true);
    }

    @Override
    public void performCut(@NotNull DataContext dataContext) {
      executeAction(IdeActions.ACTION_EDITOR_CUT, dataContext);
    }

    @Override
    public boolean isCutEnabled(@NotNull DataContext dataContext) {
      return !isViewer();
    }

    @Override
    public boolean isCutVisible(@NotNull DataContext dataContext) {
      return isCutEnabled(dataContext) && getSelectionModel().hasSelection(true);
    }

    @Override
    public void performPaste(@NotNull DataContext dataContext) {
      executeAction(IdeActions.ACTION_EDITOR_PASTE, dataContext);
    }

    @Override
    public boolean isPastePossible(@NotNull DataContext dataContext) {
      // Copy of isPasteEnabled. See interface method javadoc.
      return !isViewer();
    }

    @Override
    public boolean isPasteEnabled(@NotNull DataContext dataContext) {
      return !isViewer();
    }

    @Override
    public void deleteElement(@NotNull DataContext dataContext) {
      executeAction(IdeActions.ACTION_EDITOR_DELETE, dataContext);
    }

    @Override
    public boolean canDeleteElement(@NotNull DataContext dataContext) {
      return !isViewer();
    }

    private void executeAction(@NotNull String actionId, @NotNull DataContext dataContext) {
      EditorAction action = (EditorAction)ActionManager.getInstance().getAction(actionId);
      if (action != null) {
        action.actionPerformed(EditorImpl.this, dataContext);
      }
    }
  }

  @Override
  public void setColorsScheme(@NotNull EditorColorsScheme scheme) {
    assertIsDispatchThread();
    myScheme = scheme;
    reinitSettings();
  }

  @Override
  @NotNull
  public EditorColorsScheme getColorsScheme() {
    return myScheme;
  }

  static void assertIsDispatchThread() {
    ApplicationManager.getApplication().assertIsDispatchThread();
  }

  private static void assertReadAccess() {
    ApplicationManager.getApplication().assertReadAccessAllowed();
  }

  @Override
  public void setVerticalScrollbarOrientation(int type) {
    assertIsDispatchThread();
    int currentHorOffset = myScrollingModel.getHorizontalScrollOffset();
    myScrollBarOrientation = type;
    myScrollPane.putClientProperty(JBScrollPane.Flip.class,
                                   type == VERTICAL_SCROLLBAR_LEFT
                                   ? JBScrollPane.Flip.HORIZONTAL
                                   : null);
    myScrollingModel.scrollHorizontally(currentHorOffset);
  }

  @Override
  public void setVerticalScrollbarVisible(boolean b) {
    myScrollPane
      .setVerticalScrollBarPolicy(b ? ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS : ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
  }

  @Override
  public void setHorizontalScrollbarVisible(boolean b) {
    myScrollPane.setHorizontalScrollBarPolicy(
      b ? ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED : ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
  }

  @Override
  public int getVerticalScrollbarOrientation() {
    return myScrollBarOrientation;
  }

  public boolean isMirrored() {
    return myScrollBarOrientation != EditorEx.VERTICAL_SCROLLBAR_RIGHT;
  }

  @NotNull
  MyScrollBar getVerticalScrollBar() {
    return myVerticalScrollBar;
  }

  void setHorizontalScrollBarPersistentUI(ScrollBarUI ui) {
    JScrollBar bar = myScrollPane.getHorizontalScrollBar();
    if (bar instanceof MyScrollBar) {
      ((MyScrollBar)bar).setPersistentUI(ui);
    }
  }

  @MouseSelectionState
  private int getMouseSelectionState() {
    return myMouseSelectionState;
  }

  private void setMouseSelectionState(@MouseSelectionState int mouseSelectionState) {
    if (getMouseSelectionState() == mouseSelectionState) return;

    myMouseSelectionState = mouseSelectionState;
    myMouseSelectionChangeTimestamp = System.currentTimeMillis();

    myMouseSelectionStateAlarm.cancelAllRequests();
    if (myMouseSelectionState != MOUSE_SELECTION_STATE_NONE) {
      if (myMouseSelectionStateResetRunnable == null) {
        myMouseSelectionStateResetRunnable = () -> resetMouseSelectionState(null);
      }
      myMouseSelectionStateAlarm.addRequest(myMouseSelectionStateResetRunnable, Registry.intValue("editor.mouseSelectionStateResetTimeout"),
                                            ModalityState.stateForComponent(myEditorComponent));
    }
  }

  private void resetMouseSelectionState(@Nullable MouseEvent event) {
    setMouseSelectionState(MOUSE_SELECTION_STATE_NONE);

    MouseEvent e = event != null ? event : myMouseMovedEvent;
    if (e != null) {
      validateMousePointer(e);
    }
  }

  private void cancelAutoResetForMouseSelectionState() {
    myMouseSelectionStateAlarm.cancelAllRequests();
  }

  void replaceInputMethodText(@NotNull InputMethodEvent e) {
    if (isReleased) return;
    getInputMethodRequests();
    myInputMethodRequestsHandler.replaceInputMethodText(e);
  }

  void inputMethodCaretPositionChanged(@NotNull InputMethodEvent e) {
    if (isReleased) return;
    getInputMethodRequests();
    myInputMethodRequestsHandler.setInputMethodCaretPosition(e);
  }

  @NotNull
  InputMethodRequests getInputMethodRequests() {
    if (myInputMethodRequestsHandler == null) {
      myInputMethodRequestsHandler = new MyInputMethodHandler();
      myInputMethodRequestsSwingWrapper = new MyInputMethodHandleSwingThreadWrapper(myInputMethodRequestsHandler);
    }
    return myInputMethodRequestsSwingWrapper;
  }

  @Override
  public boolean processKeyTyped(@NotNull KeyEvent e) {
    if (e.getID() != KeyEvent.KEY_TYPED) return false;
    char c = e.getKeyChar();
    if (UIUtil.isReallyTypedEvent(e)) { // Hack just like in javax.swing.text.DefaultEditorKit.DefaultKeyTypedAction
      processKeyTyped(c);
      return true;
    }
    else {
      return false;
    }
  }

  void beforeModalityStateChanged() {
    myScrollingModel.beforeModalityStateChanged();
  }

  EditorDropHandler getDropHandler() {
    return myDropHandler;
  }

  public void setDropHandler(@NotNull EditorDropHandler dropHandler) {
    myDropHandler = dropHandler;
  }

  public void setHighlightingFilter(@Nullable Condition<RangeHighlighter> filter) {
    if (myHighlightingFilter == filter) return;
    Condition<RangeHighlighter> oldFilter = myHighlightingFilter;
    myHighlightingFilter = filter;

    for (RangeHighlighter highlighter : myDocumentMarkupModel.getDelegate().getAllHighlighters()) {
      boolean oldAvailable = oldFilter == null || oldFilter.value(highlighter);
      boolean newAvailable = filter == null || filter.value(highlighter);
      if (oldAvailable != newAvailable) {
        myMarkupModelListener.attributesChanged((RangeHighlighterEx)highlighter, true,
                                                EditorUtil.attributesImpactFontStyleOrColor(highlighter.getTextAttributes()));
      }
    }
  }

  boolean isHighlighterAvailable(@NotNull RangeHighlighter highlighter) {
    return myHighlightingFilter == null || myHighlightingFilter.value(highlighter);
  }

  private static class MyInputMethodHandleSwingThreadWrapper implements InputMethodRequests {
    private final InputMethodRequests myDelegate;

    private MyInputMethodHandleSwingThreadWrapper(InputMethodRequests delegate) {
      myDelegate = delegate;
    }

    @NotNull
    @Override
    public Rectangle getTextLocation(final TextHitInfo offset) {
      return execute(() -> myDelegate.getTextLocation(offset));
    }

    @Override
    public TextHitInfo getLocationOffset(final int x, final int y) {
      return execute(() -> myDelegate.getLocationOffset(x, y));
    }

    @Override
    public int getInsertPositionOffset() {
      return execute(myDelegate::getInsertPositionOffset);
    }

    @NotNull
    @Override
    public AttributedCharacterIterator getCommittedText(final int beginIndex, final int endIndex,
                                                        final AttributedCharacterIterator.Attribute[] attributes) {
      return execute(() -> myDelegate.getCommittedText(beginIndex, endIndex, attributes));
    }

    @Override
    public int getCommittedTextLength() {
      return execute(myDelegate::getCommittedTextLength);
    }

    @Override
    @Nullable
    public AttributedCharacterIterator cancelLatestCommittedText(AttributedCharacterIterator.Attribute[] attributes) {
      return null;
    }

    @Override
    public AttributedCharacterIterator getSelectedText(final AttributedCharacterIterator.Attribute[] attributes) {
      return execute(() -> myDelegate.getSelectedText(attributes));
    }

    private static <T> T execute(final Computable<T> computable) {
      return UIUtil.invokeAndWaitIfNeeded(computable);
    }
  }

  private class MyInputMethodHandler implements InputMethodRequests {
    private String composedText;
    private ProperTextRange composedTextRange;

    @NotNull
    @Override
    public Rectangle getTextLocation(TextHitInfo offset) {
      Point caret = logicalPositionToXY(getCaretModel().getLogicalPosition());
      Rectangle r = new Rectangle(caret, new Dimension(1, getLineHeight()));
      Point p = getLocationOnScreen(getContentComponent());
      r.translate(p.x, p.y);

      return r;
    }

    @Override
    @Nullable
    public TextHitInfo getLocationOffset(int x, int y) {
      if (composedText != null) {
        Point p = getLocationOnScreen(getContentComponent());
        p.x = x - p.x;
        p.y = y - p.y;
        int pos = logicalPositionToOffset(xyToLogicalPosition(p));
        if (composedTextRange.containsOffset(pos)) {
          return TextHitInfo.leading(pos - composedTextRange.getStartOffset());
        }
      }
      return null;
    }

    private Point getLocationOnScreen(Component component) {
      Point location = new Point();
      SwingUtilities.convertPointToScreen(location, component);
      if (LOG.isDebugEnabled() && !component.isShowing()) {
        Class<?> type = component.getClass();
        Component parent = component.getParent();
        while (parent != null && !parent.isShowing()) {
          type = parent.getClass();
          parent = parent.getParent();
        }
        String message = type.getName() + " is not showing";
        if (parent != null) message += " on visible  " + parent.getClass().getName();
        LOG.debug(message);
      }
      return location;
    }

    @Override
    public int getInsertPositionOffset() {
      int composedStartIndex = 0;
      int composedEndIndex = 0;
      if (composedText != null) {
        composedStartIndex = composedTextRange.getStartOffset();
        composedEndIndex = composedTextRange.getEndOffset();
      }

      int caretIndex = getCaretModel().getOffset();

      if (caretIndex < composedStartIndex) {
        return caretIndex;
      }
      if (caretIndex < composedEndIndex) {
        return composedStartIndex;
      }
      return caretIndex - (composedEndIndex - composedStartIndex);
    }

    private String getText(int startIdx, int endIdx) {
      if (startIdx >= 0 && endIdx > startIdx) {
        CharSequence chars = getDocument().getImmutableCharSequence();
        return chars.subSequence(startIdx, endIdx).toString();
      }

      return "";
    }

    @NotNull
    @Override
    public AttributedCharacterIterator getCommittedText(int beginIndex, int endIndex, AttributedCharacterIterator.Attribute[] attributes) {
      int composedStartIndex = 0;
      int composedEndIndex = 0;
      if (composedText != null) {
        composedStartIndex = composedTextRange.getStartOffset();
        composedEndIndex = composedTextRange.getEndOffset();
      }

      String committed;
      if (beginIndex < composedStartIndex) {
        if (endIndex <= composedStartIndex) {
          committed = getText(beginIndex, endIndex - beginIndex);
        }
        else {
          int firstPartLength = composedStartIndex - beginIndex;
          committed = getText(beginIndex, firstPartLength) + getText(composedEndIndex, endIndex - beginIndex - firstPartLength);
        }
      }
      else {
        committed = getText(beginIndex + composedEndIndex - composedStartIndex, endIndex - beginIndex);
      }

      return new AttributedString(committed).getIterator();
    }

    @Override
    public int getCommittedTextLength() {
      int length = getDocument().getTextLength();
      if (composedText != null) {
        length -= composedText.length();
      }
      return length;
    }

    @Override
    @Nullable
    public AttributedCharacterIterator cancelLatestCommittedText(AttributedCharacterIterator.Attribute[] attributes) {
      return null;
    }

    @Override
    @Nullable
    public AttributedCharacterIterator getSelectedText(AttributedCharacterIterator.Attribute[] attributes) {
      if (myCharKeyPressed) {
        myNeedToSelectPreviousChar = true;
      }
      String text = getSelectionModel().getSelectedText();
      return text == null ? null : new AttributedString(text).getIterator();
    }

    private void createComposedString(int composedIndex, @NotNull AttributedCharacterIterator text) {
      StringBuffer strBuf = new StringBuffer();

      // create attributed string with no attributes
      for (char c = text.setIndex(composedIndex); c != CharacterIterator.DONE; c = text.next()) {
        strBuf.append(c);
      }

      composedText = new String(strBuf);
    }

    private void setInputMethodCaretPosition(@NotNull InputMethodEvent e) {
      if (composedText != null) {
        int dot = composedTextRange.getStartOffset();

        TextHitInfo caretPos = e.getCaret();
        if (caretPos != null) {
          dot += caretPos.getInsertionIndex();
        }

        getCaretModel().moveToOffset(dot);
        getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      }
    }

    private void runUndoTransparent(@NotNull final Runnable runnable) {
      CommandProcessor.getInstance().runUndoTransparentAction(
        () -> CommandProcessor.getInstance().executeCommand(myProject, () -> ApplicationManager.getApplication().runWriteAction(runnable), "", getDocument(), UndoConfirmationPolicy.DEFAULT, getDocument()));
    }

    private void replaceInputMethodText(@NotNull InputMethodEvent e) {
      if (myNeedToSelectPreviousChar && SystemInfo.isMac &&
          (Registry.is("ide.mac.pressAndHold.brute.workaround") || Registry.is("ide.mac.pressAndHold.workaround") && 
                                                                   (e.getCommittedCharacterCount() > 0 || e.getCaret() == null))) {
        // This is required to support input of accented characters using press-and-hold method (http://support.apple.com/kb/PH11264).
        // JDK currently properly supports this functionality only for TextComponent/JTextComponent descendants.
        // For our editor component we need this workaround.
        // After https://bugs.openjdk.java.net/browse/JDK-8074882 is fixed, this workaround should be replaced with a proper solution.
        myNeedToSelectPreviousChar = false;
        getCaretModel().runForEachCaret(caret -> {
          int caretOffset = caret.getOffset();
          if (caretOffset > 0) {
            caret.setSelection(caretOffset - 1, caretOffset);
          }
        });
      }

      int commitCount = e.getCommittedCharacterCount();
      AttributedCharacterIterator text = e.getText();

      // old composed text deletion
      final Document doc = getDocument();

      if (composedText != null) {
        if (!isViewer() && doc.isWritable()) {
          runUndoTransparent(() -> {
            int docLength = doc.getTextLength();
            ProperTextRange range = composedTextRange.intersection(new TextRange(0, docLength));
            if (range != null) {
              doc.deleteString(range.getStartOffset(), range.getEndOffset());
            }
          });
        }
        composedText = null;
      }

      if (text != null) {
        text.first();

        // committed text insertion
        if (commitCount > 0) {
          //noinspection ForLoopThatDoesntUseLoopVariable
          for (char c = text.current(); commitCount > 0; c = text.next(), commitCount--) {
            if (c >= 0x20 && c != 0x7F) { // Hack just like in javax.swing.text.DefaultEditorKit.DefaultKeyTypedAction
              processKeyTyped(c);
            }
          }
        }

        // new composed text insertion
        if (!isViewer() && doc.isWritable()) {
          int composedTextIndex = text.getIndex();
          if (composedTextIndex < text.getEndIndex()) {
            createComposedString(composedTextIndex, text);

            runUndoTransparent(() -> EditorModificationUtil.insertStringAtCaret(EditorImpl.this, composedText, false, false));

            composedTextRange = ProperTextRange.from(getCaretModel().getOffset(), composedText.length());
          }
        }
      }
    }
  }

  private class MyMouseAdapter extends MouseAdapter {
    @Override
    public void mousePressed(@NotNull MouseEvent e) {
      requestFocus();
      runMousePressedCommand(e);
    }

    @Override
    public void mouseReleased(@NotNull MouseEvent e) {
      myMousePressArea = null;
      myLastMousePressedLocation = null;
      runMouseReleasedCommand(e);
      if (!e.isConsumed() && myMousePressedEvent != null && !myMousePressedEvent.isConsumed() &&
          Math.abs(e.getX() - myMousePressedEvent.getX()) < EditorUtil.getSpaceWidth(Font.PLAIN, EditorImpl.this) &&
          Math.abs(e.getY() - myMousePressedEvent.getY()) < getLineHeight()) {
        runMouseClickedCommand(e);
      }
    }

    @Override
    public void mouseEntered(@NotNull MouseEvent e) {
      runMouseEnteredCommand(e);
    }

    @Override
    public void mouseExited(@NotNull MouseEvent e) {
      runMouseExitedCommand(e);
      EditorMouseEvent event = new EditorMouseEvent(EditorImpl.this, e, getMouseEventArea(e));
      if (event.getArea() == EditorMouseEventArea.LINE_MARKERS_AREA) {
        myGutterComponent.mouseExited(e);
      }

      TooltipController.getInstance().cancelTooltip(FOLDING_TOOLTIP_GROUP, e, true);
    }

    private void runMousePressedCommand(@NotNull final MouseEvent e) {
      myLastMousePressedLocation = xyToLogicalPosition(e.getPoint());
      myCaretStateBeforeLastPress = isToggleCaretEvent(e) ? myCaretModel.getCaretsAndSelections() : Collections.emptyList();
      myCurrentDragIsSubstantial = false;
      clearDnDContext();


      myMousePressedEvent = e;
      EditorMouseEvent event = new EditorMouseEvent(EditorImpl.this, e, getMouseEventArea(e));

      myExpectedCaretOffset = logicalPositionToOffset(myLastMousePressedLocation);
      try {
        for (EditorMouseListener mouseListener : myMouseListeners) {
          mouseListener.mousePressed(event);
        }
      }
      finally {
        myExpectedCaretOffset = -1;
      }

      if (event.getArea() == EditorMouseEventArea.LINE_MARKERS_AREA ||
          event.getArea() == EditorMouseEventArea.FOLDING_OUTLINE_AREA && !isInsideGutterWhitespaceArea(e)) {
        myDragOnGutterSelectionStartLine = EditorUtil.yPositionToLogicalLine(EditorImpl.this, e);
      }

      // On some systems (for example on Linux) popup trigger is MOUSE_PRESSED event.
      // But this trigger is always consumed by popup handler. In that case we have to
      // also move caret.
      if (event.isConsumed() && !(event.getMouseEvent().isPopupTrigger() || event.getArea() == EditorMouseEventArea.EDITING_AREA)) {
        return;
      }

      if (myCommandProcessor != null) {
        Runnable runnable = () -> {
          if (processMousePressed(e) && myProject != null && !myProject.isDefault()) {
            IdeDocumentHistory.getInstance(myProject).includeCurrentCommandAsNavigation();
          }
        };
        myCommandProcessor
          .executeCommand(myProject, runnable, "", DocCommandGroupId.noneGroupId(getDocument()), UndoConfirmationPolicy.DEFAULT,
                          getDocument());
      }
      else {
        processMousePressed(e);
      }
      
      invokePopupIfNeeded(event);
    }

    private void runMouseClickedCommand(@NotNull final MouseEvent e) {
      EditorMouseEvent event = new EditorMouseEvent(EditorImpl.this, e, getMouseEventArea(e));
      for (EditorMouseListener listener : myMouseListeners) {
        listener.mouseClicked(event);
        if (event.isConsumed()) {
          e.consume();
          return;
        }
      }
    }

    private void runMouseReleasedCommand(@NotNull final MouseEvent e) {
      myMultiSelectionInProgress = false;
      myDragOnGutterSelectionStartLine = -1;
      myScrollingTimer.stop();

      if (e.isConsumed()) {
        return;
      }

      EditorMouseEvent event = new EditorMouseEvent(EditorImpl.this, e, getMouseEventArea(e));
      invokePopupIfNeeded(event);
      if (event.isConsumed()) {
        return;
      }
      for (EditorMouseListener listener : myMouseListeners) {
        listener.mouseReleased(event);
        if (event.isConsumed()) {
          e.consume();
          return;
        }
      }

      if (myCommandProcessor != null) {
        Runnable runnable = () -> processMouseReleased(e);
        myCommandProcessor
          .executeCommand(myProject, runnable, "", DocCommandGroupId.noneGroupId(getDocument()), UndoConfirmationPolicy.DEFAULT,
                          getDocument());
      }
      else {
        processMouseReleased(e);
      }
    }

    private void runMouseEnteredCommand(@NotNull MouseEvent e) {
      EditorMouseEvent event = new EditorMouseEvent(EditorImpl.this, e, getMouseEventArea(e));
      for (EditorMouseListener listener : myMouseListeners) {
        listener.mouseEntered(event);
        if (event.isConsumed()) {
          e.consume();
          return;
        }
      }
    }

    private void runMouseExitedCommand(@NotNull MouseEvent e) {
      EditorMouseEvent event = new EditorMouseEvent(EditorImpl.this, e, getMouseEventArea(e));
      for (EditorMouseListener listener : myMouseListeners) {
        listener.mouseExited(event);
        if (event.isConsumed()) {
          e.consume();
          return;
        }
      }
    }

    private boolean processMousePressed(@NotNull final MouseEvent e) {
      myInitialMouseEvent = e;

      if (myMouseSelectionState != MOUSE_SELECTION_STATE_NONE &&
          System.currentTimeMillis() - myMouseSelectionChangeTimestamp > Registry.intValue(
            "editor.mouseSelectionStateResetTimeout")) {
        resetMouseSelectionState(e);
      }

      int x = e.getX();
      int y = e.getY();

      if (x < 0) x = 0;
      if (y < 0) y = 0;

      final EditorMouseEventArea eventArea = getMouseEventArea(e);
      myMousePressArea = eventArea;
      if (eventArea == EditorMouseEventArea.FOLDING_OUTLINE_AREA) {
        final FoldRegion range = myGutterComponent.findFoldingAnchorAt(x, y);
        if (range != null) {
          final boolean expansion = !range.isExpanded();

          int scrollShift = y - getScrollingModel().getVerticalScrollOffset();
          Runnable processor = () -> {
            myFoldingModel.flushCaretShift();
            range.setExpanded(expansion);
            if (e.isAltDown()) {
              for (FoldRegion region : myFoldingModel.getAllFoldRegions()) {
                if (region.getStartOffset() >= range.getStartOffset() && region.getEndOffset() <= range.getEndOffset()) {
                  region.setExpanded(expansion);
                }
              }
            }
          };
          getFoldingModel().runBatchFoldingOperation(processor);
          y = myGutterComponent.getHeadCenterY(range);
          getScrollingModel().scrollVertically(y - scrollShift);
          myGutterComponent.updateSize();
          validateMousePointer(e);
          e.consume();
          return false;
        }
      }

      if (e.getSource() == myGutterComponent) {
        if (eventArea == EditorMouseEventArea.LINE_MARKERS_AREA ||
            eventArea == EditorMouseEventArea.ANNOTATIONS_AREA ||
            eventArea == EditorMouseEventArea.LINE_NUMBERS_AREA) {
          if (!tweakSelectionIfNecessary(e)) {
            myGutterComponent.mousePressed(e);
          }
          if (e.isConsumed()) return false;
        }
        x = 0;
      }

      int oldSelectionStart = mySelectionModel.getLeadSelectionOffset();

      final int oldStart = mySelectionModel.getSelectionStart();
      final int oldEnd = mySelectionModel.getSelectionEnd();

      boolean toggleCaret = e.getSource() != myGutterComponent && isToggleCaretEvent(e);
      boolean lastPressCreatedCaret = myLastPressCreatedCaret;
      if (e.getClickCount() == 1) {
        myLastPressCreatedCaret = false;
      }
      // Don't move caret on mouse press above gutter line markers area (a place where break points, 'override', 'implements' etc icons
      // are drawn) and annotations area. E.g. we don't want to change caret position if a user sets new break point (clicks
      // at 'line markers' area).
      boolean insideEditorRelatedAreas = eventArea == EditorMouseEventArea.LINE_NUMBERS_AREA ||
                  eventArea == EditorMouseEventArea.EDITING_AREA ||
                  isInsideGutterWhitespaceArea(e);
      if (insideEditorRelatedAreas) {
        VisualPosition visualPosition = getTargetPosition(x, y, true);
        LogicalPosition pos = visualToLogicalPosition(visualPosition);
        if (toggleCaret) {
          Caret caret = getCaretModel().getCaretAt(visualPosition);
          if (e.getClickCount() == 1) {
            if (caret == null) {
              myLastPressCreatedCaret = getCaretModel().addCaret(visualPosition) != null;
            }
            else {
              getCaretModel().removeCaret(caret);
            }
          }
          else if (e.getClickCount() == 3 && lastPressCreatedCaret) {
            getCaretModel().moveToVisualPosition(visualPosition);
          }
        }
        else if (e.getSource() != myGutterComponent && isCreateRectangularSelectionEvent(e)) {
          CaretState anchorCaretState = myCaretModel.getCaretsAndSelections().get(0);
          LogicalPosition anchor = Objects.equals(anchorCaretState.getCaretPosition(), anchorCaretState.getSelectionStart()) ?
                                   anchorCaretState.getSelectionEnd() : anchorCaretState.getSelectionStart();
          if (anchor == null) anchor = myCaretModel.getLogicalPosition();
          mySelectionModel.setBlockSelection(anchor, pos);
        }
        else {
          getCaretModel().removeSecondaryCarets();
          getCaretModel().moveToVisualPosition(visualPosition);
        }
      }

      if (e.isPopupTrigger()) return false;

      requestFocus();

      int caretOffset = getCaretModel().getOffset();

      int newStart = mySelectionModel.getSelectionStart();
      int newEnd = mySelectionModel.getSelectionEnd();

      myMouseSelectedRegion = myFoldingModel.getFoldingPlaceholderAt(new Point(x, y));
      myMousePressedInsideSelection = mySelectionModel.hasSelection() && caretOffset >= mySelectionModel.getSelectionStart() &&
                                      caretOffset <= mySelectionModel.getSelectionEnd();

      boolean isNavigation = oldStart == oldEnd && newStart == newEnd && oldStart != newStart;
      if (getMouseEventArea(e) == EditorMouseEventArea.LINE_NUMBERS_AREA && e.getClickCount() == 1) {
        mySelectionModel.selectLineAtCaret();
        setMouseSelectionState(MOUSE_SELECTION_STATE_LINE_SELECTED);
        mySavedSelectionStart = mySelectionModel.getSelectionStart();
        mySavedSelectionEnd = mySelectionModel.getSelectionEnd();
        return isNavigation;
      }

      if (insideEditorRelatedAreas) {
        if (e.isShiftDown() && !e.isControlDown() && !e.isAltDown()) {
          if (getMouseSelectionState() != MOUSE_SELECTION_STATE_NONE) {
            if (caretOffset < mySavedSelectionStart) {
              mySelectionModel.setSelection(mySavedSelectionEnd, caretOffset);
            }
            else {
              mySelectionModel.setSelection(mySavedSelectionStart, caretOffset);
            }
          }
          else {
            int startToUse = oldSelectionStart;
            if (mySelectionModel.isUnknownDirection() && caretOffset > startToUse) {
              startToUse = Math.min(oldStart, oldEnd);
            }
            mySelectionModel.setSelection(startToUse, caretOffset);
          }
        }
        else {
          if (!myMousePressedInsideSelection && getSelectionModel().hasSelection()) {
            setMouseSelectionState(MOUSE_SELECTION_STATE_NONE);
            mySelectionModel.setSelection(caretOffset, caretOffset);
          }
          else {
            if (e.getButton() == MouseEvent.BUTTON1
                && (eventArea == EditorMouseEventArea.EDITING_AREA || eventArea == EditorMouseEventArea.LINE_NUMBERS_AREA)
                && (!toggleCaret || lastPressCreatedCaret)) {
              switch (e.getClickCount()) {
                case 2:
                  selectWordAtCaret(mySettings.isMouseClickSelectionHonorsCamelWords() && mySettings.isCamelWords());
                  break;

                case 3:
                  if (HONOR_CAMEL_HUMPS_ON_TRIPLE_CLICK && mySettings.isCamelWords()) {
                    // We want to differentiate between triple and quadruple clicks when 'select by camel humps' is on. The former
                    // is assumed to select 'hump' while the later points to the whole word.
                    selectWordAtCaret(false);
                    break;
                  }
                  //noinspection fallthrough
                case 4:
                  mySelectionModel.selectLineAtCaret();
                  setMouseSelectionState(MOUSE_SELECTION_STATE_LINE_SELECTED);
                  mySavedSelectionStart = mySelectionModel.getSelectionStart();
                  mySavedSelectionEnd = mySelectionModel.getSelectionEnd();
                  mySelectionModel.setUnknownDirection(true);
                  break;
              }
            }
          }
        }
      }

      return isNavigation;
    }
  }

  private static boolean isColumnSelectionDragEvent(@NotNull MouseEvent e) {
    return e.isAltDown() && !e.isShiftDown() && !e.isControlDown() && !e.isMetaDown();
  }

  private static boolean isToggleCaretEvent(@NotNull MouseEvent e) {
    return isMouseActionEvent(e, IdeActions.ACTION_EDITOR_ADD_OR_REMOVE_CARET) || isAddRectangularSelectionEvent(e);
  }

  private static boolean isAddRectangularSelectionEvent(@NotNull MouseEvent e) {
    return isMouseActionEvent(e, IdeActions.ACTION_EDITOR_ADD_RECTANGULAR_SELECTION_ON_MOUSE_DRAG);
  }

  private static boolean isCreateRectangularSelectionEvent(@NotNull MouseEvent e) {
    return isMouseActionEvent(e, IdeActions.ACTION_EDITOR_CREATE_RECTANGULAR_SELECTION);
  }

  private static boolean isMouseActionEvent(@NotNull MouseEvent e, String actionId) {
    KeymapManager keymapManager = KeymapManager.getInstance();
    if (keymapManager == null) return false;
    Keymap keymap = keymapManager.getActiveKeymap();
    if (keymap == null) return false;
    MouseShortcut mouseShortcut = KeymapUtil.createMouseShortcut(e);
    String[] mappedActions = keymap.getActionIds(mouseShortcut);
    if (!ArrayUtil.contains(actionId, mappedActions)) return false;
    if (mappedActions.length < 2) return true;
    ActionManager actionManager = ActionManager.getInstance();
    for (String mappedActionId : mappedActions) {
      if (actionId.equals(mappedActionId)) continue;
      AnAction action = actionManager.getAction(mappedActionId);
      AnActionEvent actionEvent = AnActionEvent.createFromAnAction(action, e, ActionPlaces.MAIN_MENU,
                                                                   DataManager.getInstance().getDataContext(e.getComponent()));
      if (ActionUtil.lastUpdateAndCheckDumb(action, actionEvent, false)) return false;
    }
    return true;
  }

  private void selectWordAtCaret(boolean honorCamelCase) {
    mySelectionModel.selectWordAtCaret(honorCamelCase);
    setMouseSelectionState(MOUSE_SELECTION_STATE_WORD_SELECTED);
    mySavedSelectionStart = mySelectionModel.getSelectionStart();
    mySavedSelectionEnd = mySelectionModel.getSelectionEnd();
    getCaretModel().moveToOffset(mySavedSelectionEnd);
  }

  /**
   * Allows to answer if given event should tweak editor selection.
   *
   * @param e event for occurred mouse action
   * @return {@code true} if action that produces given event will trigger editor selection change; {@code false} otherwise
   */
  private boolean tweakSelectionEvent(@NotNull MouseEvent e) {
    return getSelectionModel().hasSelection() && e.getButton() == MouseEvent.BUTTON1 && e.isShiftDown()
           && getMouseEventArea(e) == EditorMouseEventArea.LINE_NUMBERS_AREA;
  }

  /**
   * Checks if editor selection should be changed because of click at the given point at gutter and proceeds if necessary.
   * <p/>
   * The main idea is that selection can be changed during left mouse clicks on the gutter line numbers area with hold
   * {@code Shift} button. The selection should be adjusted if necessary.
   *
   * @param e event for mouse click on gutter area
   * @return {@code true} if editor's selection is changed because of the click; {@code false} otherwise
   */
  private boolean tweakSelectionIfNecessary(@NotNull MouseEvent e) {
    if (!tweakSelectionEvent(e)) {
      return false;
    }

    int startSelectionOffset = getSelectionModel().getSelectionStart();
    int startVisLine = offsetToVisualLine(startSelectionOffset);

    int endSelectionOffset = getSelectionModel().getSelectionEnd();
    int endVisLine = offsetToVisualLine(endSelectionOffset - 1);

    int clickVisLine = yToVisibleLine(e.getPoint().y);

    if (clickVisLine < startVisLine) {
      // Expand selection at backward direction.
      int startOffset = logicalPositionToOffset(visualToLogicalPosition(new VisualPosition(clickVisLine, 0)));
      getSelectionModel().setSelection(startOffset, endSelectionOffset);
      getCaretModel().moveToOffset(startOffset);
    }
    else if (clickVisLine > endVisLine) {
      // Expand selection at forward direction.
      int endLineOffset = EditorUtil.getVisualLineEndOffset(this, clickVisLine);
      getSelectionModel().setSelection(getSelectionModel().getSelectionStart(), endLineOffset);
      getCaretModel().moveToOffset(endLineOffset, true);
    }
    else if (startVisLine == endVisLine) {
      // Remove selection
      getSelectionModel().removeSelection();
    }
    else {
      // Reduce selection in backward direction.
      if (getSelectionModel().getLeadSelectionOffset() == endSelectionOffset) {
        if (clickVisLine == startVisLine) {
          clickVisLine++;
        }
        int startOffset = logicalPositionToOffset(visualToLogicalPosition(new VisualPosition(clickVisLine, 0)));
        getSelectionModel().setSelection(startOffset, endSelectionOffset);
        getCaretModel().moveToOffset(startOffset);
      }
      else {
        // Reduce selection is forward direction.
        if (clickVisLine == endVisLine) {
          clickVisLine--;
        }
        int endLineOffset = EditorUtil.getVisualLineEndOffset(this, clickVisLine);
        getSelectionModel().setSelection(startSelectionOffset, endLineOffset);
        getCaretModel().moveToOffset(endLineOffset);
      }
    }
    e.consume();
    return true;
  }

  boolean useEditorAntialiasing() {
    return myUseEditorAntialiasing;
  }

  public void setUseEditorAntialiasing(boolean value) {
    myUseEditorAntialiasing = value;
  }

  private static final TooltipGroup FOLDING_TOOLTIP_GROUP = new TooltipGroup("FOLDING_TOOLTIP_GROUP", 10);

  private class MyMouseMotionListener implements MouseMotionListener {
    @Override
    public void mouseDragged(@NotNull MouseEvent e) {
      if (myDraggedRange != null || myGutterComponent.myDnDInProgress) return; // on Mac we receive events even if drag-n-drop is in progress
      validateMousePointer(e);
      ((TransactionGuardImpl)TransactionGuard.getInstance()).performUserActivity(() -> runMouseDraggedCommand(e));
      EditorMouseEvent event = new EditorMouseEvent(EditorImpl.this, e, getMouseEventArea(e));
      if (event.getArea() == EditorMouseEventArea.LINE_MARKERS_AREA) {
        myGutterComponent.mouseDragged(e);
      }

      for (EditorMouseMotionListener listener : myMouseMotionListeners) {
        listener.mouseDragged(event);
      }
    }

    @Override
    public void mouseMoved(@NotNull MouseEvent e) {
      if (getMouseSelectionState() != MOUSE_SELECTION_STATE_NONE) {
        if (myMousePressedEvent != null && myMousePressedEvent.getComponent() == e.getComponent()) {
          Point lastPoint = myMousePressedEvent.getPoint();
          Point point = e.getPoint();
          int deadZone = Registry.intValue("editor.mouseSelectionStateResetDeadZone");
          if (Math.abs(lastPoint.x - point.x) >= deadZone || Math.abs(lastPoint.y - point.y) >= deadZone) {
            resetMouseSelectionState(e);
          }
        }
        else {
          validateMousePointer(e);
        }
      }
      else {
        validateMousePointer(e);
      }

      myMouseMovedEvent = e;

      EditorMouseEvent event = new EditorMouseEvent(EditorImpl.this, e, getMouseEventArea(e));
      if (e.getSource() == myGutterComponent) {
        myGutterComponent.mouseMoved(e);
      }

      if (event.getArea() == EditorMouseEventArea.EDITING_AREA) {
        FoldRegion fold = myFoldingModel.getFoldingPlaceholderAt(e.getPoint());
        TooltipController controller = TooltipController.getInstance();
        if (fold != null && !fold.shouldNeverExpand()) {
          DocumentFragment range = createDocumentFragment(fold);
          final Point p =
            SwingUtilities.convertPoint((Component)e.getSource(), e.getPoint(), getComponent().getRootPane().getLayeredPane());
          controller.showTooltip(EditorImpl.this, p, new DocumentFragmentTooltipRenderer(range), false, FOLDING_TOOLTIP_GROUP);
        }
        else {
          controller.cancelTooltip(FOLDING_TOOLTIP_GROUP, e, true);
        }
      }

      for (EditorMouseMotionListener listener : myMouseMotionListeners) {
        listener.mouseMoved(event);
      }
    }

    @NotNull
    private DocumentFragment createDocumentFragment(@NotNull FoldRegion fold) {
      final FoldingGroup group = fold.getGroup();
      final int foldStart = fold.getStartOffset();
      if (group != null) {
        final int endOffset = myFoldingModel.getEndOffset(group);
        if (offsetToVisualLine(endOffset) == offsetToVisualLine(foldStart)) {
          return new DocumentFragment(myDocument, foldStart, endOffset);
        }
      }

      final int oldEnd = fold.getEndOffset();
      return new DocumentFragment(myDocument, foldStart, oldEnd);
    }
  }

  private class MyColorSchemeDelegate extends DelegateColorScheme {
    private final FontPreferencesImpl myFontPreferences = new FontPreferencesImpl();
    private final FontPreferencesImpl myConsoleFontPreferences = new FontPreferencesImpl();
    private final Map<TextAttributesKey, TextAttributes> myOwnAttributes   = ContainerUtilRt.newHashMap();
    private final Map<ColorKey, Color>                   myOwnColors       = ContainerUtilRt.newHashMap();
    private final EditorColorsScheme myCustomGlobalScheme;
    private Map<EditorFontType, Font> myFontsMap;
    private int myMaxFontSize = EditorFontsConstants.getMaxEditorFontSize();
    private int myFontSize = -1;
    private int myConsoleFontSize = -1;
    private String myFaceName;

    private MyColorSchemeDelegate(@Nullable EditorColorsScheme globalScheme) {
      super(globalScheme == null ? EditorColorsManager.getInstance().getGlobalScheme() : globalScheme);
      myCustomGlobalScheme = globalScheme;
      updateGlobalScheme();
    }

    private void reinitFonts() {
      EditorColorsScheme delegate = getDelegate();
      String editorFontName = getEditorFontName();
      int editorFontSize = getEditorFontSize();
      updatePreferences(myFontPreferences, editorFontName, editorFontSize, 
                        delegate == null ? null : delegate.getFontPreferences());
      String consoleFontName = getConsoleFontName();
      int consoleFontSize = getConsoleFontSize();
      updatePreferences(myConsoleFontPreferences, consoleFontName, consoleFontSize,
                        delegate == null ? null : delegate.getConsoleFontPreferences());

      myFontsMap = new EnumMap<>(EditorFontType.class);
      myFontsMap.put(EditorFontType.PLAIN, new Font(editorFontName, Font.PLAIN, editorFontSize));
      myFontsMap.put(EditorFontType.BOLD, new Font(editorFontName, Font.BOLD, editorFontSize));
      myFontsMap.put(EditorFontType.ITALIC, new Font(editorFontName, Font.ITALIC, editorFontSize));
      myFontsMap.put(EditorFontType.BOLD_ITALIC, new Font(editorFontName, Font.BOLD | Font.ITALIC, editorFontSize));
      myFontsMap.put(EditorFontType.CONSOLE_PLAIN, new Font(consoleFontName, Font.PLAIN, consoleFontSize));
      myFontsMap.put(EditorFontType.CONSOLE_BOLD, new Font(consoleFontName, Font.BOLD, consoleFontSize));
      myFontsMap.put(EditorFontType.CONSOLE_ITALIC, new Font(consoleFontName, Font.ITALIC, consoleFontSize));
      myFontsMap.put(EditorFontType.CONSOLE_BOLD_ITALIC, new Font(consoleFontName, Font.BOLD | Font.ITALIC, consoleFontSize));
    }

    private void updatePreferences(FontPreferencesImpl preferences, String fontName, int fontSize, FontPreferences delegatePreferences) {
      preferences.clear();
      preferences.register(fontName, fontSize);
      if (delegatePreferences != null) {
        boolean first = true; //skip delegate's primary font
        for (String font : delegatePreferences.getRealFontFamilies()) {
          if (!first) {
            preferences.register(font, fontSize);
          }
          first = false;
        }
      }
      preferences.setUseLigatures(delegatePreferences != null && delegatePreferences.useLigatures());
    }

    private void reinitFontsAndSettings() {
      reinitFonts();
      reinitSettings();
    }

    @Override
    public TextAttributes getAttributes(TextAttributesKey key) {
      if (myOwnAttributes.containsKey(key)) return myOwnAttributes.get(key);
      return getDelegate().getAttributes(key);
    }

    @Override
    public void setAttributes(@NotNull TextAttributesKey key, TextAttributes attributes) {
      myOwnAttributes.put(key, attributes);
    }

    @Nullable
    @Override
    public Color getColor(ColorKey key) {
      if (myOwnColors.containsKey(key)) return myOwnColors.get(key);
      return getDelegate().getColor(key);
    }

    @Override
    public void setColor(ColorKey key, Color color) {
      myOwnColors.put(key, color);

      // These two are here because those attributes are cached and I do not whant the clients to call editor's reinit
      // settings in this case.
      myCaretModel.reinitSettings();
      mySelectionModel.reinitSettings();
    }

    @Override
    public int getEditorFontSize() {
      if (myFontSize == -1) {
        return getDelegate().getEditorFontSize();
      }
      return myFontSize;
    }

    @Override
    public void setEditorFontSize(int fontSize) {
      if (fontSize < MIN_FONT_SIZE) fontSize = MIN_FONT_SIZE;
      if (fontSize > myMaxFontSize) fontSize = myMaxFontSize;
      if (fontSize == myFontSize) return;
      myFontSize = fontSize;
      reinitFontsAndSettings();
    }

    @NotNull
    @Override
    public FontPreferences getFontPreferences() {
      return myFontPreferences.getEffectiveFontFamilies().isEmpty() ? getDelegate().getFontPreferences() : myFontPreferences;
    }

    @Override
    public void setFontPreferences(@NotNull FontPreferences preferences) {
      if (Comparing.equal(preferences, myFontPreferences)) return;
      preferences.copyTo(myFontPreferences);
      reinitFontsAndSettings();
    }

    @NotNull
    @Override
    public FontPreferences getConsoleFontPreferences() {
      return myConsoleFontPreferences.getEffectiveFontFamilies().isEmpty() ? 
             getDelegate().getConsoleFontPreferences() : myConsoleFontPreferences;
    }

    @Override
    public void setConsoleFontPreferences(@NotNull FontPreferences preferences) {
      if (Comparing.equal(preferences, myConsoleFontPreferences)) return;
      preferences.copyTo(myConsoleFontPreferences);
      reinitFontsAndSettings();
    }

    @Override
    public String getEditorFontName() {
      if (myFaceName == null) {
        return getDelegate().getEditorFontName();
      }
      return myFaceName;
    }

    @Override
    public void setEditorFontName(String fontName) {
      if (Comparing.equal(fontName, myFaceName)) return;
      myFaceName = fontName;
      reinitFontsAndSettings();
    }

    @NotNull
    @Override
    public Font getFont(EditorFontType key) {
      if (myFontsMap != null) {
        Font font = myFontsMap.get(key);
        if (font != null) return font;
      }
      return getDelegate().getFont(key);
    }

    @Override
    public void setFont(EditorFontType key, Font font) {
      if (myFontsMap == null) {
        reinitFontsAndSettings();
      }
      myFontsMap.put(key, font);
      reinitSettings();
    }

    @Override
    @Nullable
    public Object clone() {
      return null;
    }

    private void updateGlobalScheme() {
      setDelegate(myCustomGlobalScheme == null ? EditorColorsManager.getInstance().getGlobalScheme() : myCustomGlobalScheme);
    }

    @Override
    public void setDelegate(@NotNull EditorColorsScheme delegate) {
      super.setDelegate(delegate);
      int globalFontSize = getDelegate().getEditorFontSize();
      myMaxFontSize = Math.max(EditorFontsConstants.getMaxEditorFontSize(), globalFontSize);
      reinitFonts();
    }

    @Override
    public void setConsoleFontSize(int fontSize) {
      myConsoleFontSize = fontSize;
      reinitFontsAndSettings();
    }

    @Override
    public int getConsoleFontSize() {
      return myConsoleFontSize == -1 ? super.getConsoleFontSize() : myConsoleFontSize;
    }
  }

  private static class ExplosionPainter extends AbstractPainter {
    private final Point myExplosionLocation;
    private final Image myImage;

    private static final long TIME_PER_FRAME = 30;
    private final int myWidth;
    private final int myHeight;
    private long lastRepaintTime = System.currentTimeMillis();
    private int frameIndex;
    private static final int TOTAL_FRAMES = 8;

    private final AtomicBoolean nrp = new AtomicBoolean(true);

    ExplosionPainter(final Point explosionLocation, Image image) {
      myExplosionLocation = new Point(explosionLocation.x, explosionLocation.y);
      myImage = image;
      myWidth = myImage.getWidth(null);
      myHeight = myImage.getHeight(null);
    }

    @Override
    public void executePaint(Component component, Graphics2D g) {
      if (!nrp.get()) return;

      long currentTimeMillis = System.currentTimeMillis();

      float alpha = 1 - (float)frameIndex / TOTAL_FRAMES;
      g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
      Image scaledImage = ImageUtil.scaleImage(myImage, alpha);

      int x = myExplosionLocation.x - scaledImage.getWidth(null) / 2;
      int y = myExplosionLocation.y - scaledImage.getHeight(null) / 2;

      if (currentTimeMillis - lastRepaintTime < TIME_PER_FRAME) {
        UIUtil.drawImage(g, scaledImage, x, y, null);
        JobScheduler.getScheduler().schedule(() -> component.repaint(x, y, myWidth, myHeight), TIME_PER_FRAME, TimeUnit.MILLISECONDS);
        return;
      }
      lastRepaintTime = currentTimeMillis;
      frameIndex++;
      UIUtil.drawImage(g, scaledImage, x, y, null);
      if (frameIndex == TOTAL_FRAMES) {
        nrp.set(false);
        IdeGlassPane glassPane = IdeGlassPaneUtil.find(component);
        ApplicationManager.getApplication().invokeLater(() -> glassPane.removePainter(this));
        component.repaint(x, y, myWidth, myHeight);
      }
      component.repaint(x, y, myWidth, myHeight);
    }

    @Override
    public boolean needsRepaint() {
      return nrp.get();
    }

  }

  static boolean handleDrop(@NotNull EditorImpl editor, @NotNull final Transferable t, int dropAction) {
    final EditorDropHandler dropHandler = editor.getDropHandler();

    if (Registry.is("debugger.click.disable.breakpoints")) {
      try {
        if (t.isDataFlavorSupported(GutterDraggableObject.flavor)) {
          Object attachedObject = t.getTransferData(GutterDraggableObject.flavor);
          if (attachedObject instanceof GutterIconRenderer) {
            GutterDraggableObject object = ((GutterIconRenderer)attachedObject).getDraggableObject();
            if (object != null) {
              object.remove();
              Point mouseLocationOnScreen = MouseInfo.getPointerInfo().getLocation();
              JComponent editorComponent = editor.getComponent();
              Point editorComponentLocationOnScreen = editorComponent.getLocationOnScreen();
              IdeGlassPaneUtil.installPainter(
                editorComponent,
                new ExplosionPainter(
                  new Point(
                    mouseLocationOnScreen.x - editorComponentLocationOnScreen.x,
                    mouseLocationOnScreen.y - editorComponentLocationOnScreen.y
                  ), editor.getGutterComponentEx().getDragImage((GutterIconRenderer)attachedObject)
                ),
                editor.getDisposable()
              );
              return true;
            }
          }
        }
      }
      catch (UnsupportedFlavorException | IOException e) {
        LOG.warn(e);
      }
    }

    if (dropHandler != null && dropHandler.canHandleDrop(t.getTransferDataFlavors())) {
      dropHandler.handleDrop(t, editor.getProject(), null, dropAction);
      return true;
    }

    final int caretOffset = editor.getCaretModel().getOffset();
    if (editor.myDraggedRange != null
        && editor.myDraggedRange.getStartOffset() <= caretOffset && caretOffset < editor.myDraggedRange.getEndOffset()) {
      return false;
    }

    if (editor.myDraggedRange != null) {
      editor.getCaretModel().moveToOffset(editor.mySavedCaretOffsetForDNDUndoHack);
    }

    CommandProcessor.getInstance().executeCommand(editor.myProject, () -> {
      try {
        editor.getSelectionModel().removeSelection();

        final int offset;
        if (editor.myDraggedRange != null) {
          editor.getCaretModel().moveToOffset(caretOffset);
          offset = caretOffset;
        }
        else {
          offset = editor.getCaretModel().getOffset();
        }
        if (editor.getDocument().getRangeGuard(offset, offset) != null) {
          return;
        }

        editor.putUserData(LAST_PASTED_REGION, null);

        EditorActionHandler pasteHandler = EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_PASTE);
        LOG.assertTrue(pasteHandler instanceof EditorTextInsertHandler, String.valueOf(pasteHandler));
        ((EditorTextInsertHandler)pasteHandler).execute(editor, editor.getDataContext(), () -> t);

        TextRange range = editor.getUserData(LAST_PASTED_REGION);
        if (range != null) {
          editor.getCaretModel().moveToOffset(range.getStartOffset());
          editor.getSelectionModel().setSelection(range.getStartOffset(), range.getEndOffset());
        }
      }
      catch (Exception exception) {
        LOG.error(exception);
      }
    }, EditorBundle.message("paste.command.name"), DND_COMMAND_KEY, UndoConfirmationPolicy.DEFAULT, editor.getDocument());

    return true;
  }

  private static class MyTransferHandler extends TransferHandler {
    private static EditorImpl getEditor(@NotNull JComponent comp) {
      EditorComponentImpl editorComponent = (EditorComponentImpl)comp;
      return editorComponent.getEditor();
    }

    @Override
    public boolean importData(TransferSupport support) {
      Component comp = support.getComponent();
      return comp instanceof JComponent && handleDrop(getEditor((JComponent)comp), support.getTransferable(), support.getDropAction());
    }
    
    @Override
    public boolean canImport(@NotNull JComponent comp, @NotNull DataFlavor[] transferFlavors) {
      Editor editor = getEditor(comp);
      final EditorDropHandler dropHandler = ((EditorImpl)editor).getDropHandler();
      if (dropHandler != null && dropHandler.canHandleDrop(transferFlavors)) {
        return true;
      }

      //should be used a better representation class
      if (Registry.is("debugger.click.disable.breakpoints") && ArrayUtil.contains(GutterDraggableObject.flavor, transferFlavors)) {
        return true;
      }

      if (editor.isViewer()) return false;

      int offset = editor.getCaretModel().getOffset();
      if (editor.getDocument().getRangeGuard(offset, offset) != null) return false;

      return ArrayUtil.contains(DataFlavor.stringFlavor, transferFlavors);
    }

    @Override
    @Nullable
    protected Transferable createTransferable(JComponent c) {
      EditorImpl editor = getEditor(c);
      String s = editor.getSelectionModel().getSelectedText();
      if (s == null) return null;
      int selectionStart = editor.getSelectionModel().getSelectionStart();
      int selectionEnd = editor.getSelectionModel().getSelectionEnd();
      editor.myDraggedRange = editor.getDocument().createRangeMarker(selectionStart, selectionEnd);

      return new StringSelection(s);
    }

    @Override
    public int getSourceActions(@NotNull JComponent c) {
      return COPY_OR_MOVE;
    }

    @Override
    protected void exportDone(@NotNull final JComponent source, @Nullable Transferable data, int action) {
      if (data == null) return;

      final Component last = DnDManager.getInstance().getLastDropHandler();

      if (last != null && !(last instanceof EditorComponentImpl) && !(last instanceof EditorGutterComponentImpl)) return;

      final EditorImpl editor = getEditor(source);
      if (action == MOVE && !editor.isViewer() && editor.myDraggedRange != null) {
        ((TransactionGuardImpl)TransactionGuard.getInstance()).performUserActivity(() -> removeDraggedOutFragment(editor));
      }

      editor.clearDnDContext();
    }

    private static void removeDraggedOutFragment(EditorImpl editor) {
      if (!FileDocumentManager.getInstance().requestWriting(editor.getDocument(), editor.getProject())) {
        return;
      }
      CommandProcessor.getInstance().executeCommand(editor.myProject, () -> ApplicationManager.getApplication().runWriteAction(() -> {
        Document doc = editor.getDocument();
        doc.startGuardedBlockChecking();
        try {
          doc.deleteString(editor.myDraggedRange.getStartOffset(), editor.myDraggedRange.getEndOffset());
        }
        catch (ReadOnlyFragmentModificationException e) {
          EditorActionManager.getInstance().getReadonlyFragmentModificationHandler(doc).handle(e);
        }
        finally {
          doc.stopGuardedBlockChecking();
        }
      }), EditorBundle.message("move.selection.command.name"), DND_COMMAND_KEY, UndoConfirmationPolicy.DEFAULT, editor.getDocument());
    }
  }

  private class EditorDocumentAdapter implements PrioritizedDocumentListener {
    @Override
    public void beforeDocumentChange(@NotNull DocumentEvent e) {
      beforeChangedUpdate();
    }

    @Override
    public void documentChanged(@NotNull DocumentEvent e) {
      changedUpdate(e);
    }

    @Override
    public int getPriority() {
      return EditorDocumentPriorities.EDITOR_DOCUMENT_ADAPTER;
    }
  }

  private class EditorDocumentBulkUpdateAdapter implements DocumentBulkUpdateListener {
    @Override
    public void updateStarted(@NotNull Document doc) {
      if (doc != getDocument()) return;

      bulkUpdateStarted();
    }

    @Override
    public void updateFinished(@NotNull Document doc) {
      if (doc != getDocument()) return;

      bulkUpdateFinished();
    }
  }

  @Override
  @NotNull
  public EditorGutter getGutter() {
    return getGutterComponentEx();
  }

  @Override
  public int calcColumnNumber(@NotNull CharSequence text, int start, int offset, int tabSize) {
    return myView.offsetToLogicalPosition(offset).column;
  }

  public boolean isInDistractionFreeMode() {
    return EditorUtil.isRealFileEditor(this)
           && (Registry.is("editor.distraction.free.mode") || isInPresentationMode());
  }

  boolean isInPresentationMode() {
    return UISettings.getInstance().getPresentationMode() && EditorUtil.isRealFileEditor(this);
  }

  @Override
  public void putInfo(@NotNull Map<String, String> info) {
    final VisualPosition visual = getCaretModel().getVisualPosition();
    info.put("caret", visual.getLine() + ":" + visual.getColumn());
  }

  private void invokePopupIfNeeded(EditorMouseEvent event) {
    if (myContextMenuGroupId != null &&
        event.getArea() == EditorMouseEventArea.EDITING_AREA &&
        event.getMouseEvent().isPopupTrigger() &&
        !event.isConsumed()) {
      String contextMenuGroupId = myContextMenuGroupId;
      Inlay inlay = myInlayModel.getElementAt(event.getMouseEvent().getPoint());
      if (inlay != null) {
        String inlayContextMenuGroupId = inlay.getRenderer().getContextMenuGroupId();
        if (inlayContextMenuGroupId != null) contextMenuGroupId = inlayContextMenuGroupId;
      }
      AnAction action = CustomActionsSchema.getInstance().getCorrectedAction(contextMenuGroupId);
      if (action instanceof ActionGroup) {
        ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.EDITOR_POPUP, (ActionGroup)action);
        MouseEvent e = event.getMouseEvent();
        final Component c = e.getComponent();
        if (c != null && c.isShowing()) {
          popupMenu.getComponent().show(c, e.getX(), e.getY());
        }
        e.consume();
      }
    }
  }

  @TestOnly
  void validateState() {
    myView.validateState();
    mySoftWrapModel.validateState();
    myFoldingModel.validateState();
    myCaretModel.validateState();
    myInlayModel.validateState();
  }

  private class MyScrollPane extends JBScrollPane {
    private MyScrollPane() {
      super(0);
      setupCorners();
    }

    @Override
    public void setUI(ScrollPaneUI ui) {
      super.setUI(ui);
      // disable standard Swing keybindings
      setInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, null);
    }

    @Override
    public void layout() {
      if (isInDistractionFreeMode()) {
        // re-calc gutter extra size after editor size is set
        // & layout once again to avoid blinking
        myGutterComponent.updateSize(true, true);
      }
      super.layout();
    }

    @Override
    protected void processMouseWheelEvent(@NotNull MouseWheelEvent e) {
      if (mySettings.isWheelFontChangeEnabled()) {
        if (EditorUtil.isChangeFontSize(e)) {
          int size = myScheme.getEditorFontSize() - e.getWheelRotation();
          if (size >= MIN_FONT_SIZE) {
            setFontSize(size, SwingUtilities.convertPoint(this, e.getPoint(), getViewport()));
          }
          return;
        }
      }

      super.processMouseWheelEvent(e);
    }

    @NotNull
    @Override
    public JScrollBar createHorizontalScrollBar() {
      return new OpaqueAwareScrollBar(Adjustable.HORIZONTAL);
    }

    @NotNull
    @Override
    public JScrollBar createVerticalScrollBar() {
      return new MyScrollBar(Adjustable.VERTICAL);
    }

    @Override
    protected void setupCorners() {
      super.setupCorners();
      setBorder(new TablessBorder());
    }
  }

  private class TablessBorder extends SideBorder {
    private TablessBorder() {
      super(JBColor.border(), SideBorder.ALL);
    }

    @Override
    public void paintBorder(@NotNull Component c, @NotNull Graphics g, int x, int y, int width, int height) {
      if (c instanceof JComponent) {
        Insets insets = ((JComponent)c).getInsets();
        if (insets.left > 0) {
          super.paintBorder(c, g, x, y, width, height);
        }
        else {
          g.setColor(UIUtil.getPanelBackground());
          g.fillRect(x, y, width, 1);
          g.setColor(Gray._50.withAlpha(90));
          g.fillRect(x, y, width, 1);
        }
      }
    }

    @NotNull
    @Override
    public Insets getBorderInsets(Component c) {
      Container splitters = SwingUtilities.getAncestorOfClass(EditorsSplitters.class, c);
      boolean thereIsSomethingAbove = !SystemInfo.isMac || UISettings.getInstance().getShowMainToolbar() || UISettings.getInstance().getShowNavigationBar() ||
                                      toolWindowIsNotEmpty();
      //noinspection ConstantConditions
      Component header = myHeaderPanel == null ? null : ArrayUtil.getFirstElement(myHeaderPanel.getComponents());
      boolean paintTop = thereIsSomethingAbove && header == null && UISettings.getInstance().getEditorTabPlacement() != SwingConstants.TOP;
      return splitters == null ? super.getBorderInsets(c) : new Insets(paintTop ? 1 : 0, 0, 0, 0);
    }

    private boolean toolWindowIsNotEmpty() {
      if (myProject == null) return false;
      ToolWindowManagerEx m = ToolWindowManagerEx.getInstanceEx(myProject);
      return m != null && !m.getIdsOn(ToolWindowAnchor.TOP).isEmpty();
    }

    @Override
    public boolean isBorderOpaque() {
      return true;
    }
  }

  private class MyHeaderPanel extends JPanel {
    private int myOldHeight;

    private MyHeaderPanel() {
      super(new BorderLayout());
    }

    @Override
    public void revalidate() {
      myOldHeight = getHeight();
      super.revalidate();
    }

    @Override
    protected void validateTree() {
      int height = myOldHeight;
      super.validateTree();
      height -= getHeight();

      if (height != 0 && !(myOldHeight == 0 && getComponentCount() > 0 && getPermanentHeaderComponent() == getComponent(0))) {
        myVerticalScrollBar.setValue(myVerticalScrollBar.getValue() - height);
      }
      myOldHeight = getHeight();
    }
  }

  private class MyTextDrawingCallback implements TextDrawingCallback {
    @Override
    public void drawChars(@NotNull Graphics g,
                          @NotNull char[] data,
                          int start,
                          int end,
                          int x,
                          int y,
                          Color color,
                          @NotNull FontInfo fontInfo)
    {
      myView.drawChars(g, data, start, end, x, y, color, fontInfo);
    }
  }

  private static class NullEditorHighlighter extends EmptyEditorHighlighter {
    private static final TextAttributes NULL_ATTRIBUTES = new TextAttributes();
  
    NullEditorHighlighter() {
      super(NULL_ATTRIBUTES);
    }
  
    @Override
    public void setAttributes(TextAttributes attributes) {}
  
    @Override
    public void setColorScheme(@NotNull EditorColorsScheme scheme) {}
  }
}
