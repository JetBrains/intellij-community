// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeWithMe.ClientId;
import com.intellij.diagnostic.Dumpable;
import com.intellij.ide.*;
import com.intellij.ide.actions.DistractionFreeModeController;
import com.intellij.ide.dnd.DnDManager;
import com.intellij.ide.dnd.DnDManagerImpl;
import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsUtils;
import com.intellij.ide.ui.laf.MouseDragSelectionEventHandler;
import com.intellij.internal.statistic.service.fus.collectors.UIEventLogger;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.*;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.DiffUtil;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.*;
import com.intellij.openapi.editor.actions.ChangeEditorFontSizeStrategy;
import com.intellij.openapi.editor.colors.*;
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.*;
import com.intellij.openapi.editor.ex.util.EditorScrollingPositionKeeper;
import com.intellij.openapi.editor.ex.util.EditorUIUtil;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.ex.util.EmptyEditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterClient;
import com.intellij.openapi.editor.impl.ad.AdTheManager;
import com.intellij.openapi.editor.impl.event.MarkupModelListener;
import com.intellij.openapi.editor.impl.stickyLines.StickyLinesManager;
import com.intellij.openapi.editor.impl.stickyLines.StickyLinesModel;
import com.intellij.openapi.editor.impl.stickyLines.VisualStickyLines;
import com.intellij.openapi.editor.impl.stickyLines.ui.StickyLineShadowPainter;
import com.intellij.openapi.editor.impl.stickyLines.ui.StickyLinesPanel;
import com.intellij.openapi.editor.impl.view.CharacterGrid;
import com.intellij.openapi.editor.impl.view.CharacterGridImpl;
import com.intellij.openapi.editor.impl.view.EditorView;
import com.intellij.openapi.editor.markup.GutterDraggableObject;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.editor.rd.LocalEditorSupportUtil;
import com.intellij.openapi.editor.state.ObservableStateListener;
import com.intellij.openapi.editor.toolbar.floating.EditorFloatingToolbar;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeGlassPaneUtil;
import com.intellij.openapi.wm.impl.IdeBackgroundUtil;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsChangeEvent;
import com.intellij.psi.codeStyle.CodeStyleSettingsListener;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLayeredPane;
import com.intellij.ui.components.JBScrollBar;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.dsl.builder.DslComponentProperty;
import com.intellij.ui.dsl.builder.VerticalComponentGap;
import com.intellij.ui.mac.MacGestureSupportInstaller;
import com.intellij.ui.mac.touchbar.TouchbarSupport;
import com.intellij.ui.paint.PaintUtil;
import com.intellij.ui.paint.PaintUtil.RoundingMode;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.*;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.*;
import com.intellij.util.ui.update.UiNotifyConnector;
import kotlin.Unit;
import org.intellij.lang.annotations.JdkConstants;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.Border;
import javax.swing.plaf.ScrollBarUI;
import javax.swing.plaf.ScrollPaneUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.*;
import java.awt.font.TextHitInfo;
import java.awt.geom.Point2D;
import java.awt.im.InputContext;
import java.awt.im.InputMethodRequests;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.text.CharacterIterator;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;


public final class EditorImpl extends UserDataHolderBase implements EditorEx, HighlighterClient, Queryable, Dumpable,
                                                                    CodeStyleSettingsListener, FocusListener {
  public static final int TEXT_ALIGNMENT_LEFT = 0;
  public static final int TEXT_ALIGNMENT_RIGHT = 1;

  private static final Object CUSTOM_LAYER_MARKER = new Object();
  static final float MIN_FONT_SIZE = 4;
  private static final Logger LOG = Logger.getInstance(EditorImpl.class);
  static final Logger EVENT_LOG = Logger.getInstance("editor.input.events");
  static final Object DND_COMMAND_GROUP = ObjectUtils.sentinel("DndCommand");
  private static final Object MOUSE_DRAGGED_COMMAND_GROUP = ObjectUtils.sentinel("MouseDraggedGroup");
  private static final Key<JComponent> PERMANENT_HEADER = Key.create("PERMANENT_HEADER");
  static final Key<Boolean> CONTAINS_BIDI_TEXT = Key.create("contains.bidi.text");
  public static final Key<Boolean> FORCED_SOFT_WRAPS = Key.create("forced.soft.wraps");
  public static final Key<Boolean> SOFT_WRAPS_EXIST = Key.create("soft.wraps.exist");
  @SuppressWarnings("WeakerAccess")
  public static final Key<Boolean> DISABLE_CARET_POSITION_KEEPING = Key.create("editor.disable.caret.position.keeping");
  public static final Key<Boolean> DISABLE_CARET_SHIFT_ON_WHITESPACE_INSERTION =
    Key.create("editor.disable.caret.shift.on.whitespace.insertion");
  public static final Key<Boolean> DISABLE_REMOVE_ON_DROP = Key.create("editor.disable.remove.on.drop");
  private static final boolean HONOR_CAMEL_HUMPS_ON_TRIPLE_CLICK =
    Boolean.parseBoolean(System.getProperty("idea.honor.camel.humps.on.triple.click"));
  private static final Key<BufferedImage> BUFFER = Key.create("buffer");
  // A cache for CodeStyle.getSettings(myProject, myVirtualFile) and similar file-specific calls.
  // Valid for this.myProject and this.myVirtualFile only.
  // E.g., it is not a valid replacement for CodeStyle.getSettings(myProject).
  @ApiStatus.Internal
  public static final Key<CodeStyleSettings> CODE_STYLE_SETTINGS = Key.create("editor.code.style.settings");
  private final @NotNull DocumentEx myDocument;

  private final JPanel myPanel;
  private final @NotNull MyScrollPane myScrollPane;
  private final @NotNull EditorComponentImpl myEditorComponent;
  private final @NotNull EditorGutterComponentImpl myGutterComponent;
  private final @Nullable StickyLinesManager myStickyLinesManager;
  private final TraceableDisposable myTraceableDisposable = new TraceableDisposable(true);
  private final FocusModeModel myFocusModeModel;
  private volatile long myLastTypedActionTimestamp = -1;
  private String myLastTypedAction;
  private LatencyListener myLatencyPublisher;

  private static final Cursor EMPTY_CURSOR;
  private final Map<Object, Cursor> myCustomCursors = new LinkedHashMap<>();
  private Cursor myDefaultCursor;
  boolean myCursorSetExternally;

  private final @NotNull EditorCaretRepaintService caretRepaintService = EditorCaretRepaintService.getInstance();

  private static final Integer SCROLL_PANE_LAYER = 0;
  private static final Integer STICKY_PANEL_LAYER = 200;
  private static final Integer VERTICAL_SCROLLBAR_LAYER = 250;
  private static final Integer FLOATING_TOOLBAR_LAYER = 300;
  private static final Integer STATUS_COMPONENT_LAYER = 300;

  static {
    Cursor emptyCursor = null;
    if (!GraphicsEnvironment.isHeadless()) {
      try {
        emptyCursor = Toolkit.getDefaultToolkit().createCustomCursor(ImageUtil.createImage(1, 1, BufferedImage.TYPE_INT_ARGB),
                                                                     new Point(),
                                                                     "Empty cursor");
      }
      catch (Exception e) {
        LOG.warn("Couldn't create an empty cursor", e);
      }
    }
    EMPTY_CURSOR = emptyCursor;
  }

  private final CommandProcessor myCommandProcessor;
  private final @NotNull MyScrollBar myVerticalScrollBar;

  private final List<EditorMouseListener> myMouseListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final @NotNull List<EditorMouseMotionListener> myMouseMotionListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  final @NotNull CaretCursor myCaretCursor;
  private final ScrollingTimer myScrollingTimer = new ScrollingTimer();

  private final @NotNull SettingsImpl mySettings;
  private final @NotNull EditorState myState;

  private boolean isReleased;

  private boolean mySuppressPainting;
  private boolean mySuppressDisposedPainting;

  private @Nullable MouseEvent myMousePressedEvent;
  private @Nullable MouseEvent myMouseMovedEvent;

  private final MouseListener myMouseListener = new MyMouseAdapter();
  private final MouseMotionListener myMouseMotionListener = new MyMouseMotionListener();

  /**
   * Holds information about area where mouse was pressed.
   */
  private @Nullable EditorMouseEventArea myMousePressArea;
  private int mySavedSelectionStart = -1;
  private int mySavedSelectionEnd = -1;

  private final PropertyChangeSupport myPropertyChangeSupport = new PropertyChangeSupport(this);
  private final EditorCopyPastProvider myEditable = new EditorCopyPastProvider(this);

  private @NotNull EditorColorSchemeDelegate myScheme;
  private final @NotNull SelectionModelImpl mySelectionModel;
  private final @NotNull EditorMarkupModelImpl myMarkupModel;
  private final @NotNull EditorFilteringMarkupModelEx myEditorFilteringMarkupModel;
  private final @NotNull MarkupModelListener myMarkupModelListener;
  private final @NotNull List<HighlighterListener> myHighlighterListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  private final @NotNull FoldingModelImpl myFoldingModel;
  private final @NotNull ScrollingModelImpl myScrollingModel;
  private final @NotNull CaretModelImpl myCaretModel;
  private final @NotNull SoftWrapModelImpl mySoftWrapModel;
  private final @NotNull InlayModelImpl myInlayModel;

  @MouseSelectionState
  private int myMouseSelectionState;
  private @Nullable FoldRegion myMouseSelectedRegion;
  private PanelWithFloatingToolbar myLayeredPane;

  @MagicConstant(intValues = {MOUSE_SELECTION_STATE_NONE, MOUSE_SELECTION_STATE_LINE_SELECTED, MOUSE_SELECTION_STATE_WORD_SELECTED})
  private @interface MouseSelectionState {
  }

  private static final int MOUSE_SELECTION_STATE_NONE = 0;
  private static final int MOUSE_SELECTION_STATE_WORD_SELECTED = 1;
  private static final int MOUSE_SELECTION_STATE_LINE_SELECTED = 2;

  private volatile EditorHighlighter myHighlighter; // updated in EDT, but can be accessed from other threads (under read action)
  private Disposable myHighlighterDisposable = Disposer.newDisposable();

  private boolean myKeepSelectionOnMousePress;

  private boolean myUpdateCursor;
  private final EditorScrollingPositionKeeper myScrollingPositionKeeper;
  private boolean myRestoreScrollingPosition;
  private int myRangeToRepaintStart;
  private int myRangeToRepaintEnd;

  private final @Nullable Project myProject;
  private long myMouseSelectionChangeTimestamp;
  private int mySavedCaretOffsetForDNDUndoHack;
  private final List<FocusChangeListener> myFocusListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  private EditorInputMethodSupport myInputMethodSupport;
  private final MouseDragSelectionEventHandler mouseDragHandler = new MouseDragSelectionEventHandler(e -> {
    processMouseDragged(e);
    return Unit.INSTANCE;
  });
  private VirtualFile myVirtualFile;
  private @Nullable Dimension myPreferredSize;

  private final SingleEdtTaskScheduler mouseSelectionStateAlarm = SingleEdtTaskScheduler.createSingleEdtTaskScheduler();
  private Runnable mouseSelectionStateResetRunnable;

  private int myDragOnGutterSelectionStartLine = -1;
  private RangeMarker myDraggedRange;
  private boolean myMouseDragStarted;
  private boolean myDragStarted;
  private boolean myDragSelectionStarted;

  private final @NotNull JPanel myHeaderPanel;

  private @Nullable MouseEvent myInitialMouseEvent;
  private boolean myIgnoreMouseEventsConsecutiveToInitial;

  private EditorDropHandler myDropHandler;

  private final HighlighterFilter myHighlightingFilter = new HighlighterFilter();

  private final @NotNull IndentsModel myIndentsModel;

  private int myStickySelectionStart;
  private boolean myPurePaintingMode;

  private final EditorSizeAdjustmentStrategy mySizeAdjustmentStrategy = new EditorSizeAdjustmentStrategy();
  private final Disposable myDisposable = Disposer.newDisposable();

  private List<CaretState> myCaretStateBeforeLastPress;
  LogicalPosition myLastMousePressedLocation;

  private Point myLastMousePressedPoint;
  private boolean myLastPressedOnGutter;
  private boolean myLastPressedOnGutterIcon;
  private VisualPosition myTargetMultiSelectionPosition;
  private boolean myMultiSelectionInProgress;
  private boolean myRectangularSelectionInProgress;
  private boolean myLastPressCreatedCaret;
  private boolean myLastPressWasAtBlockInlay;
  // Set when the selection (normal or block one) initiated by mouse drag becomes noticeable (at least one character is selected).
  // Reset on mouse press event.
  private boolean myCurrentDragIsSubstantial;
  private boolean myForcePushHappened;

  private @Nullable VisualPosition mySuppressedByBreakpointsLastPressPosition;

  private CaretImpl myPrimaryCaret;

  public final boolean myDisableRtl = Registry.is("editor.disable.rtl");

  /**
   * @deprecated use UISettings#getEditorFractionalMetricsHint instead
   */
  @Deprecated(forRemoval = true)
  public Object myFractionalMetricsHintValue = UISettings.getEditorFractionalMetricsHint();

  private final EditorModel myEditorModel;
  private final @Nullable EditorModel myAdEditorModel;

  final EditorView myView;
  final @Nullable EditorView myAdView;

  private final TextDrawingCallback myTextDrawingCallback;

  private @Nullable CharacterGridImpl myCharacterGrid;

  private boolean myCharKeyPressed;
  private boolean myNeedToSelectPreviousChar;

  boolean myDocumentChangeInProgress;
  private boolean myErrorStripeNeedsRepaint;

  private final List<EditorPopupHandler> myPopupHandlers = new ArrayList<>();

  private final ImmediatePainter myImmediatePainter;

  private final List<IntFunction<? extends @NotNull Collection<? extends LineExtensionInfo>>> myLineExtensionPainters = new SmartList<>();

  private volatile int myExpectedCaretOffset = -1;

  private boolean myBackgroundImageSet;

  private final EditorKind myKind;

  private boolean myScrollingToCaret;

  private boolean myIsStickyLinePainting;

  EditorImpl(@NotNull Document document,
             boolean viewer,
             @Nullable Project project,
             @NotNull EditorKind kind,
             @Nullable VirtualFile file,
             @Nullable EditorHighlighter highlighter) {
    assertIsDispatchThread();
    myProject = project;
    myDocument = (DocumentEx)document;
    myVirtualFile = file;
    myState = new EditorState();
    myState.refreshAll();
    final EditorColorsScheme boundColorScheme = createBoundColorSchemeDelegate(null);
    if (boundColorScheme instanceof EditorColorSchemeDelegate) {
      myScheme = (EditorColorSchemeDelegate)boundColorScheme;
    }
    else {
      LOG.warn("createBoundColorSchemeDelegate created delegate of type '%s'. Will wrap it with MyColorSchemeDelegate".formatted(boundColorScheme.getClass()),
                new Throwable());
      myScheme = new EditorColorSchemeDelegate(this, boundColorScheme);
    }
    myScrollPane = new MyScrollPane(); // create UI after scheme initialization
    myScrollPane.setBackground(JBColor.lazy(this::getBackgroundColor));
    myState.setViewer(viewer);
    myKind = kind;
    mySettings = new SettingsImpl(this, kind, project);

    MarkupModelEx documentMarkup = (MarkupModelEx)DocumentMarkupModel.forDocument(myDocument, myProject, true);

    mySelectionModel = new SelectionModelImpl(this);
    myMarkupModel = new EditorMarkupModelImpl(this);
    myEditorFilteringMarkupModel = new EditorFilteringMarkupModelEx(this, documentMarkup);
    myFoldingModel = new FoldingModelImpl(this);
    myCaretModel = new CaretModelImpl(this);
    myScrollingModel = new ScrollingModelImpl(this);
    myInlayModel = new InlayModelImpl(this);
    Disposer.register(myCaretModel, myInlayModel);
    mySoftWrapModel = new SoftWrapModelImpl(this);

    myCommandProcessor = CommandProcessor.getInstance();

    myImmediatePainter = new ImmediatePainter(this);

    myMarkupModelListener = new MarkupModelListener() {
      @Override
      public void afterAdded(@NotNull RangeHighlighterEx highlighter) {
        TextAttributes attributes = highlighter.getTextAttributes(getColorsScheme());
        onHighlighterChanged(highlighter,
                             myGutterComponent.canImpactSize(highlighter),
                             EditorUtil.attributesImpactFontStyle(attributes),
                             EditorUtil.attributesImpactForegroundColor(attributes));
      }

      @Override
      public void afterRemoved(@NotNull RangeHighlighterEx highlighter) {
        TextAttributes attributes = highlighter.getTextAttributes(getColorsScheme());
        onHighlighterChanged(highlighter, myGutterComponent.canImpactSize(highlighter),
                             EditorUtil.attributesImpactFontStyle(attributes),
                             EditorUtil.attributesImpactForegroundColor(attributes));
      }

      @Override
      public void attributesChanged(@NotNull RangeHighlighterEx highlighter,
                                    boolean renderersChanged, boolean fontStyleChanged, boolean foregroundColorChanged) {
        onHighlighterChanged(highlighter, renderersChanged, fontStyleChanged, foregroundColorChanged);
      }
    };

    myIndentsModel = new IndentsModelImpl(this);
    myCaretCursor = new CaretCursor();

    myState.setVerticalScrollBarOrientation(VERTICAL_SCROLLBAR_RIGHT);

    setHighlighter(highlighter == null ? new NullEditorHighlighter() : highlighter);

    new FoldingPopupManager(this);

    myEditorComponent = new EditorComponentImpl(this);
    myVerticalScrollBar = (MyScrollBar)myScrollPane.getVerticalScrollBar();
    if (shouldScrollBarBeOpaque()) {
      myVerticalScrollBar.setOpaque(true);
    }
    myPanel = new JPanel();

    myPanel.putClientProperty(UIUtil.NOT_IN_HIERARCHY_COMPONENTS, (Iterable<? extends Component>)(Iterable<JComponent>)() -> {
      JComponent component = getPermanentHeaderComponent();
      if (component != null && component.getParent() == null) {
        return Collections.singleton(component).iterator();
      }
      return Collections.emptyIterator();
    });
    myPanel.putClientProperty(DslComponentProperty.VERTICAL_COMPONENT_GAP, VerticalComponentGap.BOTH);

    myHeaderPanel = new EditorHeaderPanel(this);
    myGutterComponent = new EditorGutterComponentImpl(this);
    myGutterComponent.putClientProperty(ColorKey.FUNCTION_KEY, (Function<ColorKey, Color>)key -> getColorsScheme().getColor(key));
    initComponent();

    myStickyLinesManager = createStickyLinesPanel();
    if (myStickyLinesManager != null) {
      // remove the status component from the scroll pane and place it on the layered pane where the sticky panel lives,
      // otherwise the sticky panel would overlap the status
      myScrollPane.placeStatusOnTopOfStickyPanel();
    }

    myFocusModeModel = new FocusModeModel(this);
    Disposer.register(myDisposable, myFocusModeModel);

    myEditorModel = new EditorModelImpl(this);
    myAdEditorModel = AdTheManager.getInstance().getEditorModel(this);

    myView = new EditorView(this, myEditorModel);
    myAdView = myAdEditorModel == null ? null : new EditorView(this, myAdEditorModel);

    myTextDrawingCallback = new EditorTextDrawingCallback(myView);

    myView.reinitSettings();
    if (myAdView != null) myAdView.reinitSettings();

    if (LOG.isDebugEnabled()) {
      float scaledEditorFontSize = UISettingsUtils.getInstance().getScaledEditorFontSize();
      int currentFontSize = myScheme.getEditorFontSize();
      LOG.debug(String.format(
        "Creating editor view of type %s.\nCurrent font size: %d\nScaled font size: %f",
        kind, currentFontSize, scaledEditorFontSize));
    }
    myScheme.setEditorFontSize(UISettingsUtils.getInstance().getScaledEditorFontSize());

    myGutterComponent.updateSize();
    myEditorComponent.setSize(getPreferredSize());

    updateCaretCursor();

    if (!ApplicationManager.getApplication().isHeadlessEnvironment() && SystemInfoRt.isMac && SystemInfo.isJetBrainsJvm) {
      MacGestureSupportInstaller.installOnComponent(getComponent(), e -> myForcePushHappened = true);
    }

    myPopupHandlers.add(new DefaultPopupHandler());

    myScrollingPositionKeeper = new EditorScrollingPositionKeeper(this);
    Disposer.register(myDisposable, myScrollingPositionKeeper);

    addListeners();

    if (myAdEditorModel != null) {
      AdTheManager.getInstance().bindEditor(this);
    }
  }

  private void addListeners() {
    myMarkupModel.addErrorMarkerListener(new ErrorStripeListener() {
      @Override
      public void errorMarkerChanged(@NotNull ErrorStripeEvent e) {
        errorStripeMarkerChanged((RangeHighlighterEx)e.getHighlighter());
      }
    }, myCaretModel);

    myEditorFilteringMarkupModel.addMarkupModelListener(myCaretModel, myMarkupModelListener);
    myMarkupModel.addMarkupModelListener(myCaretModel, myMarkupModelListener);
    myDocument.addDocumentListener(myFoldingModel, myCaretModel);
    myDocument.addDocumentListener(myCaretModel, myCaretModel);

    myDocument.addDocumentListener(new EditorDocumentAdapter(), myCaretModel);
    myDocument.addDocumentListener(mySoftWrapModel, myCaretModel);
    myDocument.addDocumentListener(myMarkupModel, myCaretModel);

    myFoldingModel.addListener(mySoftWrapModel, myCaretModel);

    myInlayModel.addListener(myFoldingModel, myCaretModel);
    myInlayModel.addListener(myCaretModel, myCaretModel);

    myCaretModel.addCaretListener(new IndentsModelCaretListener(this));

    myCaretModel.addCaretListener(new CaretListener() {
      @Override
      public void caretPositionChanged(@NotNull CaretEvent e) {
        if (myState.isStickySelection()) {
          int selectionStart = Math.min(myStickySelectionStart, getDocument().getTextLength());
          mySelectionModel.setSelection(selectionStart, myCaretModel.getVisualPosition(), myCaretModel.getOffset());
        }
      }

      @Override
      public void caretAdded(@NotNull CaretEvent e) {
        if (myPrimaryCaret != null) {
          myPrimaryCaret.updateVisualPosition(); // repainting old primary caret's row background
        }
        repaintCaretRegion(e);
        myPrimaryCaret = myCaretModel.getPrimaryCaret();
      }

      @Override
      public void caretRemoved(@NotNull CaretEvent e) {
        repaintCaretRegion(e);
        myPrimaryCaret = myCaretModel.getPrimaryCaret(); // repainting new primary caret's row background
        myPrimaryCaret.updateVisualPosition();
      }
    });

    myCaretModel.addCaretListener(myMarkupModel, myCaretModel);

    mySoftWrapModel.addSoftWrapChangeListener(new SoftWrapChangeListener() {
      @Override
      public void recalculationEnds() {
        if (myCaretModel.isUpToDate()) {
          myCaretModel.updateVisualPosition();
        }
      }

      @Override
      public void softWrapsChanged() {
        myGutterComponent.clearLineToGutterRenderersCache();
      }
    });

    myInlayModel.addListener(new InlayModel.SimpleAdapter() {
      @Override
      public void onUpdated(@NotNull Inlay<?> inlay, int changeFlags) {
        onInlayUpdated(inlay, changeFlags);
      }

      @Override
      public void onBatchModeFinish(@NotNull Editor editor) {
        onInlayBatchModeFinish();
      }
    }, myCaretModel);

    myScrollingModel.addVisibleAreaListener(this::moveCaretIntoViewIfCoveredByToolWindowBelow);
    myScrollingModel.addVisibleAreaListener(myMarkupModel);

    PropertyChangeListener propertyChangeListener = e -> {
      if (Document.PROP_WRITABLE.equals(e.getPropertyName())) {
        myEditorComponent.repaint();
      }
    };
    myDocument.addPropertyChangeListener(propertyChangeListener);
    Disposer.register(myDisposable, () -> myDocument.removePropertyChangeListener(propertyChangeListener));

    CodeStyleSettingsManager.getInstance(myProject).subscribe(this, myDisposable);

    myState.addPropertyChangeListener((event) -> {
      switch (event.getPropertyName()) {
        case EditorState.isInsertModePropertyName -> isInsertModeChanged(event);
        case EditorState.isColumnModePropertyName -> isColumnModeChanged(event);
        case EditorState.isOneLineModePropertyName -> isOneLineModeChanged(event);
        case EditorState.isEmbeddedIntoDialogWrapperPropertyName -> isEmbeddedIntoDialogWrapperChanged(event);
        case EditorState.verticalScrollBarOrientationPropertyName -> verticalScrollBarOrientationChanged(event);
        case EditorState.isStickySelectionPropertyName -> isStickySelectionChanged(event);
        case EditorState.myBorderPropertyName -> borderChanged();
      }
    }, myDisposable);

    ApplicationManager.getApplication().getMessageBus().connect(myDisposable).subscribe(DynamicPluginListener.TOPIC, new DynamicPluginListener() {
        private void clearCachedCodeStyleSettings() {
          putUserData(CODE_STYLE_SETTINGS, null);
        }

        @Override
        public void pluginLoaded(@NotNull IdeaPluginDescriptor pluginDescriptor) {
          clearCachedCodeStyleSettings();
        }

        @Override
        public void pluginUnloaded(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
          clearCachedCodeStyleSettings();
        }
      });
  }

  public void applyFocusMode() {
    myFocusModeModel.applyFocusMode(myCaretModel.getPrimaryCaret());
  }

  public boolean isInFocusMode(@NotNull FoldRegion region) {
    return myFocusModeModel.isInFocusMode(region);
  }

  public Segment getFocusModeRange() {
    return myFocusModeModel.getFocusModeRange();
  }

  public @NotNull FocusModeModel getFocusModeModel() {
    return myFocusModeModel;
  }

  @Override
  public void focusGained(@NotNull FocusEvent e) {
    myCaretCursor.activate();
    for (Caret caret : myCaretModel.getAllCarets()) {
      int caretLine = caret.getLogicalPosition().line;
      repaintLines(caretLine, caretLine);
    }
    fireFocusGained(e);
  }

  @Override
  public void focusLost(@NotNull FocusEvent e) {
    clearCaretThread();
    for (Caret caret : myCaretModel.getAllCarets()) {
      int caretLine = caret.getLogicalPosition().line;
      repaintLines(caretLine, caretLine);
    }
    fireFocusLost(e);
  }

  private void errorStripeMarkerChanged(@NotNull RangeHighlighterEx highlighter) {
    if (myDocument.isInBulkUpdate() || myInlayModel.isInBatchMode()) return; // will be repainted later

    if (myDocumentChangeInProgress) {
      // postpone repaint request, as folding model can be in inconsistent state and so coordinate
      // conversions might give incorrect results
      myErrorStripeNeedsRepaint = true;
      return;
    }

    // optimization: there is no need to repaint error stripe if the highlighter is invisible on it
    if (myFoldingModel.isInBatchFoldingOperation()) {
      myErrorStripeNeedsRepaint = true;
    }
    else {
      int start = highlighter.getAffectedAreaStartOffset();
      int end = highlighter.getAffectedAreaEndOffset();
      EdtInvocationManager.invokeLaterIfNeeded(() -> myMarkupModel.repaint(start, end));
    }
  }
  private record HighlighterChange(int affectedStart,
                                   int affectedEnd,
                                   boolean canImpactGutterSize,
                                   boolean fontStyleChanged,
                                   boolean foregroundColorChanged,
                                   boolean isAccessibleGutterElement,
                                   boolean needRestart) {}
  private final AtomicReference<HighlighterChange> myCompositeHighlighterChange = new AtomicReference<>();
  private void onHighlighterChanged(@NotNull RangeHighlighterEx highlighter,
                                    boolean canImpactGutterSize, boolean fontStyleChanged, boolean foregroundColorChanged) {
    int textLength = myDocument.getTextLength();
    int hstart = MathUtil.clamp(highlighter.getAffectedAreaStartOffset(), 0, textLength);
    int hend = MathUtil.clamp(highlighter.getAffectedAreaEndOffset(), hstart, textLength);
    boolean isAccessibleGutterElement = AccessibleGutterLine.isAccessibleGutterElement(highlighter.getGutterIconRenderer());
    HighlighterChange change = new HighlighterChange(hstart, hend, canImpactGutterSize, fontStyleChanged, foregroundColorChanged, isAccessibleGutterElement, false);
    HighlighterChange oldChange = myCompositeHighlighterChange.getAndAccumulate(change, (change1, change2) -> {
      if (change1 == null) return change2;
      if (change2 == null) return change1;
      int start2 = MathUtil.clamp(Math.min(change1.affectedStart, change2.affectedStart), 0, textLength);
      int end2 = MathUtil.clamp(Math.max(change1.affectedEnd, change2.affectedEnd), 0, textLength);
      return new HighlighterChange(start2, end2,
                                   change1.canImpactGutterSize() || change2.canImpactGutterSize(),
                                   change1.fontStyleChanged() || change2.fontStyleChanged(),
                                   change1.foregroundColorChanged() || change2.foregroundColorChanged(),
                                   change1.isAccessibleGutterElement() || change2.isAccessibleGutterElement(),
                                   change1.needRestart() || change2.needRestart());
    });
    if (oldChange == null || oldChange.needRestart()) {
      EdtInvocationManager.invokeLaterIfNeeded(() -> {
        if (isDisposed() || myDocument.isInBulkUpdate() || myInlayModel.isInBatchMode() || myDocumentChangeInProgress) {
          myCompositeHighlighterChange.getAndUpdate(old -> old == null ? null : new HighlighterChange(old.affectedStart(), old.affectedEnd(), old.canImpactGutterSize(), old.fontStyleChanged(), old.foregroundColorChanged(), old.isAccessibleGutterElement(), true));
          return; // will be repainted later
        }

        HighlighterChange newChange = myCompositeHighlighterChange.getAndSet(null);
        if (newChange == null) return;

        if (newChange.canImpactGutterSize()) {
          updateGutterSize();
        }

        int docTextLength = myDocument.getTextLength();
        int start = MathUtil.clamp(newChange.affectedStart(), 0, docTextLength);
        int end = MathUtil.clamp(newChange.affectedEnd(), start, docTextLength);

        if (myGutterComponent.getCurrentAccessibleLine() != null && newChange.isAccessibleGutterElement()) {
          escapeGutterAccessibleLine(start, end);
        }
        int startLine = myDocument.getLineNumber(start);
        int endLine = myDocument.getLineNumber(end);
        if (start != end && (newChange.fontStyleChanged() || newChange.foregroundColorChanged())) {
          myView.invalidateRange(start, end, newChange.fontStyleChanged());
          if (myAdView != null) {
            myAdView.invalidateRange(start, end, newChange.fontStyleChanged());
          }
        }
        if (!myFoldingModel.isInBatchFoldingOperation()) { // at the end of the batch folding operation everything is repainted
          repaintLines(Math.max(0, startLine - 1), Math.min(endLine + 1, getDocument().getLineCount()));
        }
        updateCaretCursor();
      });
    }
  }

  private void onInlayUpdated(@NotNull Inlay<?> inlay, int changeFlags) {
    if (myDocument.isInBulkUpdate() || myInlayModel.isInBatchMode()) return;
    if ((changeFlags & InlayModel.ChangeFlags.GUTTER_ICON_PROVIDER_CHANGED) != 0) updateGutterSize();
    if (myDocument.isInEventsHandling() ||
        (changeFlags & (InlayModel.ChangeFlags.WIDTH_CHANGED | InlayModel.ChangeFlags.HEIGHT_CHANGED)) == 0) {
      return;
    }
    validateSize();
    int offset = inlay.getOffset();
    Inlay.Placement placement = inlay.getPlacement();
    if (placement == Inlay.Placement.INLINE) {
      repaint(offset, offset, false);
    }
    else if (placement == Inlay.Placement.AFTER_LINE_END) {
      int lineEndOffset = DocumentUtil.getLineEndOffset(offset, myDocument);
      repaint(lineEndOffset, lineEndOffset, false);
    }
    else {
      int visualLine = offsetToVisualLine(offset);
      int y = EditorUtil.getVisualLineAreaStartY(this, visualLine);
      repaintToScreenBottomStartingFrom(y);
    }
  }

  private void onInlayBatchModeFinish() {
    if (myDocument.isInBulkUpdate()) return;
    validateSize();
    updateGutterSize();
    myEditorComponent.repaint();
    myGutterComponent.repaint();
    myMarkupModel.repaint();
    updateCaretCursor();
  }

  private void moveCaretIntoViewIfCoveredByToolWindowBelow(@NotNull VisibleAreaEvent e) {
    ReadAction.run(() -> {
      Rectangle oldRectangle = e.getOldRectangle();
      Rectangle newRectangle = e.getNewRectangle();
      if (!myScrollingToCaret &&
          oldRectangle != null &&
          oldRectangle.height != newRectangle.height &&
          oldRectangle.y == newRectangle.y &&
          newRectangle.height > 0) {
        int caretY = myView.visualLineToY(myCaretModel.getVisualPosition().line);
        if (caretY < oldRectangle.getMaxY() && caretY > newRectangle.getMaxY()) {
          myScrollingToCaret = true;
          ApplicationManager.getApplication().invokeLater(() -> {
            myScrollingToCaret = false;
            if (!isReleased) EditorUtil.runWithAnimationDisabled(this, () -> myScrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE));
          }, ModalityState.any());
        }
      }
    });
  }

  /**
   * This method is intended to control a blit-accelerated scrolling, because transparent scrollbars suppress it.
   * Blit-acceleration copies as much of the rendered area as possible and then repaints only the newly exposed region.
   * It is possible to disable blit-acceleration using by the registry key {@code editor.transparent.scrollbar=true}.
   * Also, when there's a background image, blit-acceleration cannot be used (because of the static overlay).
   * In such cases this method returns {@code false} to use transparent scrollbars as designed.
   * Enabled blit-acceleration improves scrolling performance and reduces CPU usage
   * (especially if drawing is compute-intensive).
   * <p>
   * To have both the hardware acceleration and the background image,
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

  static @NotNull Color adjustThumbColor(@NotNull Color base, boolean dark) {
    return dark ? ColorUtil.withAlpha(ColorUtil.shift(base, 1.35), 0.5)
                : ColorUtil.withAlpha(ColorUtil.shift(base, 0.68), 0.4);
  }

  boolean isDarkEnough() {
    return ColorUtil.isDark(getBackgroundColor());
  }

  private void repaintCaretRegion(@NotNull CaretEvent e) {
    CaretImpl caretImpl = (CaretImpl)e.getCaret();
    caretImpl.updateVisualPosition();
    if (caretImpl.hasSelection()) {
      repaint(caretImpl.getSelectionStart(), caretImpl.getSelectionEnd(), false);
    }
  }

  @Override
  public @NotNull EditorColorsScheme createBoundColorSchemeDelegate(@Nullable EditorColorsScheme customGlobalScheme) {
    return new EditorColorSchemeDelegate(this, customGlobalScheme);
  }

  @Override
  public int getPrefixTextWidthInPixels() {
    return ReadAction.compute(() -> myView.getPrefixTextWidthInPixels()).intValue();
  }

  @Override
  public void setPrefixTextAndAttributes(@Nullable String prefixText, @Nullable TextAttributes attributes) {
    ReadAction.run(() -> {
      mySoftWrapModel.recalculate();
      myView.setPrefix(prefixText, attributes);
      if (myAdView != null) myAdView.setPrefix(prefixText, attributes);
    });
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
  public void registerLineExtensionPainter(@NotNull IntFunction<? extends @NotNull Collection<? extends LineExtensionInfo>> lineExtensionPainter) {
    myLineExtensionPainters.add(lineExtensionPainter);
  }

  public boolean processLineExtensions(int line, @NotNull Processor<? super LineExtensionInfo> processor) {
    for (IntFunction<? extends @NotNull Collection<? extends LineExtensionInfo>> painter : myLineExtensionPainters) {
      for (LineExtensionInfo extension : painter.apply(line)) {
        if (!processor.process(extension)) {
          return false;
        }
      }
    }
    if (myProject != null && myVirtualFile != null) {
      for (EditorLinePainter painter : EditorLinePainter.EP_NAME.getExtensionList()) {
        if (LightEdit.owns(myProject) && !(painter instanceof LightEditCompatible)) {
          continue;
        }

        Collection<LineExtensionInfo> extensions = painter.getLineExtensions(myProject, myVirtualFile, line);
        if (extensions != null) {
          for (LineExtensionInfo extension : extensions) {
            if (!processor.process(extension)) {
              return false;
            }
          }
        }
      }
    }
    return true;
  }

  @Override
  public void registerScrollBarRepaintCallback(@Nullable ButtonlessScrollBarUI.ScrollbarRepaintCallback callback) {
    myVerticalScrollBar.registerRepaintCallback(callback);
  }

  @Override
  public int getExpectedCaretOffset() {
    int expectedCaretOffset = myExpectedCaretOffset;
    return ReadAction.compute(() -> expectedCaretOffset == -1 ? getCaretModel().getOffset() : expectedCaretOffset);
  }

  @Override
  public void setContextMenuGroupId(@Nullable String groupId) {
    myState.setContextMenuGroupId(groupId);
  }

  @Override
  public @Nullable String getContextMenuGroupId() {
    return myState.getContextMenuGroupId();
  }

  @Override
  public void installPopupHandler(@NotNull EditorPopupHandler popupHandler) {
    myPopupHandlers.add(popupHandler);
  }

  @ApiStatus.Internal
  public @NotNull List<EditorPopupHandler> getPopupHandlers() {
    return myPopupHandlers;
  }

  @Override
  public void uninstallPopupHandler(@NotNull EditorPopupHandler popupHandler) {
    myPopupHandlers.remove(popupHandler);
  }

  @Override
  public @Nullable ActionGroup getPopupActionGroup(@NotNull EditorMouseEvent event) {
    if (event.getArea() == EditorMouseEventArea.EDITING_AREA) {
      for (int i = myPopupHandlers.size() - 1; i >= 0; i--) {
        EditorPopupHandler handler = myPopupHandlers.get(i);
        ActionGroup group = handler instanceof ContextMenuPopupHandler o ? o.getActionGroup(event) : null;
        if (group instanceof DefaultActionGroup o && o.getChildrenCount() == 0 &&
            group.getClass() == DefaultActionGroup.class) return group;
        if (group != null) return new EditorMousePopupActionGroup(group, event);
      }
      return null;
    }
    else {
      return myGutterComponent.getPopupActionGroup(event);
    }
  }

  private @Nullable Cursor getCustomCursor() {
    return ContainerUtil.getFirstItem(myCustomCursors.values());
  }

  @Override
  public void setCustomCursor(@NotNull Object requestor, @Nullable Cursor cursor) {
    ReadAction.run(() -> {
      if (cursor == null) {
        myCustomCursors.remove(requestor);
      }
      else {
        myCustomCursors.put(requestor, cursor);
      }
      updateEditorCursor();
    });
  }

  @Override
  public void setViewer(boolean isViewer) {
    myState.setViewer(isViewer);
  }

  @Override
  public boolean isViewer() {
    return myState.isViewer() || myState.isRendererMode();
  }

  @Override
  public boolean isRendererMode() {
    return myState.isRendererMode();
  }

  @Override
  public void setRendererMode(boolean isRendererMode) {
    myState.setRendererMode(isRendererMode);
  }

  @Override
  public void setFile(@NotNull VirtualFile vFile) {
    // yes, compare by instance
    //noinspection UseVirtualFileEquals
    if (vFile != myVirtualFile) {
      myVirtualFile = vFile;
      reinitSettings();
    }
  }

  @Override
  @Nullable
  @ApiStatus.Obsolete
  public VirtualFile getVirtualFile() {
    return myVirtualFile;
  }

  @Override
  public @NotNull SelectionModelImpl getSelectionModel() {
    return mySelectionModel;
  }

  @Override
  public @NotNull MarkupModelEx getMarkupModel() {
    return myMarkupModel;
  }

  @Override
  public @NotNull MarkupModelEx getFilteredDocumentMarkupModel() {
    return myEditorFilteringMarkupModel;
  }

  @Override
  public @NotNull FoldingModelImpl getFoldingModel() {
    return myFoldingModel;
  }

  @Override
  public @NotNull CaretModelImpl getCaretModel() {
    return myCaretModel;
  }

  @Override
  public @NotNull ScrollingModelEx getScrollingModel() {
    return myScrollingModel;
  }

  @Override
  public @NotNull SoftWrapModelImpl getSoftWrapModel() {
    return mySoftWrapModel;
  }

  @Override
  public @NotNull InlayModelImpl getInlayModel() {
    return myInlayModel;
  }

  @Override
  public @NotNull EditorKind getEditorKind() {
    return myKind;
  }

  @Override
  public @NotNull EditorSettings getSettings() {
    // assertReadAccess();
    return mySettings;
  }

  public void resetSizes() {
    myView.reset();
    if (myAdView != null) myAdView.reset();
  }

  @Override
  public void reinitSettings() {
    reinitSettings(true, true);
  }

  @RequiresEdt
  void reinitSettings(boolean updateGutterSize, boolean reinitSettings) {
    myScheme.updateGlobalScheme();

    boolean softWrapsUsedBefore = mySoftWrapModel.isSoftWrappingEnabled();

    if (reinitSettings) {
      mySettings.reinitSettings();
    }
    mySoftWrapModel.reinitSettings();
    myCaretModel.reinitSettings();
    mySelectionModel.reinitSettings();
    caretRepaintService.setBlinking(mySettings.isBlinkCaret());
    caretRepaintService.setBlinkPeriod(mySettings.getCaretBlinkPeriod());
    caretRepaintService.restart();

    myView.reinitSettings();
    if (myAdView != null) myAdView.reinitSettings();
    myFoldingModel.refreshSettings();
    myFoldingModel.rebuild();
    myInlayModel.reinitSettings();

    if (softWrapsUsedBefore ^ mySoftWrapModel.isSoftWrappingEnabled()) {
      validateSize();
    }

    myHighlighter.setColorScheme(myScheme);
    myGutterComponent.reinitSettings(updateGutterSize);
    myGutterComponent.revalidate();

    myEditorComponent.repaint();

    updateCaretCursor();

    if (myInitialMouseEvent != null) {
      myIgnoreMouseEventsConsecutiveToInitial = true;
    }

    ReadAction.run(() -> {
      myCaretModel.updateVisualPosition();
      // make sure carets won't appear at invalid positions (e.g., on Tab width change)
      getCaretModel().doWithCaretMerging(() -> myCaretModel.getAllCarets().forEach(caret -> caret.moveToOffset(caret.getOffset())));
    });

    if (myVirtualFile != null && myProject != null) {
      EditorNotifications.getInstance(myProject).updateNotifications(myVirtualFile);
    }

    if (myFocusModeModel != null) {
      myFocusModeModel.clearFocusMode();
    }

    myFractionalMetricsHintValue = UISettings.getEditorFractionalMetricsHint();

    if (myStickyLinesManager != null) {
      myStickyLinesManager.reinitSettings();
    }
  }

  @Contract("_->fail")
  public void throwDisposalError(@NonNls @NotNull String msg) {
    myTraceableDisposable.throwDisposalError(msg);
  }

  private final Object NON_RELEASABLE_BLOCK_GUARD = ObjectUtils.sentinel("NON_RELEASABLE_BLOCK_GUARD");

  /**
   * During execution of this method, the {@link #release()} call from the other thread is not allowed to run.
   * Can be useful when you need to guarantee the editor is still alive at some point.
   */
  @ApiStatus.Internal
  public void executeNonCancelableBlock(@NotNull Runnable runnable) {
    synchronized (NON_RELEASABLE_BLOCK_GUARD) {
      runnable.run();
    }
  }

  // EditorFactory.releaseEditor should be used to release editor
  @RequiresEdt
  void release() {
    executeNonCancelableBlock(() -> {
      if (isReleased) {
        throwDisposalError("Double release of editor:");
      }
      myTraceableDisposable.kill(null);

      isReleased = true;
      mySizeAdjustmentStrategy.cancelAllRequests();
      cancelAutoResetForMouseSelectionState();

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
      if (myAdView != null) {
        Disposer.dispose(myAdView);
        Disposer.dispose(myAdEditorModel);
      }
      clearCaretThread();

      myFocusListeners.clear();
      myMouseListeners.clear();
      myMouseMotionListeners.clear();

      myEditorComponent.removeFocusListener(this);

      myEditorComponent.removeMouseListener(myMouseListener);
      myGutterComponent.removeMouseListener(myMouseListener);
      myEditorComponent.removeMouseMotionListener(myMouseMotionListener);
      myGutterComponent.removeMouseMotionListener(myMouseMotionListener);

      Disposer.dispose(myDisposable);

      // clear error panel's cached image
      // replace UI with default to release resources (e.g. coroutines) of a custom UI correctly
      myVerticalScrollBar.setPersistentUI(JBScrollBar.createDefaultUI());
    });
  }

  private void clearCaretThread() {
    synchronized (caretRepaintService) {
      if (caretRepaintService.getEditor() == this) {
        caretRepaintService.setEditor(null);
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

    myScrollingModel.initListeners();

    myEditorComponent.setTransferHandler(new EditorTransferHandler());
    myEditorComponent.setAutoscrolls(false); // we have our own auto-scrolling code

    this.myLayeredPane = new PanelWithFloatingToolbar();
    myLayeredPane.add(myScrollPane, SCROLL_PANE_LAYER);
    UiNotifyConnector.doWhenFirstShown(myPanel, () -> {
      if (mayShowToolbar()) {
        myLayeredPane.add(new EditorFloatingToolbar(this), FLOATING_TOOLBAR_LAYER);
      }
    }, getDisposable());
    myPanel.add(myLayeredPane, BorderLayout.CENTER);

    myEditorComponent.addKeyListener(new KeyListener() {
      @Override
      public void keyPressed(@NotNull KeyEvent e) {
        if (EVENT_LOG.isDebugEnabled()) {
          EVENT_LOG.debug(e.toString());
        }
        if (e.getKeyCode() >= KeyEvent.VK_A && e.getKeyCode() <= KeyEvent.VK_Z) {
          myCharKeyPressed = true;
        }
      }

      @Override
      public void keyTyped(@NotNull KeyEvent event) {
        if (EVENT_LOG.isDebugEnabled()) {
          EVENT_LOG.debug(event.toString());
        }
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
        if (EVENT_LOG.isDebugEnabled()) {
          EVENT_LOG.debug(e.toString());
        }
        myCharKeyPressed = false;
      }
    });

    myEditorComponent.addMouseListener(myMouseListener);
    myGutterComponent.addMouseListener(myMouseListener);
    myEditorComponent.addMouseMotionListener(myMouseMotionListener);
    myGutterComponent.addMouseMotionListener(myMouseMotionListener);

    myEditorComponent.addFocusListener(this);

    UiNotifyConnector.doWhenFirstShown(myEditorComponent, myGutterComponent::updateSizeOnShowNotify, getDisposable());

    try {
      DropTarget dropTarget = myEditorComponent.getDropTarget();
      if (dropTarget != null) { // might be null in headless environment
        dropTarget.addDropTargetListener(new DropTargetAdapter() {
          @Override
          public void drop(@NotNull DropTargetDropEvent e) {
          }

          @Override
          public void dragOver(@NotNull DropTargetDragEvent e) {
            Point location = e.getLocation();

            final CaretImpl caret = getCaretModel().getCurrentCaret();
            caret.moveToVisualPosition(getTargetPosition(location.x, location.y, true, caret));
            getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
            requestFocus();
          }
        });
      }
    }
    catch (TooManyListenersException e) {
      LOG.error(e);
    }
    // update area available for soft wrapping on the component shown/hidden
    myPanel.addHierarchyListener(e -> mySoftWrapModel.getApplianceManager().updateAvailableArea());

    myPanel.addComponentListener(new ComponentAdapter() {
      @DirtyUI
      @Override
      public void componentResized(@NotNull ComponentEvent e) {
        myMarkupModel.recalcEditorDimensions();
        myMarkupModel.repaint();
        if (!isRightAligned()) return;
        updateCaretCursor();
        myCaretCursor.repaint();
      }
    });
  }

  private boolean mayShowToolbar() {
    return !isOneLineMode() && !DiffUtil.isDiffEditor(this) && isFileEditor();
  }

  private boolean isFileEditor() {
    FileDocumentManager documentManager = FileDocumentManager.getInstance();
    VirtualFile virtualFile = documentManager.getFile(myDocument);
    return virtualFile != null && virtualFile.isValid();
  }

  @Override
  public void setFontSize(int fontSize) {
    ReadAction.run(() -> setFontSizeImpl(fontSize, null, false));
  }

  @Override
  public void setFontSize(float fontSize) {
    ReadAction.run(() -> setFontSizeImpl(fontSize, null, false));
  }

  @ApiStatus.Internal
  public void setFontSize(float fontSize, @Nullable Point zoomCenter, boolean validateImmediately) {
    ReadAction.run(() -> setFontSizeImpl(fontSize, zoomCenter, validateImmediately));
  }

  /**
   * Changes editor font size, attempting to keep a given point unmoved. If point is not given, the top left screen corner is assumed.
   *
   * @param fontSize            new font size
   * @param zoomCenter          zoom point, relative to viewport
   * @param validateImmediately immediately update the editor's size (slower, but more accurate when the point is close to the bottom edge)
   */
  private void setFontSizeImpl(float fontSize, @Nullable Point zoomCenter, boolean validateImmediately) {
    int oldFontSize = myScheme.getEditorFontSize();
    float oldFontSize2D = myScheme.getEditorFontSize2D();

    Rectangle visibleArea = myScrollingModel.getVisibleArea();
    Point zoomCenterRelative = zoomCenter == null ? new Point() : zoomCenter;
    Point zoomCenterAbsolute = new Point(visibleArea.x + zoomCenterRelative.x, visibleArea.y + zoomCenterRelative.y);
    LogicalPosition zoomCenterLogical = xyToLogicalPosition(zoomCenterAbsolute);
    int oldLineHeight = getLineHeight();
    int intraLineOffset = zoomCenterAbsolute.y % oldLineHeight;

    myScheme.setEditorFontSize(fontSize);
    int newFontSize = myScheme.getEditorFontSize();
    float newFontSize2D = myScheme.getEditorFontSize2D(); // the resulting font size might be different due to applied min/max limits
    myPropertyChangeSupport.firePropertyChange(PROP_FONT_SIZE, oldFontSize, newFontSize);
    myPropertyChangeSupport.firePropertyChange(PROP_FONT_SIZE_2D, oldFontSize2D, newFontSize2D);
    if (validateImmediately) {
      // This is a slow operation and may cause performance issues when zooming large files using the mouse wheel.
      // On the other hand, the terminal needs this option because it uses the bottom-left corner as the zoom point,
      // and it doesn't work properly when the font size is increased, because we need to recompute the size immediately
      // to be able to scroll down that much.
      validateSize();
    }
    else {
      // Update vertical scroll bar bounds if necessary (we had a problem that use increased editor font size, and it was not possible
      // to scroll to the bottom of the document).
      myScrollPane.getViewport().invalidate();
    }

    Point shiftedZoomCenterAbsolute = logicalPositionToXY(zoomCenterLogical);
    myScrollingModel.disableAnimation();
    try {
      int targetX = visibleArea.x == 0 ? 0 : shiftedZoomCenterAbsolute.x - zoomCenterRelative.x; // stick to the left border if it's visible
      int targetY = shiftedZoomCenterAbsolute.y - zoomCenterRelative.y +
                    (intraLineOffset * getLineHeight() + oldLineHeight / 2) / oldLineHeight;
      myScrollingModel.scroll(targetX, targetY);
    }
    finally {
      myScrollingModel.enableAnimation();
    }
  }

  public int getFontSize() {
    return myScheme.getEditorFontSize();
  }

  public float getFontSize2D() {
    return myScheme.getEditorFontSize2D();
  }

  public @NotNull ActionCallback type(@NotNull String text) {
    ActionCallback result = new ActionCallback();

    for (int i = 0; i < text.length(); i++) {
      myLastTypedActionTimestamp = System.currentTimeMillis();
      char c = text.charAt(i);
      myLastTypedAction = Character.toString(c);
      if (!processKeyTyped(c)) {
        result.setRejected();
        return result;
      }
    }

    result.setDone();

    return result;
  }

  private boolean processKeyTyped(char c) {
    if (ProgressManager.getInstance().hasModalProgressIndicator()) {
      return false;
    }
    // - The frontend cannot synchronously check file validity;
    // - typing on the backend is not performed.
    if (LocalEditorSupportUtil.isMonolith()) {
      FileDocumentManager manager = FileDocumentManager.getInstance();
      VirtualFile file = manager.getFile(myDocument);
      if (file != null && !file.isValid()) {
        return false;
      }
    }

    DataContext context = getDataContext();

    Graphics graphics = GraphicsUtil.safelyGetGraphics(myEditorComponent);
    if (graphics != null) {
      PaintUtil.alignTxToInt((Graphics2D)graphics, PaintUtil.insets2offset(getInsets()), true, false, RoundingMode.FLOOR);
      processKeyTypedImmediately(c, graphics, context);
      graphics.dispose();
    }
    else {
      // the editor component is not showing
    }

    ActionManagerEx.getInstanceEx().fireBeforeEditorTyping(c, context);
    EditorUIUtil.hideCursorInEditor(this);
    processKeyTypedNormally(c, context);
    ActionManagerEx.getInstanceEx().fireAfterEditorTyping(c, context);

    return true;
  }

  @ApiStatus.Internal
  @VisibleForTesting
  public void processKeyTypedImmediately(char c, @NotNull Graphics graphics, @NotNull DataContext dataContext) {
    EditorActionPlan plan = new EditorActionPlan(this);
    EditorActionManager.getInstance();
    TypedAction.getInstance().beforeActionPerformed(this, c, dataContext, plan);
    if (myImmediatePainter.paint(graphics, plan)) {
      measureTypingLatency();
    }
  }

  @VisibleForTesting
  @ApiStatus.Internal
  public void processKeyTypedNormally(char c, @NotNull DataContext dataContext) {
    EditorActionManager.getInstance();
    TypedAction.getInstance().actionPerformed(this, c, dataContext);
  }

  private void fireFocusLost(@NotNull FocusEvent event) {
    for (FocusChangeListener listener : myFocusListeners) {
      listener.focusLost(this, event);
    }
  }

  private void fireFocusGained(@NotNull FocusEvent event) {
    for (FocusChangeListener listener : myFocusListeners) {
      listener.focusGained(this, event);
    }
  }

  @Override
  public void setHighlighter(@NotNull EditorHighlighter highlighter) {
    if (isReleased) {
      // do not set highlighter to the released editor
      return;
    }

    assertIsDispatchThread();
    WriteIntentReadAction.run((Runnable)() -> {
      Document document = getDocument();
      Disposer.dispose(myHighlighterDisposable);

      myHighlighterDisposable = Disposer.newDisposable();
      document.addDocumentListener(highlighter, myHighlighterDisposable);
      Disposer.register(myDisposable, myHighlighterDisposable);
      highlighter.setEditor(this);

      try (AccessToken ignored = SlowOperations.knownIssue("IJPL-162348")) {
        highlighter.setText(document.getImmutableCharSequence());
      }
      if (!(highlighter instanceof EmptyEditorHighlighter)) {
        EditorHighlighterCache.rememberEditorHighlighterForCachesOptimization(document, highlighter);
      }

      EditorHighlighter oldHighlighter = myHighlighter;
      myHighlighter = highlighter;
      myPropertyChangeSupport.firePropertyChange(PROP_HIGHLIGHTER, oldHighlighter, highlighter);

      if (myPanel != null) {
        reinitSettings();
      }
    });
  }

  @Override
  public @NotNull EditorHighlighter getHighlighter() {
    return myHighlighter;
  }

  @Override
  public @NotNull EditorComponentImpl getContentComponent() {
    return myEditorComponent;
  }

  @Override
  public @NotNull EditorGutterComponentEx getGutterComponentEx() {
    return myGutterComponent;
  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
    myPropertyChangeSupport.addPropertyChangeListener(listener);
  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener, @NotNull Disposable parentDisposable) {
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
    ReadAction.run(() -> myState.setInsertMode(mode));
  }

  private void isInsertModeChanged(ObservableStateListener.PropertyChangeEvent event) {
    assertIsDispatchThread();

    Object oldValue = extractOldValueOrLog(event, false);
    myPropertyChangeSupport.firePropertyChange(PROP_INSERT_MODE, oldValue, event.getNewValue());

    myCaretCursor.repaint();
  }

  @Override
  public boolean isInsertMode() {
    return myState.isInsertMode();
  }

  @Override
  public void setColumnMode(boolean mode) {
    assertIsDispatchThread();
    ReadAction.run(() -> myState.setColumnMode(mode));
  }

  private void isColumnModeChanged(ObservableStateListener.PropertyChangeEvent event) {
    assertIsDispatchThread();

    Object oldValue = extractOldValueOrLog(event, false);
    myPropertyChangeSupport.firePropertyChange(PROP_COLUMN_MODE, oldValue, event.getNewValue());
  }

  @Override
  public boolean isColumnMode() {
    return myState.isColumnMode();
  }

  @Override
  public int yToVisualLine(int y) {
    return myView.yToVisualLine(y);
  }

  @Override
  public @NotNull VisualPosition xyToVisualPosition(@NotNull Point p) {
    return ReadAction.compute(() -> myView.xyToVisualPosition(p));
  }

  @Override
  public @NotNull VisualPosition xyToVisualPosition(@NotNull Point2D p) {
    return ReadAction.compute(() -> myView.xyToVisualPosition(p));
  }

  @Override
  public @NotNull Point2D offsetToPoint2D(int offset, boolean leanTowardsLargerOffsets, boolean beforeSoftWrap) {
    return ReadAction.compute(() -> myView.offsetToXY(offset, leanTowardsLargerOffsets, beforeSoftWrap));
  }

  @Override
  public @NotNull Point offsetToXY(int offset, boolean leanForward, boolean beforeSoftWrap) {
    return ReadAction.compute(() -> {
      Point2D point2D = offsetToPoint2D(offset, leanForward, beforeSoftWrap);
      return new Point((int)point2D.getX(), (int)point2D.getY());
    });
  }

  @Override
  public @NotNull VisualPosition offsetToVisualPosition(int offset) {
    return offsetToVisualPosition(offset, false, false);
  }

  @Override
  public @NotNull VisualPosition offsetToVisualPosition(int offset, boolean leanForward, boolean beforeSoftWrap) {
    return EditorThreading.compute(() -> myView.offsetToVisualPosition(offset, leanForward, beforeSoftWrap));
  }

  public int offsetToVisualColumnInFoldRegion(@NotNull FoldRegion region, int offset, boolean leanTowardsLargerOffsets) {
    assertIsDispatchThread();
    return EditorThreading.compute(() -> myView.offsetToVisualColumnInFoldRegion(region, offset, leanTowardsLargerOffsets));
  }

  public int visualColumnToOffsetInFoldRegion(@NotNull FoldRegion region, int visualColumn, boolean leansRight) {
    assertIsDispatchThread();
    return EditorThreading.compute(() -> myView.visualColumnToOffsetInFoldRegion(region, visualColumn, leansRight));
  }

  @Override
  public @NotNull LogicalPosition offsetToLogicalPosition(int offset) {
    return EditorThreading.compute(() -> myView.offsetToLogicalPosition(offset));
  }

  @TestOnly
  public void setCaretActive() {
    synchronized (caretRepaintService) {
      caretRepaintService.setEditor(this);
      caretRepaintService.restart();
    }
  }

  // optimization: do not do column calculations here since we are interested in line number only
  public int offsetToVisualLine(int offset) {
    return offsetToVisualLine(offset, false);
  }

  @Override
  public int offsetToVisualLine(int offset, boolean beforeSoftWrap) {
    return myView.offsetToVisualLine(offset, beforeSoftWrap);
  }

  public int visualLineStartOffset(int visualLine) {
    return myView.visualLineToOffset(visualLine);
  }

  @Override
  public @NotNull LogicalPosition xyToLogicalPosition(@NotNull Point p) {
    return ReadAction.compute(() -> myView.xyToLogicalPosition(p));
  }

  private int logicalToVisualLine(int logicalLine) {
    return logicalLine < getEditorModel().getDocument().getLineCount()
           ? offsetToVisualLine(getEditorModel().getDocument().getLineStartOffset(logicalLine))
           : myAdView == null
             ? logicalToVisualPosition(new LogicalPosition(logicalLine, 0)).line
             : myAdView.logicalToVisualPosition(new LogicalPosition(logicalLine, 0), false).line;
  }

  int logicalLineToY(int line) {
    int visualLine = logicalToVisualLine(line);
    return myAdView == null
            ? visualLineToY(visualLine)
            : myAdView.visualLineToY(visualLine);
  }

  @Override
  public @NotNull Point logicalPositionToXY(@NotNull LogicalPosition pos) {
    return EditorThreading.compute(() -> {
      VisualPosition visible = logicalToVisualPosition(pos);
      return visualPositionToXY(visible);
    });
  }

  @Override
  public @NotNull Point visualPositionToXY(@NotNull VisualPosition visible) {
    return EditorThreading.compute(() -> {
      Point2D point2D = myView.visualPositionToXY(visible);
      return new Point((int)point2D.getX(), (int)point2D.getY());
    });
  }

  @Override
  public @NotNull Point2D visualPositionToPoint2D(@NotNull VisualPosition visible) {
    return EditorThreading.compute(() -> myView.visualPositionToXY(visible));
  }

  /**
   * Returns how much current line height bigger than the normal (16px)
   * This method is used to scale editors elements such as gutter icons, folding elements, and others
   */
  public float getScale() {
    if (Registry.is("editor.scale.gutter.icons")) {
      float newUINormLineHeight = (float)Registry.doubleValue("ide.new.ui.editor.normalized.line.height");
      float standardNormalizedLineHeight = ExperimentalUI.isNewUI() ? newUINormLineHeight : 16.0f;
      float normLineHeight = getLineHeight() / myScheme.getLineSpacing(); // normalized, as for 1.0f line spacing
      return normLineHeight / JBUIScale.scale(standardNormalizedLineHeight);
    }
    return 1.0f;
  }

  public int findNearestDirectionBoundary(int offset, boolean lookForward) {
    return EditorThreading.compute(() -> myView.findNearestDirectionBoundary(offset, lookForward));
  }

  @Override
  public int visualLineToY(int line) {
    return EditorThreading.compute(() -> myView.visualLineToY(line));
  }

  @Override
  public int @NotNull [] visualLineToYRange(int visualLine) {
    return myView.visualLineToYRange(visualLine);
  }

  @Override
  public void repaint(int startOffset, int endOffset) {
    repaint(startOffset, endOffset, true);
    myHighlighterListeners.forEach(listener -> listener.highlighterChanged(startOffset, endOffset));
  }

  public void addHighlighterListener(@NotNull HighlighterListener listener, @NotNull Disposable parentDisposable) {
    ContainerUtil.add(listener, myHighlighterListeners, parentDisposable);
  }

  void repaint(int startOffset, int endOffset, boolean invalidateTextLayout) {
    if (myDocument.isInBulkUpdate() || myInlayModel.isInBatchMode()) {
      return;
    }
    if (myView == null) {
      // repaint may be called from EditorImpl.<init> IDEA-341841
      return;
    }
    assertIsDispatchThread();
    EditorThreading.run(() -> {
      int minEndOffset = Math.min(endOffset, getEditorModel().getDocument().getTextLength());

      if (invalidateTextLayout) {
        myView.invalidateRange(startOffset, minEndOffset, true);
        if (myAdView != null) myAdView.invalidateRange(startOffset, minEndOffset, true);
      }

      if (!isShowing()) {
        return;
      }

      if (myDocumentChangeInProgress) {
        // at this point soft wrap model might be in an invalid state, so the following calculations cannot be performed correctly
        if (startOffset < myRangeToRepaintStart) myRangeToRepaintStart = startOffset;
        if (minEndOffset > myRangeToRepaintEnd) myRangeToRepaintEnd = minEndOffset;
        return;
      }

      // We do repaint in case of equal offsets. There is a possible case that there is a soft wrap at the same offset,
      // and it does occupy a particular amount of visual space that may be necessary to repaint.
      if (startOffset <= minEndOffset) {
        int startLine; int endLine;
        if (myAdView != null) {
          startLine = myAdView.offsetToVisualLine(startOffset, false);
          endLine = myAdView.offsetToVisualLine(minEndOffset, true);
        }
        else {
          startLine = myView.offsetToVisualLine(startOffset, false);
          endLine = myView.offsetToVisualLine(minEndOffset, true);
        }
        doRepaint(startLine, endLine);
      }
    });
  }

  private boolean isShowing() {
    return myGutterComponent.isShowing();
  }

  private void repaintToScreenBottom(int startLine) {
    int yStartLine = logicalLineToY(startLine);
    repaintToScreenBottomStartingFrom(yStartLine);
  }

  private void repaintToScreenBottomStartingFrom(int y) {
    Rectangle visibleArea = getScrollingModel().getVisibleArea();
    int yEndLine = visibleArea.y + visibleArea.height;

    myEditorComponent.repaintEditorComponent(visibleArea.x, y, visibleArea.x + visibleArea.width, yEndLine - y);
    myGutterComponent.repaint(0, y, myGutterComponent.getWidth(), yEndLine - y);
    myMarkupModel.repaint();
  }

  /**
   * Asks to repaint all logical lines from the given {@code [start; end]} range.
   *
   * @param startLine start logical line to repaint (inclusive)
   * @param endLine   end logical line to repaint (inclusive)
   */
  void repaintLines(int startLine, int endLine) {
    if (!isShowing()) return;

    int startVisualLine = logicalToVisualLine(startLine);
    DocumentEx document = getEditorModel().getDocument();
    int endVisualLine = document.getTextLength() <= 0
                        ? 0
                        : offsetToVisualLine(document.getLineEndOffset(Math.min(document.getLineCount() - 1, endLine)));
    doRepaint(startVisualLine, endVisualLine);
  }

  /**
   * Repaints visual lines in provided range (inclusive on both ends)
   */
  private void doRepaint(int startVisualLine, int endVisualLine) {
    Rectangle visibleArea = getScrollingModel().getVisibleArea();
    int yStart; int height;
    if (myAdView != null) {
      yStart = myAdView.visualLineToY(startVisualLine);
      height = myAdView.visualLineToYRange(endVisualLine)[1] + 2 - yStart;
    }
    else {
      yStart = visualLineToY(startVisualLine);
      height = visualLineToYRange(endVisualLine)[1] + 2 - yStart;
    }
    myEditorComponent.repaintEditorComponent(visibleArea.x, yStart, visibleArea.x + visibleArea.width, height);
    myGutterComponent.repaint(0, yStart, myGutterComponent.getWidth(), height);
    if (myStickyLinesManager != null) {
      myStickyLinesManager.repaintLines(startVisualLine, endVisualLine);
    }
  }

  private void bulkUpdateStarted() {
    if (myInlayModel.isInBatchMode()) LOG.error("Document bulk mode shouldn't be started from batch inlay operation");

    myView.getPreferredSize(); // make sure size is calculated (in case it will be required while bulk mode is active)
    if (myAdView != null) myAdView.getPreferredSize();

    myScrollingModel.onBulkDocumentUpdateStarted();

    if (myScrollingPositionKeeper != null) myScrollingPositionKeeper.savePosition();

    myCaretModel.onBulkDocumentUpdateStarted();
    mySoftWrapModel.onBulkDocumentUpdateStarted();
    myFoldingModel.onBulkDocumentUpdateStarted();
  }

  private void bulkUpdateFinished() {
    if (myInlayModel.isInBatchMode()) LOG.error("Document bulk mode shouldn't be finished from batch inlay operation");

    myFoldingModel.onBulkDocumentUpdateFinished();
    mySoftWrapModel.onBulkDocumentUpdateFinished();
    myView.reset();
    myCaretModel.onBulkDocumentUpdateFinished();

    setMouseSelectionState(MOUSE_SELECTION_STATE_NONE);

    validateSize();

    updateGutterSize();
    repaintToScreenBottom(0);
    updateCaretCursor();

    if (!Boolean.TRUE.equals(getUserData(DISABLE_CARET_POSITION_KEEPING)) && myScrollingPositionKeeper != null) {
      myScrollingPositionKeeper.restorePosition(true);
    }
  }

  private void beforeChangedUpdate(@NotNull DocumentEvent e) {
    ThreadingAssertions.assertEventDispatchThread();

    myDocumentChangeInProgress = true;
    if (isStickySelection()) {
      setStickySelection(false);
    }
    if (myDocument.isInBulkUpdate()) {
      // Assuming that the job is done at bulk listener callback methods.
      return;
    }

    myRangeToRepaintStart = Integer.MAX_VALUE;
    myRangeToRepaintEnd = 0;
    myRestoreScrollingPosition = getCaretModel().getOffset() < e.getOffset() ||
                                 getCaretModel().getOffset() > e.getOffset() + e.getOldLength();
    if (myRestoreScrollingPosition && myScrollingPositionKeeper != null) {
      myScrollingPositionKeeper.savePosition();
    }
  }

  void invokeDelayedErrorStripeRepaint() {
    if (myErrorStripeNeedsRepaint) {
      myMarkupModel.repaint();
      myErrorStripeNeedsRepaint = false;
    }
  }

  private void changedUpdate(@NotNull DocumentEvent e) {
    myDocumentChangeInProgress = false;
    if (myDocument.isInBulkUpdate()) return;

    if (myErrorStripeNeedsRepaint) {
      myMarkupModel.repaint(e.getOffset(), e.getOffset() + e.getNewLength());
      myErrorStripeNeedsRepaint = false;
    }

    setMouseSelectionState(MOUSE_SELECTION_STATE_NONE);

    if (myGutterComponent.getCurrentAccessibleLine() != null) {
      escapeGutterAccessibleLine(e.getOffset(), e.getOffset() + e.getNewLength());
    }

    validateSize();

    int startLine = offsetToLogicalLine(e.getOffset());
    int endLine = offsetToLogicalLine(e.getOffset() + e.getNewLength());

    if (startLine != endLine || StringUtil.indexOf(e.getOldFragment(), '\n') != -1) {
      myGutterComponent.clearLineToGutterRenderersCache();
    }

    if (myRangeToRepaintStart < myDocument.getLineStartOffset(startLine)) {
      startLine = myDocument.getLineNumber(myRangeToRepaintStart);
    }
    if (myRangeToRepaintEnd > myDocument.getLineEndOffset(endLine)) {
      endLine = myDocument.getLineNumber(Math.min(myRangeToRepaintEnd, myDocument.getTextLength()));
    }
    if (countLineFeeds(e.getOldFragment()) != countLineFeeds(e.getNewFragment())) {
      // Lines removed. Need to repaint till the end of the screen
      repaintToScreenBottom(startLine);
    }
    else {
      repaintLines(startLine, endLine);
    }

    updateCaretCursor();

    if (myRestoreScrollingPosition &&
        !Boolean.TRUE.equals(getUserData(DISABLE_CARET_POSITION_KEEPING)) &&
        myScrollingPositionKeeper != null) {
      myScrollingPositionKeeper.restorePosition(true);
    }
  }

  private void escapeGutterAccessibleLine(int offsetStart, int offsetEnd) {
    int startVisLine = offsetToVisualLine(offsetStart);
    int endVisLine = offsetToVisualLine(offsetEnd);
    int line = getCaretModel().getPrimaryCaret().getVisualPosition().line;
    if (startVisLine <= line && endVisLine >= line) {
      myGutterComponent.escapeCurrentAccessibleLine();
    }
  }

  public void hideCursor() {
    if (!myState.isViewer() && EMPTY_CURSOR != null && Registry.is("ide.hide.cursor.when.typing")) {
      myDefaultCursor = EMPTY_CURSOR;
      updateEditorCursor();
    }
  }

  public boolean isCursorHidden() {
    return myDefaultCursor == EMPTY_CURSOR;
  }

  public boolean isScrollToCaret() {
    return myState.isScrollToCaret();
  }

  public void setScrollToCaret(boolean scrollToCaret) {
    myState.setScrollToCaret(scrollToCaret);
  }

  public @NotNull Disposable getDisposable() {
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
      }, ModalityState.any(), __ -> isDisposed());
    }
  }

  void validateSize() {
    if (isReleased) return;

    Dimension dim = getPreferredSize();

    if (!dim.equals(myPreferredSize) && !myDocument.isInBulkUpdate() && !myInlayModel.isInBatchMode()) {
      dim = mySizeAdjustmentStrategy.adjust(dim, myPreferredSize, this);
      if (!dim.equals(myPreferredSize)) {
        myPreferredSize = dim;

        updateGutterSize();

        myEditorComponent.setSize(dim);
        myEditorComponent.fireResized();

        myMarkupModel.recalcEditorDimensions();
        myMarkupModel.repaint();
      }
    }
  }

  void recalculateSizeAndRepaint() {
    validateSize();
    myEditorComponent.repaint();
  }

  @Override
  public @NotNull DocumentEx getDocument() {
    return myDocument;
  }

  @Override
  public @NotNull JComponent getComponent() {
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

  /**
   * Sets a callback invoked after every repaint request.
   * <p>
   *   The callback is only invoked if the component is showing and the request has a non-empty rectangle.
   *   In other words, it's invoked when the repaint request is likely to be followed by an actual painting operation.
   * </p>
   * <p>
   *   The callback must be fast, non-intrusive, and should not throw any exceptions.
   * </p>
   * <p>
   *   This is an internal hack, which is why it doesn't follow the usual add-listener pattern.
   * </p>
   * @param callback the callback, {@code null} removes the callback
   */
  @ApiStatus.Internal
  public void setRepaintCallback(@Nullable Runnable callback) {
    myEditorComponent.setRepaintCallback(callback);
  }

  /**
   * Sets a callback invoked after every paint operation.
   * <p>
   *   The callback must be fast, non-intrusive, and should not throw any exceptions.
   * </p>
   * <p>
   *   This is an internal hack, which is why it doesn't follow the usual add-listener pattern.
   * </p>
   * @param callback the callback, {@code null} removes the callback
   */
  @ApiStatus.Internal
  public void setPaintCallback(@Nullable Runnable callback) {
    myView.setPaintCallback(callback);
  }

  @Override
  public boolean isStickySelection() {
    return myState.isStickySelection();
  }

  @Override
  public void setStickySelection(boolean enable) {
    myState.setStickySelection(enable);
  }

  private void isStickySelectionChanged(ObservableStateListener.PropertyChangeEvent event) {
    Object newValue = event.getNewValue();
    if (!(newValue instanceof Boolean)) {
      LOG.error("newValue is not Boolean. property name = " + event.getPropertyName() + ", newValue = " + newValue);
      return;
    }

    if ((boolean)newValue) {
      myStickySelectionStart = getCaretModel().getOffset();
    }
    else {
      mySelectionModel.removeSelection();
    }
  }

  public void setHorizontalTextAlignment(@MagicConstant(intValues = {TEXT_ALIGNMENT_LEFT, TEXT_ALIGNMENT_RIGHT}) int alignment) {
    myState.setHorizontalTextAlignment(alignment);
  }

  public boolean isRightAligned() {
    return myState.getHorizontalTextAlignment() == TEXT_ALIGNMENT_RIGHT;
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
    myEditorComponent.repaint();
  }

  /**
   * {@link #stopDumbLater} or {@link #stopDumb} must be performed in finally
   */
  public void startDumb() {
    if (ApplicationManager.getApplication().isHeadlessEnvironment() || !myEditorComponent.isShowing()) return;
    if (!Registry.is("editor.dumb.mode.available")) return;
    putUserData(BUFFER, null);
    Rectangle rect = ((JViewport)myEditorComponent.getParent()).getViewRect();
    if (rect.isEmpty()) return;
    if (myStickyLinesManager != null) {
      myStickyLinesManager.startDumb();
    }
    // The LCD text loop is enabled only for opaque images
    BufferedImage image = UIUtil.createImage(myEditorComponent, rect.width, rect.height, BufferedImage.TYPE_INT_RGB);
    Graphics imageGraphics = image.createGraphics();
    imageGraphics.translate(-rect.x, -rect.y);
    Graphics2D graphics = JBSwingUtilities.runGlobalCGTransform(myEditorComponent, imageGraphics);
    graphics.setClip(rect.x, rect.y, rect.width, rect.height);
    myEditorComponent.paintComponent(graphics);
    graphics.dispose();
    putUserData(BUFFER, image);
  }

  public boolean isDumb() {
    return getUserData(BUFFER) != null;
  }

  @ApiStatus.Internal
  public void suppressPainting(boolean suppress) {
    mySuppressPainting = suppress;
  }

  @ApiStatus.Internal
  public void suppressDisposedPainting(boolean suppress) {
    mySuppressDisposedPainting = suppress;
  }

  private boolean shouldPaint() {
    return !isReleased && !mySuppressPainting;
  }

  private void fillPlaceholder(@NotNull Graphics2D g) {
    Rectangle clip = g.getClipBounds();
    if (clip == null) {
      return;
    }

    Color bg = isReleased ? getDisposedBackground() : getBackgroundColor();
    g.setColor(bg);
    g.fillRect(clip.x, clip.y, clip.width, clip.height);
  }

  void paint(@NotNull Graphics2D g) {
    if (g.getClipBounds() == null) {
      return;
    }

    BufferedImage buffer = Registry.is("editor.dumb.mode.available", true) ? getUserData(BUFFER) : null;
    if (buffer != null) {
      Rectangle rect = getContentComponent().getVisibleRect();
      StartupUiUtil.drawImage(g, buffer, null, rect.x, rect.y);
      return;
    }

    if (!shouldPaint()) {
      fillPlaceholder(g);
      return;
    }

    EditorThreading.run(() -> {
      if (myUpdateCursor && !myPurePaintingMode) {
        setCursorPosition();
        myUpdateCursor = false;
      }

      if (myProject != null && myProject.isDisposed()) {
        return;
      }

      if (myAdView != null) {
        myAdView.paint(g);
      }
      else {
        myView.paint(g);
      }
    });

    boolean isBackgroundImageSet = IdeBackgroundUtil.isEditorBackgroundImageSet(myProject);
    if (myBackgroundImageSet != isBackgroundImageSet) {
      myBackgroundImageSet = isBackgroundImageSet;
      updateOpaque(myScrollPane.getHorizontalScrollBar());
      updateOpaque(myScrollPane.getVerticalScrollBar());
    }
  }

  @NotNull Color getDisposedBackground() {
    if (mySuppressDisposedPainting || mySuppressPainting) return getBackgroundColor();
    return new JBColor(new Color(128, 255, 128), new Color(128, 255, 128));
  }

  @Override
  public @NotNull IndentsModel getIndentsModel() {
    return myIndentsModel;
  }

  @Override
  public void setHeaderComponent(@Nullable JComponent header) {
    // ReadAction?
    JComponent oldComponent = getHeaderComponent();
    myHeaderPanel.removeAll();
    JComponent permanentHeader = getPermanentHeaderComponent();
    if (header == null) {
      header = permanentHeader;
    }
    else if (permanentHeader != null && header != permanentHeader) {
      JPanel headerPanel = new JPanel(new BorderLayout());
      headerPanel.add(permanentHeader, BorderLayout.NORTH);
      headerPanel.add(header, BorderLayout.SOUTH);
      header = headerPanel;
    }
    if (header != null) {
      myHeaderPanel.add(header);
      myPropertyChangeSupport.firePropertyChange(PROP_HEADER_COMPONENT, oldComponent, header);
    }

    myHeaderPanel.revalidate();
    myHeaderPanel.repaint();

    if (SystemInfo.isMac) {
      TouchbarSupport.onUpdateEditorHeader(this);
    }
  }

  @Override
  public boolean hasHeaderComponent() {
    return EditorThreading.compute(() -> {
      JComponent header = getHeaderComponent();
      return header != null && header != getPermanentHeaderComponent();
    });
  }

  @Override
  public @Nullable JComponent getPermanentHeaderComponent() {
    return EditorThreading.compute(() -> getUserData(PERMANENT_HEADER));
  }

  @Override
  public void setPermanentHeaderComponent(@Nullable JComponent component) {
    putUserData(PERMANENT_HEADER, component);
  }

  @Override
  public @Nullable JComponent getHeaderComponent() {
    if (myHeaderPanel.getComponentCount() > 0) {
      return (JComponent)myHeaderPanel.getComponent(0);
    }
    return null;
  }

  @Override
  public void setBackgroundColor(Color color) {
    if (getBackgroundIgnoreForced().equals(color)) {
      myState.setMyForcedBackground(null);
    }
    else {
      myState.setMyForcedBackground(color);
    }
  }

  @NotNull
  Color getForegroundColor() {
    return myScheme.getDefaultForeground();
  }

  @Override
  public @NotNull Color getBackgroundColor() {
    return EditorThreading.compute(() -> {
      Color forcedBackground = myState.getMyForcedBackground();
      return forcedBackground == null ? getBackgroundIgnoreForced() : forcedBackground;
    });
  }

  @Override
  public @NotNull TextDrawingCallback getTextDrawingCallback() {
    return myTextDrawingCallback;
  }

  @Override
  public void setPlaceholder(@Nullable CharSequence text) {
    myState.setMyPlaceholderText(text);
  }

  @Override
  public void setPlaceholderAttributes(@Nullable TextAttributes attributes) {
    myState.setMyPlaceholderAttributes(attributes);
  }

  public @Nullable TextAttributes getPlaceholderAttributes() {
    return myState.getMyPlaceholderAttributes();
  }

  public CharSequence getPlaceholder() {
    return myState.getMyPlaceholderText();
  }

  @Override
  public void setShowPlaceholderWhenFocused(boolean show) {
    myState.setMyShowPlaceholderWhenFocused(show);
  }

  public boolean getShowPlaceholderWhenFocused() {
    return myState.getMyShowPlaceholderWhenFocused();
  }

  Color getBackgroundColor(@NotNull TextAttributes attributes) {
    Color attrColor = attributes.getBackgroundColor();
    return Comparing.equal(attrColor, myScheme.getDefaultBackground()) ? getBackgroundColor() : attrColor;
  }

  private @NotNull Color getBackgroundIgnoreForced() {
    Color color = myScheme.getDefaultBackground();
    if (myDocument.isWritable()) {
      return color;
    }
    Color readOnlyColor = myScheme.getColor(EditorColors.READONLY_BACKGROUND_COLOR);
    return readOnlyColor != null ? readOnlyColor : color;
  }

  public @Nullable TextRange getComposedTextRange() {
    MyInputMethodHandler handler = getMyInputMethodHandler();
    return handler != null ? handler.getRange() : null;
  }

  private boolean composedTextExists() {
    MyInputMethodHandler handler = getMyInputMethodHandler();
    return handler != null && handler.isComposedTextShown();
  }

  private @Nullable MyInputMethodHandler getMyInputMethodHandler() {
    EditorInputMethodSupport support = myInputMethodSupport;
    return support != null && support.getRequests() instanceof MyInputMethodHandler handler ? handler : null;
  }

  @Override
  public int getMaxWidthInRange(int startOffset, int endOffset) {
    return EditorThreading.compute(() -> myView.getMaxWidthInRange(startOffset, endOffset));
  }

  public boolean isPaintSelection() {
    return myState.isPaintSelection() || !isOneLineMode() || IJSwingUtilities.hasFocus(getContentComponent());
  }

  public void setPaintSelection(boolean paintSelection) {
    myState.setPaintSelection(paintSelection);
  }

  @Override
  public @NotNull @NonNls String dumpState() {
    return "allow caret inside tab: " + mySettings.isCaretInsideTabs()
           + ", allow caret after line end: " + mySettings.isVirtualSpace()
           + ", soft wraps: " + (mySoftWrapModel.isSoftWrappingEnabled() ? "on" : "off")
           + ", caret model: " + getCaretModel().dumpState()
           + ", soft wraps data: " + getSoftWrapModel().dumpState()
           + "\n\nfolding data: " + getFoldingModel().dumpState()
           + "\ninlay model: " + getInlayModel().dumpState()
           + (myDocument instanceof DocumentImpl ? "\n\ndocument info: " + ((DocumentImpl)myDocument).dumpState() : "")
           + "\nfont preferences: " + myScheme.getFontPreferences()
           + "\npure painting mode: " + myPurePaintingMode
           + "\ninsets: " + myEditorComponent.getInsets()
           + (myView == null ? "" : "\nview: " + myView.dumpState());
  }

  public CaretRectangle @Nullable [] getCaretLocations(boolean onlyIfShown) {
    return myCaretCursor.getCaretLocations(onlyIfShown);
  }

  @Override
  public int getAscent() {
    return myView.getAscent();
  }

  @Override
  public int getLineHeight() {
    return EditorThreading.compute(() -> myView.getLineHeight());
  }

  public int getDescent() {
    return myView.getDescent();
  }

  public int getCharHeight() {
    return myView.getCharHeight();
  }

  public @NotNull FontMetrics getFontMetrics(@JdkConstants.FontStyle int fontType) {
    EditorFontType ft;
    if (fontType == Font.PLAIN) {
      ft = EditorFontType.PLAIN;
    }
    else if (fontType == Font.BOLD) {
      ft = EditorFontType.BOLD;
    }
    else if (fontType == Font.ITALIC) {
      ft = EditorFontType.ITALIC;
    }
    else if (fontType == (Font.BOLD | Font.ITALIC)) {
      ft = EditorFontType.BOLD_ITALIC;
    }
    else {
      LOG.error("Unknown font type: " + fontType);
      ft = EditorFontType.PLAIN;
    }

    return myEditorComponent.getFontMetrics(myScheme.getFont(ft));
  }

  public int getPreferredHeight() {
    return isReleased ? 0 : myView.getPreferredHeight();
  }

  public @NotNull Dimension getPreferredSize() {
    return EditorThreading.compute(() -> isReleased ? new Dimension()
                                               : Registry.is("idea.true.smooth.scrolling.dynamic.scrollbars")
                                                 ? new Dimension(getPreferredWidthOfVisibleLines(), myView.getPreferredHeight())
                                                 : myView.getPreferredSize());
  }

  /* When idea.true.smooth.scrolling=true, this method is used to compute the width of currently visible line range
     rather than the whole document width.

     As transparent scrollbars, by definition, prevent blit-acceleration of scrolling, and we really need blit-acceleration
     because not all hardware can render pixel-by-pixel scrolling with acceptable FPS without it (we now have 4K-5K displays, you know).
     To have both the hardware acceleration and the transparent scrollbars, we need to completely redesign JViewport machinery to support
     independent layers, which is (probably) possible, but it's a rather cumbersome task.

     Another approach is to make scrollbars opaque, but only in the editor (as editor is a slow-to-draw component with large screen area).
     This is what "true smooth scrolling" option currently does. Interestingly, making the vertical scrollbar opaque might actually be
     a good thing. On modern displays (size, aspect ratio) code rarely extends beyond the right screen edge, and even
     when it does, its coupling with the navigation bar only reduces intelligibility of both the navigation bar and the code itself.

     Horizontal scrollbar is another story - a single long line of text forces horizontal scrollbar in the whole document,
     and in that case "transparent" scrollbar has some merits. However, instead of using transparency, we can hide horizontal
     scrollbar altogether when it's not needed for currently visible content. In a sense, this approach is superior,
     as even "transparent" scrollbar is only semi-transparent (thus we may prefer "on-demand" scrollbar in the general case).

     Hiding the horizontal scrollbar also solves another issue - when both scrollbars are visible, vertical scrolling with
     a high-precision touchpad can result in unintentional horizontal shifts (because of the touchpad sensitivity).
     When visible content fully fits horizontally (i.e., in most cases), hiding the unneeded scrollbar
     reliably prevents the horizontal "jitter."

     Keep in mind that this functionality is experimental and may need more polishing.

     In principle, we can apply this method to other components by defining, for example,
     VariableWidth interface and supporting it in JBScrollPane. */
  private int getPreferredWidthOfVisibleLines() {
    Rectangle area = getScrollingModel().getVisibleArea();
    VisualPosition begin = xyToVisualPosition(area.getLocation());
    VisualPosition end = xyToVisualPosition(new Point(area.x + area.width, area.y + area.height));
    return Math.max(myView.getPreferredWidth(begin.line, end.line), getScrollingWidth());
  }

  /* Returns the width of the current horizontal scrolling state.
     Complements the getPreferredWidthOfVisibleLines() method to allows retaining horizontal
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

  @Override
  public @NotNull Dimension getContentSize() {
    return EditorThreading.compute(() -> isReleased ? new Dimension() : myView.getPreferredSize());
  }

  @Override
  public @NotNull JScrollPane getScrollPane() {
    return myScrollPane;
  }

  @Override
  public void setBorder(@Nullable Border border) {
    if (border == null) border = JBUI.Borders.empty();
    myState.setMyBorder(border);
  }

  private void borderChanged() {
    myScrollPane.setBorder(myState.getMyBorder());
  }

  @Override
  public Insets getInsets() {
    return EditorThreading.compute(() -> myScrollPane.getInsets());
  }

  @Override
  public int logicalPositionToOffset(@NotNull LogicalPosition pos) {
    return EditorThreading.compute(() -> myView.logicalPositionToOffset(pos));
  }

  /**
   * @return the total number of lines that can be viewed by the user.
   * This is the number of all document lines
   * (considering that a single logical document line may be represented
   * as multiple visual lines because of soft wraps appliance)
   * minus the number of folded lines.
   */
  public int getVisibleLineCount() {
    return EditorThreading.compute(() -> myView.getVisibleLineCount());
  }

  @Override
  public @NotNull VisualPosition logicalToVisualPosition(@NotNull LogicalPosition logicalPos) {
    return EditorThreading.compute(() -> myView.logicalToVisualPosition(logicalPos, false));
  }

  @Override
  public @NotNull LogicalPosition visualToLogicalPosition(@NotNull VisualPosition visiblePos) {
    return EditorThreading.compute(() -> myView.visualToLogicalPosition(visiblePos));
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

  private @NotNull VisualPosition getTargetPosition(int x, int y, boolean trimToLineWidth, @Nullable Caret targetCaret) {
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
    if (yToVisualLine(y) >= visualLineCount) {
      y = visualLineToY(Math.max(0, visualLineCount - 1));
    }
    VisualPosition visualPosition = xyToVisualPosition(new Point(x, y));
    Caret caret = targetCaret != null ? targetCaret : getCaretModel().getPrimaryCaret();
    if (EditorUtil.isBlockLikeCaret(caret) && !visualPosition.leansRight && visualPosition.column > 0) {
      // Adjustment for block caret when clicking in the second half of the character
      visualPosition = new VisualPosition(visualPosition.line, visualPosition.column - 1, true);
    }
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

    if (myInitialMouseEvent != null &&
        (e.getComponent() != myInitialMouseEvent.getComponent() || !e.getPoint().equals(myInitialMouseEvent.getPoint()))) {
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

    FoldRegion region = getFoldingModel().getFoldingPlaceholderAt(e.getPoint());
    if (region != null && region == myMouseSelectedRegion) {
      getFoldingModel().runBatchFoldingOperation(() -> region.setExpanded(true), true, false);
      validateMousePointer(e, null);
    }

    // The general idea is to check if the user performed 'caret position change click' (left click most of the time) inside selection
    // and, in the case of the positive answer, clear selection. Please note that there is a possible case that mouse click
    // is performed inside selection, but it triggers the context menu. We don't want to drop the selection then.
    if (myMousePressedEvent != null
        && myKeepSelectionOnMousePress
        && !myLastPressWasAtBlockInlay
        && !myDragStarted
        && myMousePressedEvent.getClickCount() == 1
        && !myMousePressedEvent.isShiftDown()
        && !myMousePressedEvent.isPopupTrigger()
        && !e.isPopupTrigger()
        && !isToggleCaretEvent(myMousePressedEvent)
        && !isCreateRectangularSelectionEvent(myMousePressedEvent)) {
      getSelectionModel().removeSelection();
    }
  }

  @Override
  public @NotNull DataContext getDataContext() {
    return EditorUtil.getEditorDataContext(this);
  }

  private boolean isInsideGutterWhitespaceArea(@NotNull MouseEvent e) {
    EditorMouseEventArea area = getMouseEventArea(e);
    return area == EditorMouseEventArea.FOLDING_OUTLINE_AREA &&
           myGutterComponent.convertX(e.getX()) > myGutterComponent.getWhitespaceSeparatorOffset();
  }

  @Override
  public EditorMouseEventArea getMouseEventArea(@NotNull MouseEvent e) {
    return EditorThreading.compute(() -> {
      if (myGutterComponent != e.getSource()) return EditorMouseEventArea.EDITING_AREA;

      int x = myGutterComponent.convertX(e.getX());

      return myGutterComponent.getEditorMouseAreaByOffset(x);
    });
  }

  private void requestFocus() {
    if (!myEditorComponent.hasFocus()) {
      IdeFocusManager.getInstance(myProject).requestFocus(myEditorComponent, true);
    }
  }

  private void resetMousePointer() {
    UIUtil.setCursor(myEditorComponent, Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
  }

  private void validateMousePointer(@NotNull MouseEvent e, @Nullable EditorMouseEvent editorMouseEvent) {
    if (e.getSource() == myGutterComponent) {
      myGutterComponent.validateMousePointer(e);
    }
    else {
      myGutterComponent.setActiveFoldRegions(Collections.emptyList());
      myDefaultCursor = getDefaultCursor(e, editorMouseEvent);
      updateEditorCursor();
    }
  }

  private void updateEditorCursor() {
    Cursor customCursor = getCustomCursor();
    if (customCursor == null && myCursorSetExternally && myEditorComponent.isCursorSet()) {
      Cursor cursor = myEditorComponent.getCursor();
      if (cursor != Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR) &&
          cursor != Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR) &&
          cursor != EMPTY_CURSOR) {
        // someone else has set cursor, don't touch it
        return;
      }
    }

    UIUtil.setCursor(myEditorComponent, customCursor == null ? myDefaultCursor : customCursor);
    myCursorSetExternally = false;
  }

  private @NotNull Cursor getDefaultCursor(@NotNull MouseEvent e, @Nullable EditorMouseEvent editorMouseEvent) {
    Cursor result = null;
    if (getSelectionModel().hasSelection() && (e.getModifiersEx() & (InputEvent.BUTTON1_DOWN_MASK | InputEvent.BUTTON2_DOWN_MASK)) == 0) {
      int offset = editorMouseEvent == null ? logicalPositionToOffset(xyToLogicalPosition(e.getPoint())) : editorMouseEvent.getOffset();
      if (getSelectionModel().getSelectionStart() <= offset && offset < getSelectionModel().getSelectionEnd()) {
        result = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR);
      }
    }
    if (result == null) {
      FoldRegion foldRegion = editorMouseEvent == null ? myFoldingModel.getFoldingPlaceholderAt(e.getPoint())
                                                       : editorMouseEvent.getCollapsedFoldRegion();
      if (foldRegion != null && !(foldRegion instanceof CustomFoldRegion)) {
        result = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
      }
    }
    return result == null ? Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR) : result;
  }

  private void runMouseDraggedCommand(@NotNull MouseEvent e) {
    if (myCommandProcessor == null || e.isConsumed() || myMousePressedEvent != null && myMousePressedEvent.isConsumed()) {
      return;
    }
    myCommandProcessor.executeCommand(myProject, () -> mouseDragHandler.mouseDragged(e), "", MOUSE_DRAGGED_COMMAND_GROUP,
                                      UndoConfirmationPolicy.DEFAULT, getDocument());
  }

  private void processMouseDragged(@NotNull MouseEvent e) {
    if (!SwingUtilities.isLeftMouseButton(e) && !SwingUtilities.isMiddleMouseButton(e)
        || (Registry.is("editor.disable.drag.with.right.button") && SwingUtilities.isRightMouseButton(e))) {
      return;
    }

    if (!myMouseDragStarted) {
      var point = convertPoint(e.getComponent(), e.getPoint(), myEditorComponent);
      var sensitivity = dragSensitivity();
      myMouseDragStarted = myLastMousePressedPoint == null
                           || !myLastPressedOnGutter // Small drags aren't a problem in the editor, only on the gutter.
                           || Math.abs(myLastMousePressedPoint.x - point.x) >= sensitivity
                           || Math.abs(myLastMousePressedPoint.y - point.y) >= sensitivity;
      if (!myMouseDragStarted) {
        return;
      }

      if (mySuppressedByBreakpointsLastPressPosition != null) {
        getCaretModel().removeSecondaryCarets();
        getCaretModel().moveToVisualPosition(mySuppressedByBreakpointsLastPressPosition);
        mySuppressedByBreakpointsLastPressPosition = null;
      }
    }

    EditorMouseEventArea eventArea = getMouseEventArea(e);
    if (eventArea == EditorMouseEventArea.ANNOTATIONS_AREA) return;
    if (eventArea == EditorMouseEventArea.LINE_MARKERS_AREA ||
        eventArea == EditorMouseEventArea.FOLDING_OUTLINE_AREA && !isInsideGutterWhitespaceArea(e)) {
      // The general idea is that we don't want to change caret position on gutter marker area click (e.g., on setting a breakpoint)
      // but do want to allow bulk selection on gutter marker mouse drag. However, when a drag is performed, the first event is
      // a 'mouse pressed' event, that's why we remember target line on 'mouse pressed' processing and use that information on
      // further dragging (if any).
      if (myDragOnGutterSelectionStartLine >= 0) {
        mySelectionModel.removeSelection();
        myCaretModel.moveToOffset(myDragOnGutterSelectionStartLine < myDocument.getLineCount()
                                  ? myDocument.getLineStartOffset(myDragOnGutterSelectionStartLine) : myDocument.getTextLength());
      }
      myDragOnGutterSelectionStartLine = -1;
    }

    if (eventArea == EditorMouseEventArea.LINE_NUMBERS_AREA &&
        NewUI.isEnabled() && EditorUtil.isBreakPointsOnLineNumbers() &&
        getMouseSelectionState() != MOUSE_SELECTION_STATE_LINE_SELECTED &&
        //IDEA-295653 We should not select a line if we are dragging an object. For example, a breakpoint
        !myLastPressedOnGutterIcon && !myDragSelectionStarted) {
      selectLineAtCaret(true); //IDEA-305975
      getGutterComponentEx().putClientProperty("active.line.number", null); //clear hovered breakpoint
      myDragSelectionStarted = true;
    }

    boolean columnSelectionDragEvent = isColumnSelectionDragEvent(e);
    boolean toggleCaretEvent = isToggleCaretEvent(e);
    boolean addRectangularSelectionEvent = isAddRectangularSelectionEvent(e);
    boolean columnSelectionDrag = isColumnMode() && !myLastPressCreatedCaret || columnSelectionDragEvent;
    if (!columnSelectionDragEvent && toggleCaretEvent && !myLastPressCreatedCaret) {
      return; // ignoring drag after removing a caret
    }
    if (myLastPressWasAtBlockInlay) {
      return; // ignoring drag originating over block inlay
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
      if (y > visibleArea.y + visibleArea.height && visibleArea.y + visibleArea.height < myEditorComponent.getHeight()) {
        dy = y - visibleArea.y - visibleArea.height;
      }
    }
    if (dx == 0 && dy == 0) {
      myScrollingTimer.stop();

      if (myLastPressedOnGutterIcon) {
        return;
      }

      SelectionModel selectionModel = getSelectionModel();
      Caret leadCaret = getLeadCaret();
      int oldSelectionStart = leadCaret.getLeadSelectionOffset();
      VisualPosition oldVisLeadSelectionStart = leadCaret.getLeadSelectionPosition();
      int oldCaretOffset = getCaretModel().getOffset();
      boolean multiCaretSelection = columnSelectionDrag || toggleCaretEvent;
      VisualPosition newVisualCaret = getTargetPosition(x, y, !multiCaretSelection, leadCaret);
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
        myDragSelectionStarted = true;
      }
      else {
        if (multiCaretSelection) {
          if (myLastMousePressedLocation != null && (myCurrentDragIsSubstantial || !newLogicalCaret.equals(myLastMousePressedLocation))) {
            createSelectionTill(newLogicalCaret);
            blockActionsIfNeeded(e, myLastMousePressedLocation, newLogicalCaret);
            myDragSelectionStarted = true;
          }
        }
        else {
          if (getMouseSelectionState() != MOUSE_SELECTION_STATE_NONE) {
            setupSpecialSelectionOnMouseDrag(newCaretOffset, caretShift);
            cancelAutoResetForMouseSelectionState();
            return;
          }

          if (!myKeepSelectionOnMousePress) {
            // There is a possible case that lead selection position should be adjusted in accordance with the mouse move direction.
            // E.g., consider a situation when a user selects the whole line by clicking at 'line numbers' area.
            // 'Line end' is considered to be lead selection point then.
            // However, when the mouse is dragged down, we want to consider 'line start' to be lead selection point.
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
            else if (EditorUtil.isBlockLikeCaret(getCaretModel().getPrimaryCaret()) && Registry.is("editor.block.caret.selection.vim-like")) {
              // adjust selection range, so that it covers caret location
              if (mySelectionModel.hasSelection() && oldVisLeadSelectionStart.equals(mySelectionModel.getSelectionEndPosition())) {
                oldVisLeadSelectionStart = prevSelectionVisualPosition(oldVisLeadSelectionStart);
              }
              if (newVisualCaret.after(oldVisLeadSelectionStart)) {
                newVisualCaret = nextSelectionVisualPosition(newVisualCaret);
                newCaretOffset = visualPositionToOffset(newVisualCaret);
              }
              else if (oldVisLeadSelectionStart.after(newVisualCaret) ||
                       oldVisLeadSelectionStart.equals(newVisualCaret) && mySelectionModel.hasSelection()) {
                oldVisLeadSelectionStart = nextSelectionVisualPosition(oldVisLeadSelectionStart);
              }
              oldSelectionStart = visualPositionToOffset(oldVisLeadSelectionStart);
            }
            setSelectionAndBlockActions(e, oldVisLeadSelectionStart, oldSelectionStart, newVisualCaret, newCaretOffset);
            cancelAutoResetForMouseSelectionState();
            myDragSelectionStarted = true;
          }
          else {
            if (caretShift != 0) {
              if (myMousePressedEvent != null) {
                if (mySettings.isDndEnabled()) {
                  if (!myDragStarted) {
                    if (ApplicationManager.getApplication().isUnitTestMode()) {
                      // It can lead to process hanging, and breaking drag-n-drop in other applications
                      throw new UnsupportedOperationException("Drag'n'drop operation shouldn't be started in tests");
                    }
                    myDragStarted = true;
                    boolean isCopy = UIUtil.isControlKeyDown(e) || isViewer() || !getDocument().isWritable();
                    mySavedCaretOffsetForDNDUndoHack = oldCaretOffset;
                    getContentComponent().getTransferHandler().exportAsDrag(getContentComponent(), e, isCopy ? TransferHandler.COPY
                                                                                                             : TransferHandler.MOVE);
                  }
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

  /**
   * Returns the sensitivity of the editor's drag and drop.
   * <p>
   * Mouse drags shorter than this value by both axes are interpreted as clicks, not drags.
   * </p>
   * @return the value in user-space pixels, ignoring any user scaling
   */
  @ApiStatus.Internal
  public static int dragSensitivity() {
    return Registry.intValue("editor.drag.sensitivity", 5, 0, 25);
  }

  private static @NotNull Point convertPoint(@NotNull Component from, @NotNull Point point, @NotNull Component to) {
    // Can't just use RelativePoint or SwingUtilities.convertPoint because some tests create just the editor without a root pane.
    var result = new Point(point);
    var fromTopParent = from;
    for (var c = from.getParent(); c != null; c = c.getParent()) {
      fromTopParent = c;
      if (c.getParent() == null) {
        break;
      }
      result.translate(c.getX(), c.getY());
    }
    var toTopParent = to;
    var toOffset = new Point(0, 0);
    for (var c = to.getParent(); c != null; c = c.getParent()) {
      toTopParent = c;
      if (c.getParent() == null) {
        break;
      }
      toOffset.translate(c.getX(), c.getY());
    }
    if (fromTopParent != toTopParent) {
      throw new IllegalArgumentException("Components don't have a common parent: " + from + " and " + to);
    }
    result.translate(-toOffset.x, -toOffset.y);
    return result;
  }

  private @NotNull VisualPosition nextSelectionVisualPosition(@NotNull VisualPosition pos) {
    if (!isColumnMode() && pos.column >= EditorUtil.getLastVisualLineColumnNumber(this, pos.line)) {
      return new VisualPosition(pos.line + 1, 0, false);
    }
    return new VisualPosition(pos.line, pos.column + 1, false);
  }

  private @NotNull VisualPosition prevSelectionVisualPosition(@NotNull VisualPosition pos) {
    int prevColumn = pos.column - 1;
    if (prevColumn >= 0) {
      return new VisualPosition(pos.line, prevColumn, true);
    }
    if (isColumnMode() || pos.line == 0) {
      return new VisualPosition(pos.line, 0, true);
    }
    int prevLine = pos.line - 1;
    return new VisualPosition(prevLine, EditorUtil.getLastVisualLineColumnNumber(this, prevLine), true);
  }

  private void setupSpecialSelectionOnMouseDrag(int newCaretOffset, int caretShift) {
    int newSelectionStart;
    int newSelectionEnd = newCaretOffset;
    if (caretShift < 0) {
      if (getMouseSelectionState() == MOUSE_SELECTION_STATE_WORD_SELECTED) {
        newSelectionEnd = myCaretModel.getWordAtCaretStart(mySettings.isCamelWords() && mySettings.isMouseClickSelectionHonorsCamelWords());
      }
      else if (getMouseSelectionState() == MOUSE_SELECTION_STATE_LINE_SELECTED) {
        newSelectionEnd = visualPositionToOffset(new VisualPosition(getCaretModel().getVisualPosition().line, 0));
      }
      newSelectionStart = validateOffset(mySavedSelectionEnd);
    }
    else {
      if (getMouseSelectionState() == MOUSE_SELECTION_STATE_WORD_SELECTED) {
        newSelectionEnd = myCaretModel.getWordAtCaretEnd(mySettings.isCamelWords() && mySettings.isMouseClickSelectionHonorsCamelWords());
      }
      else if (getMouseSelectionState() == MOUSE_SELECTION_STATE_LINE_SELECTED) {
        newSelectionEnd = visualPositionToOffset(new VisualPosition(getCaretModel().getVisualPosition().line + 1, 0));
      }
      newSelectionStart = validateOffset(mySavedSelectionStart);
    }
    if (newSelectionEnd < 0) newSelectionEnd = newCaretOffset;
    mySelectionModel.setSelection(newSelectionStart, newSelectionEnd);
    myCaretModel.moveToOffset(newSelectionEnd);
  }

  private int validateOffset(int offset) {
    if (offset < 0) return 0;
    if (offset > myDocument.getTextLength()) return myDocument.getTextLength();
    return offset;
  }

  void clearDnDContext() {
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

  private @NotNull Caret getLeadCaret() {
    List<Caret> allCarets = myCaretModel.getAllCarets();
    Caret firstCaret = allCarets.get(0);
    if (firstCaret == myCaretModel.getPrimaryCaret()) {
      return allCarets.get(allCarets.size() - 1);
    }
    return firstCaret;
  }

  private void setSelectionAndBlockActions(@NotNull MouseEvent mouseDragEvent,
                                           @NotNull VisualPosition startPosition,
                                           int startOffset,
                                           @NotNull VisualPosition endPosition,
                                           int endOffset) {
    mySelectionModel.setSelection(startPosition, startOffset, endPosition, endOffset);
    if (myCurrentDragIsSubstantial || startOffset != endOffset || !Comparing.equal(startPosition, endPosition)) {
      onSubstantialDrag(mouseDragEvent);
    }
  }

  private void blockActionsIfNeeded(@NotNull MouseEvent mouseDragEvent,
                                    @NotNull LogicalPosition startPosition,
                                    @NotNull LogicalPosition endPosition) {
    if (myCurrentDragIsSubstantial || !startPosition.equals(endPosition)) {
      onSubstantialDrag(mouseDragEvent);
    }
  }

  private void onSubstantialDrag(@NotNull MouseEvent mouseDragEvent) {
    IdeEventQueue.getInstance().blockNextEvents(mouseDragEvent, IdeEventQueue.BlockMode.ACTIONS);
    myCurrentDragIsSubstantial = true;
  }

  void updateCaretCursor() {
    myUpdateCursor = true;
    if (myCaretCursor.myIsShown) {
      myCaretCursor.myStartTime = System.currentTimeMillis();
    }
    else {
      myCaretCursor.myIsShown = true;
      myCaretCursor.repaint();
    }
  }

  private void setCursorPosition() {
    List<CaretRectangle> caretPoints = new ArrayList<>();
    for (Caret caret : getCaretModel().getAllCarets()) {
      boolean isRtl = caret.isAtRtlLocation();
      VisualPosition caretPosition = caret.getVisualPosition();
      Point2D pos1 = visualPositionToPoint2D(caretPosition.leanRight(!isRtl));
      Point2D pos2 = visualPositionToPoint2D(new VisualPosition(caretPosition.line,
                                                                Math.max(0, caretPosition.column + (isRtl ? -1 : 1)), isRtl));
      float width = (float)Math.abs(pos2.getX() - pos1.getX());
      if (!isRtl && myInlayModel.hasInlineElementAt(caretPosition)) {
        width = Math.min(width, (float)Math.ceil(myView.getPlainSpaceWidth()));
      }
      caretPoints.add(new CaretRectangle(pos1, width, caret, isRtl));
    }
    myCaretCursor.setPositions(caretPoints.toArray(new CaretRectangle[0]));
  }

  @Override
  public boolean setCaretVisible(boolean b) {
    return EditorThreading.compute(() -> {
      boolean old = myCaretCursor.isActive();
      if (b) {
        myCaretCursor.activate();
      }
      else {
        myCaretCursor.passivate();
      }
      return old;
    });
  }

  @Override
  public boolean setCaretEnabled(boolean enabled) {
    return EditorThreading.compute(() -> {
      boolean old = myCaretCursor.isEnabled();
      myCaretCursor.setEnabled(enabled);
      return old;
    });
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
  public @Nullable Project getProject() {
    return myProject;
  }

  @Override
  public boolean isOneLineMode() {
    return myState.isOneLineMode();
  }

  @Override
  public boolean isEmbeddedIntoDialogWrapper() {
    return myState.isEmbeddedIntoDialogWrapper();
  }

  @Override
  public void setEmbeddedIntoDialogWrapper(boolean b) {
    assertIsDispatchThread();
    EditorThreading.run(() -> myState.setEmbeddedIntoDialogWrapper(b));
  }

  private void isEmbeddedIntoDialogWrapperChanged(ObservableStateListener.PropertyChangeEvent event) {
    assertIsDispatchThread();

    Object newValue = event.getNewValue();
    if (!(newValue instanceof Boolean)) {
      LOG.error("newValue is not Boolean. property name = " + event.getPropertyName() + ", newValue = " + newValue);
      return;
    }
    boolean newValueBoolean = (boolean)newValue;

    myScrollPane.setFocusable(!newValueBoolean);
    myEditorComponent.setFocusCycleRoot(!newValueBoolean);
    myEditorComponent.setFocusable(newValueBoolean);
  }

  @Override
  public void setOneLineMode(boolean isOneLineMode) {
    myState.setOneLineMode(isOneLineMode);
  }

  private void isOneLineModeChanged(ObservableStateListener.PropertyChangeEvent event) {
    Object newValue = event.getNewValue();
    if (!(newValue instanceof Boolean)) {
      LOG.error("newValue is not Boolean. property name = " + event.getPropertyName() + ", newValue = " + newValue);
      return;
    }

    mouseDragHandler.setNativeSelectionEnabled((boolean)newValue);
    getScrollPane().setInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, null);
    JBScrollPane pane = ObjectUtils.tryCast(getScrollPane(), JBScrollPane.class);
    JComponent component = pane == null ? null : pane.getStatusComponent();
    if (component != null) component.setVisible(!isOneLineMode());
    reinitSettings();

    Object oldValue = extractOldValueOrLog(event, false);
    myPropertyChangeSupport.firePropertyChange(PROP_ONE_LINE_MODE, oldValue, event.getNewValue());
  }

  public static final class CaretRectangle {
    public final @NotNull Point2D myPoint;
    public final float myWidth;
    public final @Nullable Caret myCaret;
    public final boolean myIsRtl;

    private CaretRectangle(@NotNull Point2D point, float width, @Nullable Caret caret, boolean isRtl) {
      myPoint = point;
      myWidth = Math.max(width, 2);
      myCaret = caret;
      myIsRtl = isRtl;
    }
  }

  final class CaretCursor {
    private CaretRectangle @NotNull [] myLocations = {new CaretRectangle(new Point(0, 0), 0, null, false)};
    private boolean myEnabled = true;

    private boolean myIsShown;
    private long myStartTime;

    public boolean isEnabled() {
      return myEnabled;
    }

    public void setEnabled(boolean enabled) {
      myEnabled = enabled;
    }

    private void activate() {
      boolean blink = mySettings.isBlinkCaret();
      int blinkPeriod = mySettings.getCaretBlinkPeriod();
      synchronized (caretRepaintService) {
        caretRepaintService.setEditor(EditorImpl.this);
        caretRepaintService.setBlinking(blink);
        caretRepaintService.setBlinkPeriod(blinkPeriod);
        myIsShown = true;
        caretRepaintService.restart();
      }
    }

    public boolean isActive() {
      synchronized (caretRepaintService) {
        return myIsShown;
      }
    }

    void setActive(boolean isActive) {
      synchronized (caretRepaintService) {
        myIsShown = isActive;
      }
    }

    private void passivate() {
      synchronized (caretRepaintService) {
        myIsShown = false;
      }
    }

    long getStartTime() {
      synchronized (caretRepaintService) {
        return myStartTime;
      }
    }

    private void setPositions(CaretRectangle @NotNull [] locations) {
      myStartTime = System.currentTimeMillis();
      myLocations = locations;
    }

    void repaint() {
      myView.repaintCarets();
    }

    private boolean isEditorInputFocusOwner() {
      Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
      Component content = getContentComponent();
      for (Component temp = focusOwner; temp != null; temp = (temp instanceof Window) ? null : temp.getParent()) {
        if (temp == content) {
          return true;
        }
        // check if hosted component is an input focus owner, in that case input focus belongs to hosted component instead of the editor
        else if (temp instanceof EditorHostedComponent hostedComponent && hostedComponent.isInputFocusOwner()) {
          return false;
        }
      }
      return false;
    }

    CaretRectangle @Nullable [] getCaretLocations(boolean onlyIfShown) {
      if (onlyIfShown && (!isEnabled() || !isActive() || isRendererMode() || !isEditorInputFocusOwner())) {
        return null;
      }
      return myLocations;
    }
  }

  private final class ScrollingTimer {
    private Timer myTimer;
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


      myTimer = TimerUtil.createNamedTimer("Editor scroll timer", Registry.intValue("editor.scrolling.animation.interval.ms"), e -> {
        if (isDisposed()) {
          stop();
          return;
        }
        Runnable command = () -> {
          int oldSelectionStart = mySelectionModel.getLeadSelectionOffset();
          VisualPosition caretPosition = myMultiSelectionInProgress ? myTargetMultiSelectionPosition
                                                                    : getCaretModel().getVisualPosition();
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
            setupSpecialSelectionOnMouseDrag(newCaretOffset, caretShift);
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
        };
        WriteIntentReadAction.run((Runnable)() ->
          myCommandProcessor.executeCommand(myProject, command, EditorBundle.message("move.cursor.command.name"),
                                            DocCommandGroupId.noneGroupId(getDocument()), UndoConfirmationPolicy.DEFAULT, getDocument())
        );
      });
      myTimer.start();
    }

    private void stop() {
      if (myTimer != null) {
        myTimer.stop();
        myTimer = null;
      }
    }
  }

  private static void updateOpaque(@Nullable JScrollBar bar) {
    if (bar instanceof OpaqueAwareScrollBar) {
      bar.setOpaque(((OpaqueAwareScrollBar)bar).myOpaque);
    }
  }

  private class OpaqueAwareScrollBar extends JBScrollBar {
    private boolean myOpaque;

    private OpaqueAwareScrollBar(@JdkConstants.AdjustableOrientation int orientation) {
      super(orientation);
      putClientProperty(ColorKey.FUNCTION_KEY, (Function<ColorKey, Color>)key -> getColorsScheme().getColor(key));
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

  final class MyScrollBar extends OpaqueAwareScrollBar {
    private static final @NonNls String APPLE_LAF_AQUA_SCROLL_BAR_UI_CLASS = "apple.laf.AquaScrollBarUI";
    private ScrollBarUI myPersistentUI;

    private MyScrollBar(@JdkConstants.AdjustableOrientation int orientation) {
      super(orientation);
    }

    void setPersistentUI(@NotNull ScrollBarUI ui) {
      myPersistentUI = ui;
      setUI(ui);
    }

    @Override
    public void setUI(ScrollBarUI ui) {
      if (myPersistentUI == null) myPersistentUI = ui;
      super.setUI(myPersistentUI);
    }

    /**
     * Returns the height of the top (decrease) scroll bar button.
     * The real height is only available if the scroll bar is a BasicScrollBarUI.
     * Otherwise, returns a fake but good enough value.
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
          JButton decrButtonValue = (JButton)BasicScrollBarUiButtonHolder.decrButtonField.invoke(barUI);
          LOG.assertTrue(decrButtonValue != null);
          return top + decrButtonValue.getHeight();
        }
        catch (Throwable e) {
          throw new IllegalStateException(e);
        }
      }
      return top + 15;
    }

    /**
     * Returns the height of the bottom (increase) scroll bar button.
     * The real height is only available if the scroll bar is a BasicScrollBarUI.
     * Otherwise, returns a fake but good enough value.
     */
    int getIncScrollButtonHeight() {
      ScrollBarUI barUI = getUI();
      Insets insets = getInsets();
      if (barUI instanceof ButtonlessScrollBarUI) {
        return insets.top + ((ButtonlessScrollBarUI)barUI).getIncrementButtonHeight();
      }
      if (barUI instanceof BasicScrollBarUI) {
        try {
          JButton incrButtonValue = (JButton)BasicScrollBarUiButtonHolder.incrButtonField.invoke(barUI);
          LOG.assertTrue(incrButtonValue != null);
          return insets.bottom + incrButtonValue.getHeight();
        }
        catch (Throwable e) {
          throw new IllegalStateException(e);
        }
      }
      if (barUI != null && APPLE_LAF_AQUA_SCROLL_BAR_UI_CLASS.equals(barUI.getClass().getName())) {
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

  @Override
  public @NotNull CopyProvider getCopyProvider() {
    return myEditable;
  }

  @Override
  public @NotNull CutProvider getCutProvider() {
    return myEditable;
  }

  @Override
  public @NotNull PasteProvider getPasteProvider() {
    return myEditable;
  }

  @Override
  public @NotNull DeleteProvider getDeleteProvider() {
    return myEditable;
  }

  /**
   * Returns the instance of the character grid corresponding to this editor
   * <p>
   *   A non-{@code null} value is only returned when the character grid mode is enabled,
   *   see {@link EditorSettings#setCharacterGridWidthMultiplier(Float)}.
   * </p>
   * @return the current character grid instance or {@code null} if the editor is not in the grid mode
   */
  @ApiStatus.Internal
  public @Nullable CharacterGrid getCharacterGrid() {
    if (getSettings().getCharacterGridWidthMultiplier() == null) {
      myCharacterGrid = null;
      return null;
    }
    else {
      if (myCharacterGrid == null) {
        myCharacterGrid = new CharacterGridImpl(this);
      }
      return myCharacterGrid;
    }
  }

  /**
   * This method doesn't set the scheme itself, but the delegate
   * @param scheme - original scheme
   * @see EditorColorSchemeDelegate
   */
  @Override
  public void setColorsScheme(final @NotNull EditorColorsScheme scheme) {
    assertIsDispatchThread();
    EditorThreading.run(() -> {
      final EditorColorsManager colorsManager = ApplicationManager.getApplication().getServiceIfCreated(EditorColorsManager.class);
      if (colorsManager == null) {
        LOG.info("Skipping attempt to set color scheme without EditorColorsManager");
        return;
      }
      if (scheme instanceof EditorColorSchemeDelegate) {
        myScheme = (EditorColorSchemeDelegate)scheme;
      }
      else {
        myScheme = new EditorColorSchemeDelegate(this, scheme);
      }
      reinitSettings();
    });
  }

  @Override
  public @NotNull EditorColorsScheme getColorsScheme() {
    return myScheme;
  }

  static void assertIsDispatchThread() {
    ThreadingAssertions.assertEventDispatchThread();
  }

  @Override
  public void setVerticalScrollbarOrientation(int type) {
    assertIsDispatchThread();
    EditorThreading.run(() -> myState.setVerticalScrollBarOrientation(type));
  }

  private void verticalScrollBarOrientationChanged(ObservableStateListener.PropertyChangeEvent event) {
    assertIsDispatchThread();

    Object newValue = event.getNewValue();
    if (!(newValue instanceof Integer)) {
      LOG.error("newValue is not Integer. property name = " + event.getPropertyName() + ", newValue = " + newValue);
      return;
    }

    int currentHorOffset = myScrollingModel.getHorizontalScrollOffset();
    myScrollPane.putClientProperty(JBScrollPane.Flip.class,
                                   (int)newValue == VERTICAL_SCROLLBAR_LEFT
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
    return myState.getVerticalScrollBarOrientation();
  }

  public boolean isMirrored() {
    return myState.getVerticalScrollBarOrientation() != VERTICAL_SCROLLBAR_RIGHT;
  }

  @NotNull
  MyScrollBar getVerticalScrollBar() {
    return myVerticalScrollBar;
  }

  @ApiStatus.Internal
  public int getStickyLinesPanelWidth() {
    return myPanel.getWidth(); //- myVerticalScrollBar.getWidth();
  }

  @MouseSelectionState
  private int getMouseSelectionState() {
    return myMouseSelectionState;
  }

  /**
   * Update baseline selection if {@link Caret#selectWordAtCaret} action was performed asynchronously.
   *
   * @see #selectWordAtCaret(boolean)
   */
  @ApiStatus.Internal
  public void updateMouseWordSelectionStateToCaret() {
    if (myMouseSelectionState != MOUSE_SELECTION_STATE_WORD_SELECTED) return;
    Caret caret = getCaretModel().getCurrentCaret();
    mySavedSelectionStart = caret.getSelectionStart();
    mySavedSelectionEnd = caret.getSelectionEnd();
    caret.moveToOffset(mySavedSelectionEnd);
  }

  private void setMouseSelectionState(@MouseSelectionState int mouseSelectionState) {
    if (getMouseSelectionState() == mouseSelectionState) return;

    myMouseSelectionState = mouseSelectionState;
    myMouseSelectionChangeTimestamp = System.currentTimeMillis();

    mouseSelectionStateAlarm.cancel();
    if (myMouseSelectionState != MOUSE_SELECTION_STATE_NONE) {
      if (mouseSelectionStateResetRunnable == null) {
        mouseSelectionStateResetRunnable = () -> WriteIntentReadAction.run((Runnable)() -> resetMouseSelectionState(null, null));
      }
      mouseSelectionStateAlarm.request(Registry.intValue("editor.mouseSelectionStateResetTimeout"),
                                       ModalityState.stateForComponent(myEditorComponent),
                                       mouseSelectionStateResetRunnable);
    }
  }

  private void resetMouseSelectionState(@Nullable MouseEvent event, @Nullable EditorMouseEvent editorMouseEvent) {
    setMouseSelectionState(MOUSE_SELECTION_STATE_NONE);

    MouseEvent e = event != null ? event : myMouseMovedEvent;
    if (e != null) {
      validateMousePointer(e, editorMouseEvent);
    }
  }

  private void cancelAutoResetForMouseSelectionState() {
    mouseSelectionStateAlarm.cancel();
  }

  @RequiresEdt(generateAssertion = false)
  @NotNull EditorInputMethodSupport getInputMethodSupport() {
    if (myInputMethodSupport == null) {
      MyInputMethodHandler handler = new MyInputMethodHandler();
      myInputMethodSupport = new EditorInputMethodSupport(handler, new EditorInputMethodListener(handler));
    }
    return myInputMethodSupport;
  }

  @ApiStatus.Internal
  @RequiresEdt(generateAssertion = false)
  public void setInputMethodSupport(@Nullable EditorInputMethodSupport inputMethodSupport) {
    myInputMethodSupport = inputMethodSupport;
  }

  @Override
  public boolean processKeyTyped(@NotNull KeyEvent e) {
    myLastTypedActionTimestamp = -1;
    if (e.getID() != KeyEvent.KEY_TYPED) {
      return false;
    }
    if (!UIUtil.isReallyTypedEvent(e)) { // Hack just like in javax.swing.text.DefaultEditorKit.DefaultKeyTypedAction
      return false;
    }
    char c = e.getKeyChar();
    myLastTypedActionTimestamp = e.getWhen();
    myLastTypedAction = Character.toString(c);
    processKeyTyped(c);
    return true;
  }

  public void recordLatencyAwareAction(@NotNull String actionId, long timestampMs) {
    myLastTypedActionTimestamp = timestampMs;
    myLastTypedAction = actionId;
  }

  void measureTypingLatency() {
    if (myLastTypedActionTimestamp == -1) {
      return;
    }
    long latencyMs = System.currentTimeMillis() - myLastTypedActionTimestamp;
    myLastTypedActionTimestamp = -1;

    LatencyListener latencyPublisher = myLatencyPublisher;
    if (latencyPublisher == null) {
      latencyPublisher = ApplicationManager.getApplication().getMessageBus().syncPublisher(LatencyListener.TOPIC);
      myLatencyPublisher = latencyPublisher;
    }
    latencyPublisher.recordTypingLatency(this, myLastTypedAction, latencyMs);
  }

  public boolean isProcessingTypedAction() {
    return myLastTypedActionTimestamp != -1;
  }

  void beforeModalityStateChanged() {
    myScrollingModel.beforeModalityStateChanged();
    if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
      myGutterComponent.resetMousePointer();
      resetMousePointer();
    }
  }

  EditorDropHandler getDropHandler() {
    return myDropHandler;
  }

  public void setDropHandler(@NotNull EditorDropHandler dropHandler) {
    myDropHandler = dropHandler;
  }

  RangeMarker getDraggedRange() {
    return myDraggedRange;
  }

  void setDraggedRange(RangeMarker draggedRange) {
    myDraggedRange = draggedRange;
  }

  /**
   * @deprecated Use {@link #addHighlightingPredicate} and {@link #removeHighlightingPredicate} instead.
   */
  @Deprecated
  public void setHighlightingPredicate(@Nullable Predicate<? super RangeHighlighter> filter) {
    if (filter == null) {
      removeHighlightingPredicate(EditorHighlightingPredicateWrapper.KEY);
    }
    else {
      EditorHighlightingPredicateWrapper wrapper = new EditorHighlightingPredicateWrapper(filter);
      addHighlightingPredicate(EditorHighlightingPredicateWrapper.KEY, wrapper);
    }
  }

  /**
   * Adds a highlighting predicate to the editor markup model and removes a previous predicated associated with the key.
   * The predicate is applied to this editor only, other editors are not affected.
   * <p/>
   * A range highlighter is shown for the editor only if all predicates accept it.
   * <p/>
   *
   * @param key       the key associated with the predicate
   * @param predicate the predicate that checks if the editor should show a range highlighter or not.
   */
  @ApiStatus.Experimental
  @RequiresEdt
  public <T extends EditorHighlightingPredicate> @Nullable T addHighlightingPredicate(@NotNull Key<T> key, @NotNull T predicate) {
    T oldPredicate = getUserData(key);
    putUserData(key, predicate);
    HighlighterFilter oldFilter = myHighlightingFilter.updatePredicate(predicate, oldPredicate);
    updateRangeMarkers(oldFilter);
    return oldPredicate;
  }

  /**
   * Removes the highlighting predicate from the editor associated with key.
   * It is applied to this editor only, other editors are not affected.
   * <p/>
   *
   * @param key the key associated with the highlighter predicate to be removed
   */
  @ApiStatus.Experimental
  @RequiresEdt
  public <T extends EditorHighlightingPredicate> @Nullable T removeHighlightingPredicate(@NotNull Key<T> key) {
    T predicate = getUserData(key);
    if (predicate == null) {
      return null;
    }

    putUserData(key, null);
    HighlighterFilter oldFilter = myHighlightingFilter.removePredicate(predicate);
    updateRangeMarkers(oldFilter);
    return predicate;
  }

  @ApiStatus.Experimental
  @RequiresEdt
  private void updateRangeMarkers(@Nullable HighlighterFilter oldFilter) {
    if (oldFilter == null) {
      // nothing changed
      return;
    }

    TextEditor textEditor = null;

    for (RangeHighlighter highlighter : myEditorFilteringMarkupModel.getDelegate().getAllHighlighters()) {
      boolean oldAvailable = oldFilter.shouldRender(highlighter);
      boolean newAvailable = myHighlightingFilter.shouldRender(highlighter);
      if (oldAvailable != newAvailable) {
        TextAttributes attributes = highlighter.getTextAttributes(getColorsScheme());
        myMarkupModelListener.attributesChanged((RangeHighlighterEx)highlighter, true,
                                                EditorUtil.attributesImpactFontStyle(attributes),
                                                EditorUtil.attributesImpactForegroundColor(attributes));
        HighlightInfo fileLevelInfo = HighlightInfo.fromRangeHighlighter(highlighter);
        if (fileLevelInfo != null && fileLevelInfo.isFileLevelAnnotation()) {
          if (textEditor == null) {
            textEditor = TextEditorProvider.getInstance().getTextEditor(this);
          }

          JComponent component = fileLevelInfo.getFileLevelComponent(textEditor);
          if (component != null) {
            component.setVisible(newAvailable);
          }
        }
      }
    }
  }

  boolean isHighlighterAvailable(@NotNull RangeHighlighter highlighter) {
    return myHighlightingFilter.shouldRender(highlighter);
  }

  private boolean hasBlockInlay(@NotNull Point point) {
    Inlay<?> inlay = myInlayModel.getElementAt(point);
    return inlay != null && (inlay.getPlacement() == Inlay.Placement.ABOVE_LINE || inlay.getPlacement() == Inlay.Placement.BELOW_LINE);
  }

  private static final class EditorInputMethodListener implements InputMethodListener {

    private final MyInputMethodHandler myHandler;

    private EditorInputMethodListener(@NotNull MyInputMethodHandler handler) {
      myHandler = handler;
    }

    @Override
    public void inputMethodTextChanged(InputMethodEvent event) {
      myHandler.replaceInputMethodText(event);
      myHandler.setInputMethodCaretPosition(event);
      event.consume();
    }

    @Override
    public void caretPositionChanged(InputMethodEvent event) {
      myHandler.setInputMethodCaretPosition(event);
      event.consume();
    }
  }

  private final class MyInputMethodHandler implements InputMethodRequests {
    /**
     * Very high inlay priority to keep IME inlays to be always the nearest to the caret.
     * Not the Integer.MAX_VALUE to prevent accidental overflow.
     */
    private static final int IME_INLAY_PRIORITY = 1000000;

    private RangeMarker composedRangeMarker;
    private Inlay<?> inlayLeft;
    private Inlay<?> inlayRight;

    private @Nullable ProperTextRange getRange() {
      if (composedRangeMarker == null) return null;
      return new ProperTextRange(composedRangeMarker.getStartOffset(), composedRangeMarker.getEndOffset());
    }

    @Override
    public @NotNull Rectangle getTextLocation(TextHitInfo offset) {
      if (isDisposed()) return new Rectangle();
      Point caret = logicalPositionToXY(getCaretModel().getPrimaryCaret().getLogicalPosition());
      Rectangle r = new Rectangle(caret, new Dimension(1, getLineHeight()));
      Point p = getLocationOnScreen(getContentComponent());
      r.translate(p.x, p.y);
      return r;
    }

    @Override
    public @Nullable TextHitInfo getLocationOffset(int x, int y) {
      if (composedRangeMarker != null) {
        Point p = getLocationOnScreen(getContentComponent());
        p.x = x - p.x;
        p.y = y - p.y;
        int pos = logicalPositionToOffset(xyToLogicalPosition(p));
        if (composedRangeMarker.getTextRange().containsInclusive(pos)) {
          return TextHitInfo.leading(pos - composedRangeMarker.getStartOffset());
        }
      }
      return null;
    }

    private boolean isComposedTextShown() {
      return composedRangeMarker != null || inlayLeft != null || inlayRight != null;
    }

    private static @NotNull Point getLocationOnScreen(@NotNull Component component) {
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
      if (composedRangeMarker != null) {
        composedStartIndex = composedRangeMarker.getStartOffset();
        composedEndIndex = composedRangeMarker.getEndOffset();
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

    private @NotNull String getText(int startIdx, int endIdx) {
      if (startIdx >= 0 && endIdx > startIdx) {
        CharSequence chars = getDocument().getImmutableCharSequence();
        return chars.subSequence(startIdx, endIdx).toString();
      }

      return "";
    }

    @Override
    public @NotNull AttributedCharacterIterator getCommittedText(int beginIndex,
                                                                 int endIndex,
                                                                 AttributedCharacterIterator.Attribute[] attributes) {
      int composedStartIndex = 0;
      int composedEndIndex = 0;
      if (composedRangeMarker != null) {
        composedStartIndex = composedRangeMarker.getStartOffset();
        composedEndIndex = composedRangeMarker.getEndOffset();
      }

      String committed;
      if (beginIndex < composedStartIndex) {
        if (endIndex <= composedStartIndex) {
          committed = getText(beginIndex, endIndex);
        }
        else {
          int firstPartLength = composedStartIndex - beginIndex;
          committed = getText(beginIndex, beginIndex + firstPartLength) + getText(composedEndIndex, endIndex - firstPartLength);
        }
      }
      else {
        committed = getText(beginIndex + composedEndIndex - composedStartIndex, endIndex);
      }

      return new AttributedString(committed).getIterator();
    }

    @Override
    public int getCommittedTextLength() {
      int length = getDocument().getTextLength();
      if (composedRangeMarker != null) {
        length -= composedRangeMarker.getEndOffset() - composedRangeMarker.getStartOffset();
      }
      return length;
    }

    @Override
    public @Nullable AttributedCharacterIterator cancelLatestCommittedText(AttributedCharacterIterator.Attribute[] attributes) {
      return null;
    }

    @Override
    public @Nullable AttributedCharacterIterator getSelectedText(AttributedCharacterIterator.Attribute[] attributes) {
      if (myCharKeyPressed) {
        myNeedToSelectPreviousChar = true;
      }
      String text = getSelectionModel().getSelectedText();
      return text == null ? null : new AttributedString(text).getIterator();
    }

    private static @NotNull String createComposedString(int composedIndex, @NotNull AttributedCharacterIterator text) {
      StringBuilder strBuf = new StringBuilder();

      // create attributed string with no attributes
      for (char c = text.setIndex(composedIndex); c != CharacterIterator.DONE; c = text.next()) {
        strBuf.append(c);
      }

      return strBuf.toString();
    }

    private void setInputMethodCaretPosition(@NotNull InputMethodEvent e) {
      if (composedRangeMarker != null) {
        int dot = composedRangeMarker.getStartOffset();

        TextHitInfo caretPos = e.getCaret();
        if (caretPos != null) {
          dot += caretPos.getInsertionIndex();
        }

        getCaretModel().moveToOffset(dot);
        getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      }
    }

    private void runUndoTransparent(@NotNull Runnable runnable) {
      CommandProcessor.getInstance().runUndoTransparentAction(
        () -> CommandProcessor.getInstance().executeCommand(myProject, () -> ApplicationManager.getApplication().runWriteAction(runnable),
                                                            "", getDocument(), UndoConfirmationPolicy.DEFAULT, getDocument()));
    }

    private static boolean hasRelevantCommittedText(@NotNull InputMethodEvent e) {
      if (e.getCommittedCharacterCount() <= 0) return false;
      AttributedCharacterIterator text = e.getText();
      return text == null || text.first() != 0xA5 /* Yen character */;
    }

    private void replaceInputMethodText(@NotNull InputMethodEvent e) {
      if (myNeedToSelectPreviousChar && SystemInfo.isMac &&
          (Registry.is("ide.mac.pressAndHold.brute.workaround") || Registry.is("ide.mac.pressAndHold.workaround") &&
                                                                   (hasRelevantCommittedText(e) || e.getCaret() == null))) {
        // This is required to support input of accented characters using press-and-hold method (http://support.apple.com/kb/PH11264).
        // JDK currently properly supports this functionality only for TextComponent/JTextComponent descendants.
        // For our editor component, we need this workaround.
        // After https://bugs.openjdk.org/browse/JDK-8074882 is fixed, this workaround should be replaced with a proper solution.
        myNeedToSelectPreviousChar = false;
        getCaretModel().runForEachCaret(caret -> {
          int caretOffset = caret.getOffset();
          if (caretOffset > 0) {
            caret.setSelection(caretOffset - 1, caretOffset);
          }
        });
      }

      boolean isCaretMoved = false;
      int caretPositionToRestore = 0;
      int composedStartIndex = -1;

      int commitCount = e.getCommittedCharacterCount();
      AttributedCharacterIterator text = e.getText();

      // old composed text deletion
      Document doc = getDocument();

      if (inlayLeft != null || inlayRight != null) {
        composedStartIndex = inlayLeft != null ? inlayLeft.getOffset() : inlayRight.getOffset();
        if (inlayLeft != null) {
          Disposer.dispose(inlayLeft);
          inlayLeft = null;
        }
        if (inlayRight != null) {
          Disposer.dispose(inlayRight);
          inlayRight = null;
        }
      }
      if (composedRangeMarker != null) {
        if (!isViewer() && doc.isWritable()) {
          composedStartIndex = composedRangeMarker.getStartOffset();
          runUndoTransparent(() -> {
            if (composedRangeMarker.isValid()) {
              doc.deleteString(composedRangeMarker.getStartOffset(), composedRangeMarker.getEndOffset());
            }
          });
          if (composedRangeMarker.isValid()) {
            // IDEA-331578: Deleting a string from a document may move the range marker to the left (i.e. during inline rename)
            // We must use the new `startOffset` value here if the range marker is still valid
            composedStartIndex = composedRangeMarker.getStartOffset();
          }
        }
        composedRangeMarker.dispose();
        composedRangeMarker = null;
      }
      if (composedStartIndex >= 0) {
        isCaretMoved = getCaretModel().getOffset() != composedStartIndex;
        if (isCaretMoved) {
          caretPositionToRestore = getCaretModel().getCurrentCaret().getOffset();
          // if caret set further in the doc, we should add commitCount
          if (caretPositionToRestore > composedStartIndex) {
            caretPositionToRestore += commitCount;
          }
          getCaretModel().moveToOffset(composedStartIndex);
        }
      }

      if (text != null) {
        text.first();

        // committed text insertion
        if (commitCount > 0) {
          for (char c = text.current(); c != CharacterIterator.DONE && commitCount > 0; c = text.next(), commitCount--) {
            if (c >= 0x20 && c != 0x7F) { // Hack just like in javax.swing.text.DefaultEditorKit.DefaultKeyTypedAction
              processKeyTyped(c);
            }
          }
        }

        // new composed text insertion
        if (!isViewer() && doc.isWritable()) {
          int composedTextIndex = text.getIndex();
          if (composedTextIndex < text.getEndIndex()) {
            String composedString = createComposedString(composedTextIndex, text);
            if (Registry.is("editor.input.method.inlay")) {
              runUndoTransparent(() -> EditorModificationUtil.deleteSelectedTextForAllCarets(EditorImpl.this));

              var offset = getCaretModel().getCurrentCaret().getOffset();
              var caret = e.getCaret();
              var leftLength = caret != null ? caret.getInsertionIndex() : 0;
              if (leftLength > 0) {
                inlayLeft = getInlayModel().addInlineElement(offset, false, -IME_INLAY_PRIORITY,
                                                             new InputMethodInlayRenderer(composedString.substring(0, leftLength)));
              }
              if (leftLength < composedString.length()) {
                inlayRight = getInlayModel().addInlineElement(offset, true, IME_INLAY_PRIORITY,
                                                              new InputMethodInlayRenderer(composedString.substring(leftLength)));
              }
            }
            else {
              runUndoTransparent(() -> EditorModificationUtilEx.insertStringAtCaret(EditorImpl.this, composedString, false, false));
              composedRangeMarker =
                myDocument.createRangeMarker(getCaretModel().getOffset(), getCaretModel().getOffset() + composedString.length(), true);
            }
          }
        }
      }

      if (isCaretMoved) {
        getCaretModel().moveToOffset(caretPositionToRestore);
      }
    }
  }

  private final class MyMouseAdapter extends MouseAdapter {
    @Override
    public void mousePressed(@NotNull MouseEvent e) {
      if (EVENT_LOG.isDebugEnabled()) {
        EVENT_LOG.debug(e.toString());
      }
      requestFocus();
      runMousePressedCommand(e);
      myInitialMouseEvent = e.isConsumed() ? e : null;
    }

    @Override
    public void mouseReleased(@NotNull MouseEvent e) {
      if (EVENT_LOG.isDebugEnabled()) {
        EVENT_LOG.debug(e.toString());
      }
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
      if (EVENT_LOG.isDebugEnabled()) {
        EVENT_LOG.debug(e.toString());
      }
      runMouseEnteredCommand(e);
    }

    @Override
    public void mouseExited(@NotNull MouseEvent e) {
      if (EVENT_LOG.isDebugEnabled()) {
        EVENT_LOG.debug(e.toString());
      }
      runMouseExitedCommand(e);
      myGutterComponent.mouseExited(e);
    }

    private void runMousePressedCommand(@NotNull MouseEvent e) {
      EditorMouseEvent event = createEditorMouseEvent(e);
      myLastPressWasAtBlockInlay = false;
      myLastMousePressedLocation = event.getLogicalPosition();
      myLastMousePressedPoint = convertPoint(e.getComponent(), e.getPoint(), myEditorComponent);
      myLastPressedOnGutter = e.getSource() == myGutterComponent;
      var lastPressedPointOnGutter = convertPoint(myEditorComponent, myLastMousePressedPoint, myGutterComponent);
      myLastPressedOnGutterIcon = myGutterComponent.getGutterRenderer(lastPressedPointOnGutter) != null;
      myCaretStateBeforeLastPress = isToggleCaretEvent(e) ? myCaretModel.getCaretsAndSelections() : Collections.emptyList();
      myCurrentDragIsSubstantial = false;
      myMouseDragStarted = false;
      myDragStarted = false;
      myDragSelectionStarted = false;
      myForcePushHappened = false;
      clearDnDContext();

      myMousePressedEvent = e;

      myExpectedCaretOffset = event.getOffset();
      try {
        for (EditorMouseListener mouseListener : myMouseListeners) {
          mouseListener.mousePressed(event);
          if (isReleased) return;
        }
      }
      finally {
        myExpectedCaretOffset = -1;
      }

      if (composedTextExists()) {
        InputContext inputContext = myEditorComponent.getInputContext();
        if (inputContext != null) {
          inputContext.endComposition();
        }
      }

      if (event.getArea() == EditorMouseEventArea.LINE_MARKERS_AREA ||
          event.getArea() == EditorMouseEventArea.FOLDING_OUTLINE_AREA && !isInsideGutterWhitespaceArea(e)) {
        myDragOnGutterSelectionStartLine = EditorUtil.yPositionToLogicalLine(EditorImpl.this, e);
      }

      if (event.isConsumed()) return;

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

    private void runMouseClickedCommand(@NotNull MouseEvent e) {
      EditorMouseEvent event = createEditorMouseEvent(e);
      for (EditorMouseListener listener : myMouseListeners) {
        listener.mouseClicked(event);
        if (isReleased || event.isConsumed()) {
          return;
        }
      }
    }

    private void runMouseReleasedCommand(@NotNull MouseEvent e) {
      myMultiSelectionInProgress = false;
      myDragOnGutterSelectionStartLine = -1;
      myScrollingTimer.stop();

      if (e.isConsumed()) {
        return;
      }

      EditorMouseEvent event = createEditorMouseEvent(e);
      for (EditorMouseListener listener : myMouseListeners) {
        listener.mouseReleased(event);
        if (isReleased || event.isConsumed()) {
          return;
        }
      }

      invokePopupIfNeeded(event);
      if (event.isConsumed()) {
        return;
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
        if (isReleased) return;
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
        if (isReleased) return;
        if (event.isConsumed()) {
          e.consume();
          return;
        }
      }
    }

    private boolean processMousePressed(@NotNull MouseEvent e) {
      if (myMouseSelectionState != MOUSE_SELECTION_STATE_NONE &&
          System.currentTimeMillis() - myMouseSelectionChangeTimestamp > Registry.intValue(
            "editor.mouseSelectionStateResetTimeout")) {
        resetMouseSelectionState(e, null);
      }

      int x = e.getX();
      int y = e.getY();

      if (x < 0) x = 0;
      if (y < 0) y = 0;

      EditorMouseEventArea eventArea = getMouseEventArea(e);
      myMousePressArea = eventArea;
      if (eventArea == EditorMouseEventArea.FOLDING_OUTLINE_AREA) {
        FoldRegion range = myGutterComponent.findFoldingAnchorAt(x, y);
        if (range != null) {
          boolean expansion = !range.isExpanded();
          UIEventLogger.EditorFoldingIconClicked.log(expansion, e.isAltDown());

          int scrollShift = expansion ? 0 : visualLineToY(yToVisualLine(y)) - getScrollingModel().getVerticalScrollOffset();
          getFoldingModel().runBatchFoldingOperation(() -> {
            range.setExpanded(expansion);
            if (e.isAltDown()) {
              for (FoldRegion region : myFoldingModel.getAllFoldRegions()) {
                if (region.getStartOffset() >= range.getStartOffset() && region.getEndOffset() <= range.getEndOffset()) {
                  region.setExpanded(expansion);
                }
              }
            }
          }, true, false);
          if (!expansion) {
            int newY = visualLineToY(offsetToVisualLine(range.getStartOffset()));
            EditorUtil.runWithAnimationDisabled(EditorImpl.this, () -> myScrollingModel.scrollVertically(newY - scrollShift));
          }
          myGutterComponent.updateSize();
          validateMousePointer(e, null);
          e.consume();
          return false;
        }
      }

      if (e.getSource() == myGutterComponent) {
        if (!tweakSelectionIfNecessary(e)) {
          myGutterComponent.mousePressed(e);
        }
        if (e.isConsumed()) return false;
        x = 0;
      }

      Caret selectionCaret = null;
      int oldSelectionStart = mySelectionModel.getLeadSelectionOffset();

      int oldStart = mySelectionModel.getSelectionStart();
      int oldEnd = mySelectionModel.getSelectionEnd();

      LogicalPosition oldBlockStart = null;

      if (isColumnMode()) {
        @NotNull List<CaretState> caretsAndSelections = getCaretModel().getCaretsAndSelections();

        CaretState originalCaret = caretsAndSelections.get(0);
        oldBlockStart = Objects.equals(originalCaret.getCaretPosition(), originalCaret.getSelectionEnd())
                        ? originalCaret.getSelectionStart()
                        : originalCaret.getSelectionEnd();
      }

      boolean toggleCaret = e.getSource() != myGutterComponent && isToggleCaretEvent(e);
      boolean lastPressCreatedCaret = myLastPressCreatedCaret;
      if (e.getClickCount() == 1) {
        myLastPressCreatedCaret = false;
      }
      myLastPressWasAtBlockInlay =
        eventArea == EditorMouseEventArea.EDITING_AREA && hasBlockInlay(e.getPoint());
      boolean clickOnBreakpointOverLineNumbers =
        eventArea == EditorMouseEventArea.LINE_NUMBERS_AREA && ExperimentalUI.isNewUI() && EditorUtil.isBreakPointsOnLineNumbers();
      // Don't move the caret when the mouse is pressed in the gutter line markers area
      // (a place where breakpoints, 'override'/'implements' and other icons are drawn) or in the "annotations" area.
      //
      // For example, we don't want to change the caret position
      // when the user sets a new breakpoint by clicking at the 'line markers' area.
      // Also, don't move the caret when the context menu for an inlay is invoked.
      //
      // Also, don't move the caret too early if mouse pressed over line numbers area,
      // the user might just set a new breakpoint and not selecting lines.
      boolean moveCaret = (eventArea == EditorMouseEventArea.LINE_NUMBERS_AREA && !clickOnBreakpointOverLineNumbers) ||
                          isInsideGutterWhitespaceArea(e) ||
                          eventArea == EditorMouseEventArea.EDITING_AREA && !myLastPressWasAtBlockInlay;
      assert !(moveCaret && clickOnBreakpointOverLineNumbers);
      // We don't know which caret we want to work with yet
      VisualPosition visualPosition = getTargetPosition(x, y, true, null);
      if (clickOnBreakpointOverLineNumbers) {
        // However, we should save the information about the first position to correctly initiate drag,
        // if the user needs it instead of breakpoint setting.
        mySuppressedByBreakpointsLastPressPosition = visualPosition;
      }
      else if (moveCaret) {
        mySuppressedByBreakpointsLastPressPosition = null;
        LogicalPosition pos = visualToLogicalPosition(visualPosition);
        if (toggleCaret) {
          Caret caret = getCaretModel().getCaretAt(visualPosition);
          if (e.getClickCount() == 1) {
            if (caret == null) {
              myLastPressCreatedCaret = !EditorUtil.checkMaxCarets(EditorImpl.this) && getCaretModel().addCaret(visualPosition) != null;
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
          selectionCaret = eventArea == EditorMouseEventArea.EDITING_AREA &&
                           SwingUtilities.isRightMouseButton(e) &&
                           getCaretModel().getCaretCount() > 1
                           ? getSelectionCaret(pos) : null;
          if (selectionCaret == null) {
            getCaretModel().removeSecondaryCarets();
            getCaretModel().moveToVisualPosition(visualPosition);
          }
          else {
            selectionCaret.moveToVisualPosition(visualPosition);
          }
        }
      }

      if (e.isPopupTrigger()) return false;

      requestFocus();

      int caretOffset = getCaretModel().getOffset();

      int newStart = mySelectionModel.getSelectionStart();
      int newEnd = mySelectionModel.getSelectionEnd();

      Point p = new Point(x, y);
      myMouseSelectedRegion = myFoldingModel.getFoldingPlaceholderAt(p);
      myKeepSelectionOnMousePress = selectionCaret != null ||
                                    mySelectionModel.hasSelection() &&
                                    caretOffset >= mySelectionModel.getSelectionStart() &&
                                    caretOffset <= mySelectionModel.getSelectionEnd() &&
                                    !isPointAfterSelectionEnd(p) &&
                                    (SwingUtilities.isLeftMouseButton(e) && mySettings.isDndEnabled() ||
                                     SwingUtilities.isRightMouseButton(e));

      boolean isNavigation = oldStart == oldEnd && newStart == newEnd && oldStart != newStart;
      if (eventArea == EditorMouseEventArea.LINE_NUMBERS_AREA && e.getClickCount() == 1) {
        if (clickOnBreakpointOverLineNumbers) {
          //do nothing here and set/unset a breakpoint if possible in XLineBreakpointManager
          return false;
        }
        else {
          // Move the caret to the end of the selection, that is, the beginning of the next line.
          // This is more consistent with the caret placement on "Extend line selection" and on dragging through the line numbers area.
          selectLineAtCaret(true);
        }
        return isNavigation;
      }

      if (moveCaret) {
        if (e.isShiftDown() && !e.isControlDown() && !e.isAltDown() && !e.isMetaDown()) {
          if (oldBlockStart != null) {
            mySelectionModel.setBlockSelection(oldBlockStart, getCaretModel().getLogicalPosition());
          }
          else {
            int startToUse;
            if (getMouseSelectionState() != MOUSE_SELECTION_STATE_NONE) {
              if (caretOffset < mySavedSelectionStart) {
                startToUse = mySavedSelectionEnd;
              }
              else {
                startToUse = mySavedSelectionStart;
              }
            }
            else {
              startToUse = oldSelectionStart;
              if (mySelectionModel.isUnknownDirection() && caretOffset > startToUse) {
                startToUse = Math.min(oldStart, oldEnd);
              }
            }
            mySelectionModel.setSelection(startToUse, caretOffset);
          }
        }
        else {
          if (!myKeepSelectionOnMousePress && getSelectionModel().hasSelection() && !isCreateRectangularSelectionEvent(e) &&
              e.getClickCount() == 1) {
            if (!toggleCaret) {
              setMouseSelectionState(MOUSE_SELECTION_STATE_NONE);
              mySelectionModel.setSelection(caretOffset, caretOffset);
            }
          }
          else {
            if (e.getButton() == MouseEvent.BUTTON1
                && (eventArea == EditorMouseEventArea.EDITING_AREA || eventArea == EditorMouseEventArea.LINE_NUMBERS_AREA)
                && (!toggleCaret || lastPressCreatedCaret)
                && !(myMouseSelectedRegion instanceof CustomFoldRegion)) {
              switch (e.getClickCount()) {
                case 2:
                  selectWordAtCaret(mySettings.isMouseClickSelectionHonorsCamelWords() && mySettings.isCamelWords());
                  break;

                case 3:
                  if (eventArea == EditorMouseEventArea.EDITING_AREA &&
                      HONOR_CAMEL_HUMPS_ON_TRIPLE_CLICK && mySettings.isCamelWords()) {
                    // We want to differentiate between triple and quadruple clicks when 'select by camel humps' is on. The former
                    // is assumed to select 'hump' while the latter points to the whole word.
                    selectWordAtCaret(false);
                    break;
                  }
                  //noinspection fallthrough
                case 4:
                  // Triple and quadruple clicks on the line number reset the selection to a single line,
                  // except that in this case we keep the caret at the beginning of this line, not the next line.
                  selectLineAtCaret(false);
                  mySelectionModel.setUnknownDirection(true);
                  break;
              }
            }
          }
        }
      }

      return isNavigation;
    }

    private boolean isPointAfterSelectionEnd(@NotNull Point p) {
      VisualPosition selectionEndPosition = myCaretModel.getCurrentCaret().getSelectionEndPosition();
      Point selectionEnd = visualPositionToXY(selectionEndPosition);
      return p.y >= selectionEnd.y + getLineHeight() ||
             p.y >= selectionEnd.y && p.x > selectionEnd.x && xyToVisualPosition(p).column > selectionEndPosition.column;
    }

    private Caret getSelectionCaret(@NotNull LogicalPosition logicalPosition) {
      int offset = logicalPositionToOffset(logicalPosition);
      for (Caret caret : getCaretModel().getAllCarets()) {
        if (offset >= caret.getSelectionStart() && offset <= caret.getSelectionEnd()) {
          return caret;
        }
      }
      return null;
    }
  }

  private static boolean isColumnSelectionDragEvent(@NotNull MouseEvent e) {
    return isMouseActionEvent(e, IdeActions.ACTION_EDITOR_CREATE_RECTANGULAR_SELECTION_ON_MOUSE_DRAG);
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

  private static boolean isMouseActionEvent(@NotNull MouseEvent e, @NotNull String actionId) {
    KeymapManager keymapManager = KeymapManager.getInstance();
    if (keymapManager == null) return false;
    Keymap keymap = keymapManager.getActiveKeymap();
    MouseShortcut mouseShortcut = KeymapUtil.createMouseShortcut(e);
    List<String> mappedActions = keymap.getActionIds(mouseShortcut);
    if (!mappedActions.contains(actionId)) {
      return false;
    }

    if (mappedActions.size() < 2 || e.getID() == MouseEvent.MOUSE_DRAGGED /* 'normal' actions are not invoked on mouse drag */) {
      return true;
    }

    // todo do not update actions manually on EDT
    ActionManager actionManager = ActionManager.getInstance();
    for (String mappedActionId : mappedActions) {
      if (actionId.equals(mappedActionId)) continue;
      AnAction action = actionManager.getAction(mappedActionId);
      DataContext dataContext = DataManager.getInstance().getDataContext(e.getComponent());
      AnActionEvent actionEvent = AnActionEvent.createFromAnAction(action, e, ActionPlaces.MAIN_MENU, dataContext);
      ActionUtil.updateAction(action, actionEvent);
      if (actionEvent.getPresentation().isEnabled()) return false;
    }
    return true;
  }

  /**
   * Enter click-and-drag selection mode: select words only.
   */
  private void selectWordAtCaret(boolean honorCamelCase) {
    Caret caret = getCaretModel().getCurrentCaret();
    try (AccessToken ignore = SlowOperations.startSection(SlowOperations.ACTION_PERFORM)) {
      caret.selectWordAtCaret(honorCamelCase);
    }
    setMouseSelectionState(MOUSE_SELECTION_STATE_WORD_SELECTED);
    mySavedSelectionStart = caret.getSelectionStart();
    mySavedSelectionEnd = caret.getSelectionEnd();
    getCaretModel().moveToOffset(mySavedSelectionEnd);
  }

  /**
   * Enter click-and-drag selection mode: select lines only.
   */
  private void selectLineAtCaret(boolean moveToEnd) {
    Caret caret = getCaretModel().getCurrentCaret();
    caret.selectLineAtCaret();
    setMouseSelectionState(MOUSE_SELECTION_STATE_LINE_SELECTED);
    mySavedSelectionStart = caret.getSelectionStart();
    mySavedSelectionEnd = caret.getSelectionEnd();
    if (moveToEnd) {
      caret.moveToOffset(mySavedSelectionEnd);
    }
  }

  /**
   * Return true if given event should tweak editor selection.
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
   * @param e an event for mouse-click on gutter area
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

    int clickVisLine = yToVisualLine(e.getPoint().y);

    if (clickVisLine < startVisLine) {
      // Expand selection in the backward direction.
      int startOffset = visualPositionToOffset(new VisualPosition(clickVisLine, 0));
      getSelectionModel().setSelection(startOffset, endSelectionOffset);
      getCaretModel().moveToOffset(startOffset);
    }
    else if (clickVisLine > endVisLine) {
      // Expand selection in the forward direction.
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
        int startOffset = visualPositionToOffset(new VisualPosition(clickVisLine, 0));
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
    return myState.isUseAntialiasing();
  }

  public void setUseEditorAntialiasing(boolean value) {
    myState.setUseAntialiasing(value);
  }

  @ApiStatus.Internal
  public void addLayer(@NotNull JComponent component, int index) {
    component.putClientProperty(CUSTOM_LAYER_MARKER, true);
    myLayeredPane.add(component, Integer.valueOf(index));
  }

  private @NotNull EditorMouseEvent createEditorMouseEvent(@NotNull MouseEvent e) {
    Point point = e.getPoint();
    EditorMouseEventArea area = getMouseEventArea(e);
    boolean inEditingArea = area == EditorMouseEventArea.EDITING_AREA;
    EditorLocation location = new EditorLocation(this, inEditingArea ? point : new Point(0, point.y));
    VisualPosition visualPosition = location.getVisualPosition();
    LogicalPosition logicalPosition = location.getLogicalPosition();
    int offset = location.getOffset();
    int relX = point.x - myEditorComponent.getInsets().left;
    Inlay<?> inlayCandidate = inEditingArea ? myInlayModel.getElementAt(location, true) : null;
    Inlay<?> inlay = inlayCandidate == null ||
                     (inlayCandidate.getPlacement() == Inlay.Placement.BELOW_LINE ||
                      inlayCandidate.getPlacement() == Inlay.Placement.ABOVE_LINE) &&
                     inlayCandidate.getWidthInPixels() <= relX ? null : inlayCandidate;
    FoldRegion foldRegionCandidate = inEditingArea ? myFoldingModel.getFoldingPlaceholderAt(location, true) : null;
    FoldRegion foldRegion = foldRegionCandidate instanceof CustomFoldRegion &&
                            ((CustomFoldRegion)foldRegionCandidate).getWidthInPixels() <= relX ? null : foldRegionCandidate;
    GutterIconRenderer gutterIconRenderer = inEditingArea ? null : myGutterComponent.getGutterRenderer(point);
    boolean overText = inlayCandidate == null &&
                       (foldRegionCandidate == null || foldRegion != null) &&
                       offsetToLogicalPosition(offset).equals(logicalPosition);
    return new EditorMouseEvent(this, e, area, offset, logicalPosition, visualPosition,
                                overText, foldRegion, inlay, gutterIconRenderer);
  }

  private final class MyMouseMotionListener implements MouseMotionListener {
    @DirtyUI
    @Override
    public void mouseDragged(@NotNull MouseEvent e) {
      if (EVENT_LOG.isDebugEnabled()) {
        EVENT_LOG.debug(e.toString());
      }
      if (myDraggedRange != null || myGutterComponent.myDnDInProgress) {
        return; // on Mac we receive events even if drag-n-drop is in progress
      }
      if (myForcePushHappened) {
        return; // avoid selection creation on accidental mouse move/drag after force push
      }
      validateMousePointer(e, null);
      EditorMouseEvent event = createEditorMouseEvent(e);
      for (EditorMouseMotionListener listener : myMouseMotionListeners) {
        listener.mouseDragged(event);
        if (isReleased) return;
      }
      ((TransactionGuardImpl)TransactionGuard.getInstance()).performUserActivity(() -> runMouseDraggedCommand(e));
      if (event.getArea() == EditorMouseEventArea.LINE_MARKERS_AREA) {
        myGutterComponent.mouseDragged(e);
      }
    }

    @DirtyUI
    @Override
    public void mouseMoved(@NotNull MouseEvent e) {
      if (EVENT_LOG.isDebugEnabled()) {
        EVENT_LOG.debug(e.toString());
      }
      EditorMouseEvent event = createEditorMouseEvent(e);

      if (getMouseSelectionState() != MOUSE_SELECTION_STATE_NONE) {
        if (myMousePressedEvent != null && myMousePressedEvent.getComponent() == e.getComponent()) {
          Point lastPoint = myMousePressedEvent.getPoint();
          Point point = e.getPoint();
          int deadZone = Registry.intValue("editor.mouseSelectionStateResetDeadZone");
          if (Math.abs(lastPoint.x - point.x) >= deadZone || Math.abs(lastPoint.y - point.y) >= deadZone) {
            resetMouseSelectionState(e, event);
          }
        }
        else {
          validateMousePointer(e, event);
        }
      }
      else {
        validateMousePointer(e, event);
      }

      myMouseMovedEvent = e;

      if (e.getSource() == myGutterComponent) {
        myGutterComponent.mouseMoved(e);
      }

      for (EditorMouseMotionListener listener : myMouseMotionListeners) {
        listener.mouseMoved(event);
        if (isReleased) return;
      }
    }
  }

  static boolean handleDrop(@NotNull EditorImpl editor, @NotNull Transferable t, int dropAction) {
    EditorDropHandler dropHandler = editor.getDropHandler();

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
              Disposable painterListenersDisposable = Disposer.newDisposable("PainterListenersDisposable");
              Disposer.register(editor.getDisposable(), painterListenersDisposable);
              GutterIconDropAnimator painter = new GutterIconDropAnimator(
                new Point(
                  mouseLocationOnScreen.x - editorComponentLocationOnScreen.x,
                  mouseLocationOnScreen.y - editorComponentLocationOnScreen.y
                ), editor.myGutterComponent.getDragImage((GutterIconRenderer)attachedObject), painterListenersDisposable
              );
              IdeGlassPaneUtil.installPainter(
                editorComponent,
                painter, painterListenersDisposable
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

    int caretOffset = editor.getCaretModel().getOffset();
    if (editor.myDraggedRange != null
        && editor.myDraggedRange.getTextRange().contains(caretOffset)) {
      return false;
    }

    if (editor.myDraggedRange != null) {
      editor.getCaretModel().moveToOffset(editor.mySavedCaretOffsetForDNDUndoHack);
    }

    CommandProcessor.getInstance().executeCommand(editor.myProject, () -> {
      try {
        editor.getSelectionModel().removeSelection();

        int offset;
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

        AnAction pasteAction = ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_PASTE);
        if (pasteAction instanceof EditorAction) {
          EditorTextInsertHandler handler = ((EditorAction)pasteAction).getHandlerOfType(EditorTextInsertHandler.class);
          if (handler == null) {
            LOG.error("No suitable paste handler found");
          }
          else {
            try (AccessToken ignore = SlowOperations.startSection(SlowOperations.ACTION_PERFORM)) {
              handler.execute(editor, editor.getDataContext(), () -> t);
            }
          }
        }
        else {
          LOG.error("Couldn't find paste action: " + pasteAction);
        }

        TextRange range = editor.getUserData(LAST_PASTED_REGION);
        if (range != null) {
          editor.getCaretModel().moveToOffset(range.getStartOffset());
          editor.getSelectionModel().setSelection(range.getStartOffset(), range.getEndOffset());
        }

        if (DnDManager.getInstance() instanceof DnDManagerImpl manager) {
          manager.setLastDropHandler(editor.getContentComponent());
        }
      }
      catch (Exception exception) {
        LOG.error(exception);
      }
    }, EditorBundle.message("paste.command.name"), DND_COMMAND_GROUP, UndoConfirmationPolicy.DEFAULT, editor.getDocument());

    return true;
  }

  private final class EditorDocumentAdapter implements PrioritizedDocumentListener {
    @Override
    public void beforeDocumentChange(@NotNull DocumentEvent e) {
      beforeChangedUpdate(e);
    }

    @Override
    public void documentChanged(@NotNull DocumentEvent e) {
      changedUpdate(e);
    }

    @Override
    public void bulkUpdateStarting(@NotNull Document document) {
      bulkUpdateStarted();
    }

    @Override
    public void bulkUpdateFinished(@NotNull Document document) {
      EditorImpl.this.bulkUpdateFinished();
    }

    @Override
    public int getPriority() {
      return EditorDocumentPriorities.EDITOR_DOCUMENT_ADAPTER;
    }
  }

  @Override
  public @NotNull EditorGutter getGutter() {
    return getGutterComponentEx();
  }

  public boolean isInDistractionFreeMode() {
    return EditorUtil.isRealFileEditor(this)
           && (DistractionFreeModeController.isDistractionFreeModeEnabled() || isInPresentationMode());
  }

  boolean isInPresentationMode() {
    return UISettings.getInstance().getPresentationMode() && EditorUtil.isRealFileEditor(this);
  }

  @ApiStatus.Experimental
  @ApiStatus.Internal
  public @NotNull EditorState getState() {
    return myState;
  }

  private static @Nullable Object extractOldValueOrLog(@NotNull ObservableStateListener.PropertyChangeEvent event,
                                                       @SuppressWarnings("SameParameterValue") @Nullable Object fallbackValue) {
    Ref<@Nullable Object> oldValueRef = event.getOldValueRef();
    if (oldValueRef == null) {
      LOG.error("oldValueRef is expected not-null for the property: property name = " + event.getPropertyName());
      return fallbackValue;
    }
    return oldValueRef.get();
  }

  @Override
  public void putInfo(@NotNull Map<? super String, ? super String> info) {
    VisualPosition visual = getCaretModel().getVisualPosition();
    info.put("caret", visual.getLine() + ":" + visual.getColumn());
  }

  private void invokePopupIfNeeded(@NotNull EditorMouseEvent event) {
    if (myPopupHandlers.isEmpty()) return;

    if (event.getArea() == EditorMouseEventArea.EDITING_AREA && event.getMouseEvent().isPopupTrigger() && !event.isConsumed()) {
      if (ContainerUtil.all(myPopupHandlers, o -> o instanceof ContextMenuPopupHandler)) {
        ActionGroup group = getPopupActionGroup(event);
        if (group == null) return;
        if (group instanceof DefaultActionGroup o && o.getChildrenCount() == 0 &&
            group.getClass() == DefaultActionGroup.class) return;
        new ContextMenuPopupHandler.Simple(group).handlePopup(event);
      }
      else {
        String message = "Non-ContextMenuPopupHandler popup handler detected: " +
                         ContainerUtil.map(myPopupHandlers, o -> o.getClass().getName());
        if (ClientId.isCurrentlyUnderLocalId()) {
          LOG.warn(message);
        }
        else {
          LOG.error(message);
        }
        for (int i = myPopupHandlers.size() - 1; i >= 0; i--) {
          if (myPopupHandlers.get(i).handlePopup(event)) break;
        }
      }
    }
  }

  @Override
  public void codeStyleSettingsChanged(@NotNull CodeStyleSettingsChangeEvent event) {
    if (myProject != null) {
      VirtualFile eventFile = event.getVirtualFile();
      final var file = getVirtualFile();
      if (eventFile != null && !eventFile.equals(file)) {
        return;
      }
      int oldTabSize = EditorUtil.getTabSize(this);
      final var eventSettings = event.getSettings();
      final var cachedSettings = this.getUserData(CODE_STYLE_SETTINGS);
      if (cachedSettings != null && eventSettings == null) {
        // This event is not a result of settings computation finishing, but also we already have settings cached.
        // As editor settings are reinitialized, only the cached settings will be used.
        // But settings for the file may have changed, so we must request the settings properly.
        // If the settings indeed need to be recomputed, the request will trigger a background computation.
        // Once that computation is finished, this method will be called again with eventSettings != null.
        CodeStyle.getSettings(myProject, file);
      }
      if (eventSettings != null) {
        this.putUserData(CODE_STYLE_SETTINGS, eventSettings);
      }
      mySettings.reinitSettings();
      int newTabSize = EditorUtil.getTabSize(this);
      if (oldTabSize != newTabSize) {
        reinitSettings(false, true);
      }
      else {
        // cover the case of right margin update
        myEditorComponent.repaint();
      }
    }
  }

  public void bidiTextFound() {
    if (myProject != null && myVirtualFile != null && replace(CONTAINS_BIDI_TEXT, null, Boolean.TRUE)) {
      EditorNotifications.getInstance(myProject).updateNotifications(myVirtualFile);
    }
  }

  @TestOnly
  @ApiStatus.Internal
  public void validateState() {
    myView.validateState();
    mySoftWrapModel.validateState();
    myFoldingModel.validateState();
    myCaretModel.validateState();
    myInlayModel.validateState();
  }

  @Override
  public String toString() {
    return "EditorImpl[" + FileDocumentManager.getInstance().getFile(myDocument) + "]";
  }

  private final class DefaultPopupHandler extends ContextMenuPopupHandler {
    @Override
    public @Nullable ActionGroup getActionGroup(@NotNull EditorMouseEvent event) { //TODO ! renderer, collapsed-host
      String contextMenuGroupId = myState.getContextMenuGroupId();
      Inlay<?> inlay = event.getInlay();
      if (inlay != null) {
        ActionGroup group = inlay.getRenderer().getContextMenuGroup(inlay);
        if (group != null) return group;
        String inlayContextMenuGroupId = inlay.getRenderer().getContextMenuGroupId(inlay);
        if (inlayContextMenuGroupId != null) contextMenuGroupId = inlayContextMenuGroupId;
      }
      else {
        FoldRegion foldRegion = event.getCollapsedFoldRegion();
        if (foldRegion instanceof CustomFoldRegion customFoldRegion) {
          ActionGroup group = customFoldRegion.getRenderer().getContextMenuGroup(customFoldRegion);
          if (group != null) return group;
        }
      }
      return getGroupForId(contextMenuGroupId);
    }
  }

  @DirtyUI
  private final class MyScrollPane extends JBScrollPane {
    private JComponent myStatusComponent;

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
      WriteIntentReadAction.run((Runnable)() -> {
        if (isInDistractionFreeMode()) {
          // re-calc gutter extra size after editor size is set
          // & layout once again to avoid blinking
          myGutterComponent.updateSize(true, true);
        }
        super.layout();
      });
    }

    @Override
    protected void processMouseWheelEvent(@NotNull MouseWheelEvent e) {
      if (EVENT_LOG.isDebugEnabled()) {
        EVENT_LOG.debug(e.toString());
      }
      if (mySettings.isWheelFontChangeEnabled()) {
        if (EditorUtil.isChangeFontSize(e)) {
          boolean isWheelFontChangePersistent = EditorSettingsExternalizable.getInstance().isWheelFontChangePersistent()
                                                && !UISettings.getInstance().getPresentationMode();
          float shift = e.getWheelRotation();

          var strategy = getUserData(ChangeEditorFontSizeStrategy.KEY);
          if (strategy != null) {
            strategy.setFontSize(strategy.getFontSize() - shift);
            return;
          }

          float size = myScheme.getEditorFontSize2D();
          if (isWheelFontChangePersistent) {
            size = EditorColorsManager.getInstance().getGlobalScheme().getEditorFontSize2D();
          }

          size -= shift;
          if (size >= MIN_FONT_SIZE) {
            if (isWheelFontChangePersistent) {
              setFontSizeImpl(UISettingsUtils.getInstance().scaleFontSize(size),
                              convertPoint(this, e.getPoint(), getViewport()), false);
              adjustGlobalFontSize(size);
            }
            else {
              setFontSizeImpl(size, convertPoint(this, e.getPoint(), getViewport()), false);
            }
          }
          return;
        }
      }

      super.processMouseWheelEvent(e);
    }

    @Override
    public @NotNull JScrollBar createHorizontalScrollBar() {
      return new OpaqueAwareScrollBar(Adjustable.HORIZONTAL);
    }

    @Override
    public @NotNull JScrollBar createVerticalScrollBar() {
      return new MyScrollBar(Adjustable.VERTICAL);
    }

    @Override
    protected void setupCorners() {
      super.setupCorners();
      setBorder(new EditorTablessBorder(myProject, myHeaderPanel));
    }

    void placeStatusOnTopOfStickyPanel() {
      setStatusComponent(super.getStatusComponent());
    }

    @Override
    public JComponent getStatusComponent() {
      if (myStickyLinesManager == null) {
        return super.getStatusComponent();
      }
      else {
        return myStatusComponent;
      }
    }

    @Override
    public void setStatusComponent(JComponent statusComponent) {
      if (myStickyLinesManager == null) {
        super.setStatusComponent(statusComponent);
      }
      else {
        JComponent oldStatus = super.getStatusComponent();
        if (oldStatus != null) {
          super.setStatusComponent(null);
        }
        if (statusComponent != null) {
          myLayeredPane.add(statusComponent, STATUS_COMPONENT_LAYER);
        }
        else if (myStatusComponent != null) {
          myLayeredPane.remove(myStatusComponent);
        }
        firePropertyChange("statusComponent", myStatusComponent, statusComponent);
        myStatusComponent = statusComponent;
        revalidate();
        repaint();
      }
    }

    @Override
    protected Layout createLayout() {
      return new MyScrollPaneLayout();
    }
  }

  private static final class MyScrollPaneLayout extends JBScrollPane.Layout {
    void setVerticalScrollBar(JScrollBar vsb) {
      // allows layout manager to supervise vertical scroll bar placed on layered pane
      this.vsb = vsb;
    }
  }

  public void adjustGlobalFontSize(float size) {
    EditorColorsManager.getInstance().getGlobalScheme().setEditorFontSize(size);
    myScheme.resetEditorFontSize();
    EditorColorsManagerImpl.fireGlobalSchemeChange(null);
  }

  private final class PanelWithFloatingToolbar extends JBLayeredPane {
    @Override
    public void doLayout() {
      Component[] components = getComponents();
      Rectangle r = getBounds();
      for (Component c : components) {
        if (c instanceof JScrollPane || isCustomLayer(c)) {
          // Custom layers or main scroll panel (MyScrollPane) occupy whole area
          c.setBounds(0, 0, r.width, r.height);
        }
        else if (c instanceof EditorFloatingToolbar){
          // Floating toolbar: EditorFloatingToolbar
          Dimension d = c.getPreferredSize();
          int rightInsets = getVerticalScrollBar().getWidth() + (isMirrored() ? myGutterComponent.getWidth() : 0);
          c.setBounds(r.width - d.width - rightInsets - 20, 20, d.width, d.height);
        }
        else if (c instanceof JScrollBar) {
          // Vertical scroll bar: JScrollBar
          // do nothing here, MyScrollPaneLayout manages vsb
        }
        else if (!(c instanceof StickyLinesPanel)) {
          // Status component: NonOpaquePanel
          Dimension d = c.getPreferredSize();
          c.setBounds(r.width - d.width, 0, d.width, d.height);
          MyScrollBar vsb = myVerticalScrollBar;
          if (vsb.getY() != d.height) {
            vsb.setBounds(vsb.getX(), d.height, vsb.getWidth(), vsb.getHeight() - d.height);
          }
        }
      }
    }

    @Override
    public Dimension getPreferredSize() {
      return myScrollPane.getPreferredSize();
    }

    private static boolean isCustomLayer(Component c) {
      return c instanceof JComponent jComponent && jComponent.getClientProperty(CUSTOM_LAYER_MARKER) != null;
    }
  }

  @ApiStatus.Internal
  public MouseListener getMouseListener() {
    return myMouseListener;
  }

  @ApiStatus.Internal
  @Override
  public int getStickyLinesPanelHeight() {
    if (myStickyLinesManager == null || !getSettings().areStickyLinesShown()) {
      return 0;
    }
    return myStickyLinesManager.panelHeight();
  }

  /**
   * There is no point to show the editor fragment hint if the sticky panel shows the same line.
   * They also shouldn't be shown for non-local client ids, they will be handled there locally.
   */
  @ApiStatus.Internal
  public boolean shouldSuppressEditorFragmentHint(int hintStartLogicalLine) {
    if (!ClientId.isCurrentlyUnderLocalId()) {
      return true;
    }
    if (myStickyLinesManager != null) {
      return myStickyLinesManager.suppressHintForLine(hintStartLogicalLine);
    }
    return false;
  }

  /**
   * If true, the editor is in special "clean" mode when editor's content is being rendered on sticky lines panel.
   * This allows suppressing visual elements like caret row background, vertical indent lines, right margin line, etc.
   */
  @ApiStatus.Internal
  public boolean isStickyLinePainting() {
    return myIsStickyLinePainting;
  }

  @ApiStatus.Internal
  public void setStickyLinePainting(boolean stickyLinePainting) {
    myIsStickyLinePainting = stickyLinePainting;
  }

  @ApiStatus.Internal
  public void stickyLinesForLangChanged(@NotNull ObservableStateListener.PropertyChangeEvent event) {
    if (myStickyLinesManager != null) {
      Ref<Object> oldValRef = event.getOldValueRef();
      if (oldValRef != null &&
          oldValRef.get() instanceof Boolean oldVal &&
          event.getNewValue() instanceof Boolean newVal) {
        if (oldVal && !newVal) {
          /*
          Sticky model should be cleared in the following scenario:
            1) editor is initialized
            2) editor.language == null
            3) areStickyLinesShown == true
            4) sticky model is not empty from previous session
            5) sticky lines are shown on the panel
            6) updated editor.language != null
            7) areStickyLinesShown == false for the particular language
            8) sticky lines are hidden
            9) clearing sticky model to avoid showing lines on next editor opening
           */
          myStickyLinesManager.clearStickyModel();
        }
      }
    }
  }

  private @Nullable StickyLinesManager createStickyLinesPanel() {
    if (myProject != null && myKind == EditorKind.MAIN_EDITOR && !isMirrored()) {
      StickyLinesModel stickyModel = StickyLinesModel.getModel(myEditorFilteringMarkupModel.getDelegate());
      VisualStickyLines visualStickyLines = new VisualStickyLines(this, stickyModel);
      StickyLineShadowPainter shadowPainter = new StickyLineShadowPainter();
      StickyLinesPanel stickyPanel = new StickyLinesPanel(this, shadowPainter, visualStickyLines);
      StickyLinesManager stickyManager = new StickyLinesManager(
        this, stickyModel,
        stickyPanel,
        shadowPainter,
        visualStickyLines,
        myDisposable
      );
      myLayeredPane.add(stickyPanel, STICKY_PANEL_LAYER);
      myLayeredPane.add(myVerticalScrollBar, VERTICAL_SCROLLBAR_LAYER);
      ((MyScrollPaneLayout) myScrollPane.getLayout()).setVerticalScrollBar(myVerticalScrollBar);
      return stickyManager;
    }
    return null;
  }

  @ApiStatus.Experimental
  @ApiStatus.Internal
  public EditorView getView() {
    return myView;
  }

  @ApiStatus.Experimental
  @ApiStatus.Internal
  public @NotNull EditorModel getEditorModel() {
    if (myAdEditorModel != null) {
      return myAdEditorModel;
    }
    return myEditorModel;
  }
}
