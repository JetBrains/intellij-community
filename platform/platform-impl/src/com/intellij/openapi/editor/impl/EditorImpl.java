/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.diagnostic.Dumpable;
import com.intellij.diagnostic.LogMessageEx;
import com.intellij.ide.*;
import com.intellij.ide.dnd.DnDManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.impl.MouseGestureManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.*;
import com.intellij.openapi.editor.colors.*;
import com.intellij.openapi.editor.colors.impl.DelegateColorScheme;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.*;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.ex.util.EmptyEditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterClient;
import com.intellij.openapi.editor.impl.event.MarkupModelListener;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapAppliancePlaces;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapDrawingType;
import com.intellij.openapi.editor.impl.view.EditorView;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.fileEditor.impl.EditorsSplitters;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeGlassPane;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.impl.IdeBackgroundUtil;
import com.intellij.openapi.wm.impl.IdeGlassPaneImpl;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLayeredPane;
import com.intellij.ui.components.JBScrollBar;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.mac.MacGestureSupportForEditor;
import com.intellij.ui.paint.EffectPainter;
import com.intellij.util.*;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.text.CharArrayCharSequence;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.ui.*;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntIntHashMap;
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
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.*;
import java.awt.font.TextHitInfo;
import java.awt.im.InputMethodRequests;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.lang.reflect.Field;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.text.CharacterIterator;
import java.util.*;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class EditorImpl extends UserDataHolderBase implements EditorEx, HighlighterClient, Queryable, Dumpable {
  private static final boolean isOracleRetina = UIUtil.isRetina() /*&& SystemInfo.isOracleJvm*/;
  private static final int MIN_FONT_SIZE = 8;
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.impl.EditorImpl");
  private static final Key DND_COMMAND_KEY = Key.create("DndCommand");
  @NonNls
  public static final Object IGNORE_MOUSE_TRACKING = "ignore_mouse_tracking";
  private static final Key<JComponent> PERMANENT_HEADER = Key.create("PERMANENT_HEADER");
  public static final Key<Boolean> DO_DOCUMENT_UPDATE_TEST = Key.create("DoDocumentUpdateTest");
  public static final Key<Boolean> FORCED_SOFT_WRAPS = Key.create("forced.soft.wraps");
  public static final Key<Boolean> DISABLE_CARET_POSITION_KEEPING = Key.create("editor.disable.caret.position.keeping");
  private static final boolean HONOR_CAMEL_HUMPS_ON_TRIPLE_CLICK = Boolean.parseBoolean(System.getProperty("idea.honor.camel.humps.on.triple.click"));
  private static final Key<BufferedImage> BUFFER = Key.create("buffer");
  private static final Color CURSOR_FOREGROUND_LIGHT = Gray._255;
  private static final Color CURSOR_FOREGROUND_DARK = Gray._0;
  @NotNull private final DocumentEx myDocument;

  private final JPanel myPanel;
  @NotNull private final JScrollPane myScrollPane = new MyScrollPane();
  @NotNull private final EditorComponentImpl myEditorComponent;
  @NotNull private final EditorGutterComponentImpl myGutterComponent;
  private final TraceableDisposable myTraceableDisposable = new TraceableDisposable(true);
  private int myLinePaintersWidth;

  private static final Cursor EMPTY_CURSOR;

  static {
    ComplementaryFontsRegistry.getFontAbleToDisplay(' ', 0, Font.PLAIN, UIManager.getFont("Label.font").getFamily()); // load costly font info

    Cursor emptyCursor = null;
    try {
      emptyCursor = Toolkit.getDefaultToolkit().createCustomCursor(UIUtil.createImage(1, 1, BufferedImage.TYPE_INT_ARGB),
                                                                    new Point(),
                                                                    "Empty cursor");
    }
    catch (Exception e){
      LOG.warn("Couldn't create an empty cursor", e);
    }
    EMPTY_CURSOR = emptyCursor;
  }

  private final CommandProcessor myCommandProcessor;
  @NotNull private final MyScrollBar myVerticalScrollBar;

  private final List<EditorMouseListener> myMouseListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  @NotNull private final List<EditorMouseMotionListener> myMouseMotionListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  private int myCharHeight = -1;
  private int myLineHeight = -1;
  private int myDescent    = -1;

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
  private ArrowPainter myTabPainter;
  private boolean myIsViewer;
  @NotNull private final SelectionModelImpl mySelectionModel;
  @NotNull private final EditorMarkupModelImpl myMarkupModel;
  @NotNull private final EditorFilteringMarkupModelEx myDocumentMarkupModel;
  @NotNull private final MarkupModelListener myMarkupModelListener;

  @NotNull private final FoldingModelImpl myFoldingModel;
  @NotNull private final ScrollingModelImpl myScrollingModel;
  @NotNull private final CaretModelImpl myCaretModel;
  @NotNull private final SoftWrapModelImpl mySoftWrapModel;

  @NotNull private static final RepaintCursorCommand ourCaretBlinkingCommand = new RepaintCursorCommand();
  private DocumentBulkUpdateListener myBulkUpdateListener;

  @MouseSelectionState
  private int myMouseSelectionState;
  @Nullable private FoldRegion myMouseSelectedRegion;

  @MagicConstant(intValues = {MOUSE_SELECTION_STATE_NONE, MOUSE_SELECTION_STATE_LINE_SELECTED, MOUSE_SELECTION_STATE_WORD_SELECTED})
  private @interface MouseSelectionState {}
  private static final int MOUSE_SELECTION_STATE_NONE          = 0;
  private static final int MOUSE_SELECTION_STATE_WORD_SELECTED = 1;
  private static final int MOUSE_SELECTION_STATE_LINE_SELECTED = 2;

  private EditorHighlighter myHighlighter;
  private Disposable myHighlighterDisposable = Disposer.newDisposable();
  private final TextDrawingCallback myTextDrawingCallback = new MyTextDrawingCallback();

  @MagicConstant(intValues = {VERTICAL_SCROLLBAR_LEFT, VERTICAL_SCROLLBAR_RIGHT})
  private int         myScrollBarOrientation;
  private boolean     myMousePressedInsideSelection;
  private FontMetrics myPlainFontMetrics;
  private FontMetrics myBoldFontMetrics;
  private FontMetrics myItalicFontMetrics;
  private FontMetrics myBoldItalicFontMetrics;

  private static final int CACHED_CHARS_BUFFER_SIZE = 300;

  private final     ArrayList<CachedFontContent> myFontCache       = new ArrayList<>();
  @Nullable private FontInfo                     myCurrentFontType;

  private final EditorSizeContainer mySizeContainer = new EditorSizeContainer();

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
  private int myVirtualPageHeight;

  private final Alarm myMouseSelectionStateAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
  private Runnable myMouseSelectionStateResetRunnable;

  private boolean myEmbeddedIntoDialogWrapper;
  @Nullable private CachedFontContent myLastCache;
  private int myDragOnGutterSelectionStartLine = -1;
  private RangeMarker myDraggedRange;

  private boolean mySoftWrapsChanged;

  // transient fields used during painting
  private VisualPosition mySelectionStartPosition;
  private VisualPosition mySelectionEndPosition;

  private Color myLastBackgroundColor;
  private Point myLastBackgroundPosition;
  private int myLastBackgroundWidth;
  private static final boolean ourIsUnitTestMode = ApplicationManager.getApplication().isUnitTestMode();
  @NotNull private final JPanel myHeaderPanel;

  @Nullable private MouseEvent myInitialMouseEvent;
  private boolean myIgnoreMouseEventsConsecutiveToInitial;

  private EditorDropHandler myDropHandler;

  private Condition<RangeHighlighter> myHighlightingFilter;

  private char[] myPrefixText;
  private TextAttributes myPrefixAttributes;
  private int myPrefixWidthInPixels;
  @NotNull private final IndentsModel myIndentsModel;

  @Nullable
  private CharSequence myPlaceholderText;
  @Nullable private TextAttributes myPlaceholderAttributes;
  private int myLastPaintedPlaceholderWidth;
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
  public final boolean myUseNewRendering = Registry.is("editor.new.rendering");
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

  EditorImpl(@NotNull Document document, boolean viewer, @Nullable Project project) {
    assertIsDispatchThread();
    myProject = project;
    myDocument = (DocumentEx)document;
    if (myDocument instanceof DocumentImpl && !myUseNewRendering) {
      ((DocumentImpl)myDocument).requestTabTracking();
    }
    myScheme = createBoundColorSchemeDelegate(null);
    initTabPainter();
    myIsViewer = viewer;
    mySettings = new SettingsImpl(this, project);
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
    myScrollingModel = new ScrollingModelImpl(this);
    mySoftWrapModel = new SoftWrapModelImpl(this);
    if (!myUseNewRendering) mySizeContainer.reset();

    myCommandProcessor = CommandProcessor.getInstance();

    myImmediatePainter = new ImmediatePainter(this);

    if (myDocument instanceof DocumentImpl) {
      myBulkUpdateListener = new EditorDocumentBulkUpdateAdapter();
      ((DocumentImpl)myDocument).addInternalBulkModeListener(myBulkUpdateListener);
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

        if (myUseNewRendering && renderersChanged) {
          updateGutterSize();
        }
        
        boolean errorStripeNeedsRepaint = renderersChanged || highlighter.getErrorStripeMarkColor() != null;
        if (myUseNewRendering && myDocumentChangeInProgress) {
          // postpone repaint request, as folding model can be in inconsistent state and so coordinate 
          // conversions might give incorrect results
          myErrorStripeNeedsRepaint |= errorStripeNeedsRepaint;
          return;
        }
        
        int textLength = myDocument.getTextLength();

        clearTextWidthCache();

        int start = Math.min(Math.max(highlighter.getAffectedAreaStartOffset(), 0), textLength);
        int end = Math.min(Math.max(highlighter.getAffectedAreaEndOffset(), 0), textLength);

        int startLine = start == -1 ? 0 : myDocument.getLineNumber(start);
        int endLine = end == -1 ? myDocument.getLineCount() : myDocument.getLineNumber(end);
        if (myUseNewRendering && start != end && fontStyleOrColorChanged) {
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

        if (!myUseNewRendering && renderersChanged) {
          updateGutterSize();
        }

        updateCaretCursor();
      }
    };

    getFilteredDocumentMarkupModel().addMarkupModelListener(myCaretModel, myMarkupModelListener);
    getMarkupModel().addMarkupModelListener(myCaretModel, myMarkupModelListener);

    myDocument.addDocumentListener(myFoldingModel, myCaretModel);
    myDocument.addDocumentListener(myCaretModel, myCaretModel);
    myDocument.addDocumentListener(mySelectionModel, myCaretModel);

    myDocument.addDocumentListener(new EditorDocumentAdapter(), myCaretModel);
    myDocument.addDocumentListener(mySoftWrapModel, myCaretModel);

    myFoldingModel.addListener(mySoftWrapModel, myCaretModel);

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

      @Override
      public void softWrapsChanged() {
        mySoftWrapsChanged = true;
      }
    });

    if (!myUseNewRendering) {
      mySoftWrapModel.addVisualSizeChangeListener(new VisualSizeChangeListener() {
        @Override
        public void onLineWidthsChange(int startLine, int oldEndLine, int newEndLine, @NotNull TIntIntHashMap lineWidths) {
          mySizeContainer.update(startLine, newEndLine, oldEndLine);
          for (int i = startLine; i <= newEndLine; i++) {
            if (lineWidths.contains(i)) {
              int width = lineWidths.get(i);
              if (width >= 0) {
                mySizeContainer.updateLineWidthIfNecessary(i, width);
              }
            }
          }
        }
      });
    }

    EditorHighlighter highlighter = new EmptyEditorHighlighter(myScheme.getAttributes(HighlighterColors.TEXT));
    setHighlighter(highlighter);

    myEditorComponent = new EditorComponentImpl(this);
    myScrollPane.putClientProperty(JBScrollPane.BRIGHTNESS_FROM_VIEW, true);
    myVerticalScrollBar = (MyScrollBar)myScrollPane.getVerticalScrollBar();
    myVerticalScrollBar.setOpaque(false);
    myPanel = new JPanel();

    UIUtil.putClientProperty(
      myPanel, UIUtil.NOT_IN_HIERARCHY_COMPONENTS, new Iterable<JComponent>() {
        @NotNull
        @Override
        public Iterator<JComponent> iterator() {
          JComponent component = getPermanentHeaderComponent();
          if (component != null && !component.isValid()) {
            return Collections.singleton(component).iterator();
          }
          return ContainerUtil.emptyIterator();
        }
      });

    myHeaderPanel = new MyHeaderPanel();
    myGutterComponent = new EditorGutterComponentImpl(this);
    initComponent();

    if (myUseNewRendering) {
      myView = new EditorView(this);
      myView.reinitSettings();
    }
    else {
      myView = null;
    }

    if (UISettings.getInstance().PRESENTATION_MODE) {
      setFontSize(UISettings.getInstance().PRESENTATION_MODE_FONT_SIZE);
    }

    myGutterComponent.updateSize();
    Dimension preferredSize = getPreferredSize();
    myEditorComponent.setSize(preferredSize);

    updateCaretCursor();

    if (SystemInfo.isJavaVersionAtLeast("1.8") && SystemInfo.isMacIntel64 && SystemInfo.isJetbrainsJvm && Registry.is("ide.mac.forceTouch")) {
      new MacGestureSupportForEditor(getComponent());
    }

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
    return myUseNewRendering ? (int)myView.getPrefixTextWidthInPixels() : myPrefixWidthInPixels;
  }

  @Override
  public void setPrefixTextAndAttributes(@Nullable String prefixText, @Nullable TextAttributes attributes) {
    myPrefixText = prefixText == null ? null : prefixText.toCharArray();
    myPrefixAttributes = attributes;
    myPrefixWidthInPixels = 0;
    if (myPrefixText != null) {
      for (char c : myPrefixText) {
        LOG.assertTrue(myPrefixAttributes != null);
        if (myPrefixAttributes != null) {
          myPrefixWidthInPixels += EditorUtil.charWidth(c, myPrefixAttributes.getFontType(), this);
        }
      }
    }
    mySoftWrapModel.recalculate();
    if (myUseNewRendering) myView.setPrefix(prefixText, attributes);
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

  @Override
  @NotNull
  public EditorSettings getSettings() {
    assertReadAccess();
    return mySettings;
  }

  public void resetSizes() {
    if (myUseNewRendering) {
      myView.reset();
    }
    else {
      mySizeContainer.reset();
    }
  }

  @Override
  public void reinitSettings() {
    assertIsDispatchThread();
    clearSettingsCache();

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
    if (myUseNewRendering) {
      myView.reinitSettings();
    }
    else {
      mySizeContainer.reset();
    }
    myFoldingModel.rebuild();

    if (softWrapsUsedBefore ^ mySoftWrapModel.isSoftWrappingEnabled()) {
      if (!myUseNewRendering) {
        mySizeContainer.reset();
      }
      validateSize();
    }

    myHighlighter.setColorScheme(myScheme);
    myFoldingModel.refreshSettings();

    myGutterComponent.reinitSettings();
    myGutterComponent.revalidate();

    myEditorComponent.repaint();

    initTabPainter();
    updateCaretCursor();

    if (myInitialMouseEvent != null) {
      myIgnoreMouseEventsConsecutiveToInitial = true;
    }

    myCaretModel.updateVisualPosition();

    // make sure carets won't appear at invalid positions (e.g. on Tab width change)
    for (Caret caret : getCaretModel().getAllCarets()) {
      caret.moveToOffset(caret.getOffset());
    }
  }

  private void clearSettingsCache() {
    myCharHeight = -1;
    myLineHeight = -1;
    myDescent = -1;
    myPlainFontMetrics = null;

    clearTextWidthCache();
  }

  private void initTabPainter() {
    myTabPainter = new ArrowPainter(
      ColorProvider.byColorsScheme(myScheme, EditorColors.WHITESPACES_COLOR),
      new Computable.PredefinedValueComputable<>(EditorUtil.getSpaceWidth(Font.PLAIN, this)),
      () -> getCharHeight()
    );
  }

  /**
   * To be called when editor was not disposed while it should
   */
  public void throwEditorNotDisposedError(@NonNls @NotNull final String msg) {
    myTraceableDisposable.throwObjectNotDisposedError(msg);
  }

  /**
   * In case of "editor not disposed error" use {@link #throwEditorNotDisposedError(String)}
   */
  public void throwDisposalError(@NonNls @NotNull String msg) {
    myTraceableDisposable.throwDisposalError(msg);
  }

  public void release() {
    assertIsDispatchThread();
    if (isReleased) {
      throwDisposalError("Double release of editor:");
    }
    myTraceableDisposable.kill(null);

    isReleased = true;
    clearSettingsCache();
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
    if (myUseNewRendering) Disposer.dispose(myView);
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
    if (myDocument instanceof DocumentImpl && !myUseNewRendering) {
      ((DocumentImpl)myDocument).giveUpTabTracking();
    }
    Disposer.dispose(myDisposable);
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

   /*  Default mode till 1.4.0
    *   myScrollPane.getViewport().setScrollMode(JViewport.BLIT_SCROLL_MODE);
    */

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
              final MyScrollBar scrollBar = getVerticalScrollBar();
              c.setBounds(r.width - d.width - scrollBar.getWidth() - 20, 20, d.width, d.height);
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
        KeyboardInternationalizationNotificationManager.showNotification();
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

            if (myUseNewRendering) {
              getCaretModel().moveToVisualPosition(getTargetPosition(location.x, location.y, true));
            }
            else {
              getCaretModel().moveToLogicalPosition(getLogicalPositionForScreenPos(location.x, location.y, true));
            }
            getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
            requestFocus();
          }
        });
      }
    }
    catch (TooManyListenersException e) {
      LOG.error(e);
    }

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
      myScrollingModel.scrollToOffsets(visibleArea.x == 0 ? 0 : shiftedZoomCenterAbsolute.x - zoomCenterRelative.x, // stick to left border if it's visible
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

    ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
    DataContext dataContext = getDataContext();

    myImmediatePainter.paintCharacter(myEditorComponent.getGraphics(), c);

    actionManager.fireBeforeEditorTyping(c, dataContext);
    MacUIUtil.hideCursor();
    EditorActionManager.getInstance().getTypedAction().actionPerformed(this, c, dataContext);

    return true;
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
    myHighlighter = highlighter;
    myHighlighterDisposable = () -> document.removeDocumentListener(highlighter);
    Disposer.register(myDisposable, myHighlighterDisposable);
    highlighter.setEditor(this);
    highlighter.setText(document.getImmutableCharSequence());
    if (!(highlighter instanceof EmptyEditorHighlighter)) {
      EditorHighlighterCache.rememberEditorHighlighterForCachesOptimization(document, highlighter);
    }

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
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        removePropertyChangeListener(listener);
      }
    });
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
    if (myUseNewRendering) return myView.yToVisualLine(y);
    assert y >= 0 : y;
    return y / getLineHeight();
  }

  @Override
  @NotNull
  public VisualPosition xyToVisualPosition(@NotNull Point p) {
    if (myUseNewRendering) return myView.xyToVisualPosition(p);
    int line = yToVisibleLine(Math.max(p.y, 0));
    int px = p.x;
    if (line == 0 && myPrefixText != null) {
      px -= myPrefixWidthInPixels;
    }
    if (px < 0) {
      px = 0;
    }

    int textLength = myDocument.getTextLength();
    LogicalPosition logicalPosition = visualToLogicalPosition(new VisualPosition(line, 0));
    int offset = logicalPositionToOffset(logicalPosition);
    int plainSpaceSize = EditorUtil.getSpaceWidth(Font.PLAIN, this);

    if (offset >= textLength) return new VisualPosition(line, EditorUtil.columnsNumber(px, plainSpaceSize));

    // There is a possible case that starting logical line is split by soft-wraps and it's part after the split should be drawn.
    // We mark that we're under such circumstances then.
    boolean activeSoftWrapProcessed = logicalPosition.softWrapLinesOnCurrentLogicalLine <= 0;

    CharSequence text = myDocument.getImmutableCharSequence();

    LogicalPosition endLogicalPosition = visualToLogicalPosition(new VisualPosition(line + 1, 0));
    int endOffset = logicalPositionToOffset(endLogicalPosition);

    if (offset > endOffset) {
      LogMessageEx.error(LOG, "Detected invalid (x; y)->VisualPosition processing", String.format(
        "Given point: %s, mapped to visual line %d. Visual(%d; %d) is mapped to "
        + "logical position '%s' which is mapped to offset %d (start offset). Visual(%d; %d) is mapped to logical '%s' which is mapped "
        + "to offset %d (end offset). State: %s",
        p, line, line, 0, logicalPosition, offset, line + 1, 0, endLogicalPosition, endOffset, dumpState()
      ));
      return new VisualPosition(line, EditorUtil.columnsNumber(px, plainSpaceSize));
    }
    IterationState state = new IterationState(this, offset, endOffset, false);

    int fontType = state.getMergedAttributes().getFontType();

    int x = 0;
    int charWidth;
    boolean onSoftWrapDrawing = false;
    char c = ' ';
    int prevX = 0;
    int column = 0;
    outer:
    while (true) {
      charWidth = -1;
      if (offset >= textLength) {
        break;
      }

      if (offset >= state.getEndOffset()) {
        state.advance();
        fontType = state.getMergedAttributes().getFontType();
      }

      SoftWrap softWrap = mySoftWrapModel.getSoftWrap(offset);
      if (softWrap != null) {
        if (activeSoftWrapProcessed) {
          prevX = x;
          charWidth = getSoftWrapModel().getMinDrawingWidthInPixels(SoftWrapDrawingType.BEFORE_SOFT_WRAP_LINE_FEED);
          x += charWidth;
          if (x >= px) {
            onSoftWrapDrawing = true;
          }
          else {
            column++;
          }
          break;
        }
        else {
          CharSequence softWrapText = softWrap.getText();
          for (int i = 1/*Assuming line feed is located at the first position*/; i < softWrapText.length(); i++) {
            c = softWrapText.charAt(i);
            prevX = x;
            charWidth = charToVisibleWidth(c, fontType, x);
            x += charWidth;
            if (x >= px) {
              break outer;
            }
            column += EditorUtil.columnsNumber(c, x, prevX, plainSpaceSize);
          }

          // Process 'after soft wrap' sign.
          prevX = x;
          charWidth = mySoftWrapModel.getMinDrawingWidthInPixels(SoftWrapDrawingType.AFTER_SOFT_WRAP);
          x += charWidth;
          if (x >= px) {
            onSoftWrapDrawing = true;
            break;
          }
          column++;
          activeSoftWrapProcessed = true;
        }
      }
      FoldRegion region = state.getCurrentFold();
      if (region != null) {
        char[] placeholder = region.getPlaceholderText().toCharArray();
        for (char aPlaceholder : placeholder) {
          c = aPlaceholder;
          x += EditorUtil.charWidth(c, fontType, this);
          if (x >= px) {
            break outer;
          }
          column++;
        }
        offset = region.getEndOffset();
      }
      else {
        prevX = x;
        c = text.charAt(offset);
        if (c == '\n') {
          break;
        }
        charWidth = charToVisibleWidth(c, fontType, x);
        x += charWidth;

        if (x >= px) {
          break;
        }
        column += EditorUtil.columnsNumber(c, x, prevX, plainSpaceSize);

        offset++;
      }
    }

    if (charWidth < 0) {
      charWidth = EditorUtil.charWidth(c, fontType, this);
    }

    if (charWidth < 0) {
      charWidth = plainSpaceSize;
    }

    if (x >= px && c == '\t' && !onSoftWrapDrawing) {
      if (mySettings.isCaretInsideTabs()) {
        column += (px - prevX) / plainSpaceSize;
        if ((px - prevX) % plainSpaceSize > plainSpaceSize / 2) column++;
      }
      else if ((x - px) * 2 < x - prevX) {
        column += EditorUtil.columnsNumber(c, x, prevX, plainSpaceSize);
      }
    }
    else {
      if (x >= px) {
        if (c != '\n' && (x - px) * 2 < charWidth) column++;
      }
      else {
        int diff = px - x;
        column += diff / plainSpaceSize;
        if (diff % plainSpaceSize * 2 >= plainSpaceSize) {
          column++;
        }
      }
    }

    return new VisualPosition(line, column);
  }

  /**
   * Allows to answer how much width requires given char to be represented on a screen.
   *
   * @param c        target character
   * @param fontType font type to use for representation of the given character
   * @param currentX current <code>'x'</code> position on a line where given character should be displayed
   * @return width required to represent given char with the given settings on a screen;
   *         <code>'0'</code> if given char is a line break
   */
  private int charToVisibleWidth(char c, @JdkConstants.FontStyle int fontType, int currentX) {
    if (c == '\n') {
      return 0;
    }

    if (c == '\t') {
      return EditorUtil.nextTabStop(currentX, this) - currentX;
    }
    return EditorUtil.charWidth(c, fontType, this);
  }

  @NotNull
  public Point offsetToXY(int offset, boolean leanTowardsLargerOffsets) {
    return myUseNewRendering ? myView.offsetToXY(offset, leanTowardsLargerOffsets, false) : 
           visualPositionToXY(offsetToVisualPosition(offset, leanTowardsLargerOffsets, false));
  }
  
  @Override
  @NotNull
  public VisualPosition offsetToVisualPosition(int offset) {
    return offsetToVisualPosition(offset, false, false);
  }

  @Override
  @NotNull
  public VisualPosition offsetToVisualPosition(int offset, boolean leanForward, boolean beforeSoftWrap) {
    if (myUseNewRendering) return myView.offsetToVisualPosition(offset, leanForward, beforeSoftWrap);
    return logicalToVisualPosition(offsetToLogicalPosition(offset));
  }

  @Override
  @NotNull
  public LogicalPosition offsetToLogicalPosition(int offset) {
    return offsetToLogicalPosition(offset, true);
  }

  @NotNull
  public LogicalPosition offsetToLogicalPosition(int offset, boolean softWrapAware) {
    if (myUseNewRendering) return myView.offsetToLogicalPosition(offset);
    if (softWrapAware) {
      return mySoftWrapModel.offsetToLogicalPosition(offset);
    }
    int line = offsetToLogicalLine(offset);
    int column = calcColumnNumber(offset, line, false, myDocument.getImmutableCharSequence());
    return new LogicalPosition(line, column);
  }

  @TestOnly
  public void setCaretActive() {
    synchronized (ourCaretBlinkingCommand) {
      ourCaretBlinkingCommand.myEditor = this;
    }
  }

  // optimization: do not do column calculations here since we are interested in line number only
  public int offsetToVisualLine(int offset) {
    if (myUseNewRendering) return myView.offsetToVisualLine(offset, false);
    int textLength = getDocument().getTextLength();
    if (offset >= textLength) {
      return Math.max(0, getVisibleLineCount() - 1); // lines are 0 based
    }
    int line = offsetToLogicalLine(offset);
    int lineStartOffset = line >= myDocument.getLineCount() ? myDocument.getTextLength() : myDocument.getLineStartOffset(line);

    int result = logicalToVisualLine(line);

    // There is a possible case that logical line that contains target offset is soft-wrapped (represented in more than one visual
    // line). Hence, we need to perform necessary adjustments to the visual line that is used to show logical line start if necessary.
    int i = getSoftWrapModel().getSoftWrapIndex(lineStartOffset);
    if (i < 0) {
      i = -i - 1;
    }
    List<? extends SoftWrap> softWraps = getSoftWrapModel().getRegisteredSoftWraps();
    for (; i < softWraps.size(); i++) {
      SoftWrap softWrap = softWraps.get(i);
      if (softWrap.getStart() > offset) {
        break;
      }
      result++; // Assuming that every soft wrap contains only one virtual line feed symbol
    }
    return result;
  }
  
  public int visualLineStartOffset(int visualLine) {
    if (myUseNewRendering) return myView.visualLineToOffset(visualLine);
    throw new UnsupportedOperationException();
  }

  private int logicalToVisualLine(int line) {
    assertReadAccess();
    return logicalToVisualPosition(new LogicalPosition(line, 0)).line;
  }

  @Override
  @NotNull
  public LogicalPosition xyToLogicalPosition(@NotNull Point p) {
    Point pp = p.x >= 0 && p.y >= 0 ? p : new Point(Math.max(p.x, 0), Math.max(p.y, 0));
    return visualToLogicalPosition(xyToVisualPosition(pp));
  }

  private int logicalLineToY(int line) {
    int visualLine = myUseNewRendering && line < myDocument.getLineCount() ? offsetToVisualLine(myDocument.getLineStartOffset(line)) : 
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
    if (myUseNewRendering) return myView.visualPositionToXY(visible); 
    int y = visibleLineToY(visible.line);
    LogicalPosition logical = visualToLogicalPosition(new VisualPosition(visible.line, 0));
    int logLine = logical.line;

    int lineStartOffset = -1;
    int reserved = 0;
    int column = visible.column;

    if (logical.softWrapLinesOnCurrentLogicalLine > 0) {
      int linesToSkip = logical.softWrapLinesOnCurrentLogicalLine;
      List<? extends SoftWrap> softWraps = getSoftWrapModel().getSoftWrapsForLine(logLine);
      for (SoftWrap softWrap : softWraps) {
        if (myFoldingModel.isOffsetCollapsed(softWrap.getStart()) && myFoldingModel.isOffsetCollapsed(softWrap.getStart() - 1)) {
          continue;
        }
        linesToSkip--; // Assuming here that every soft wrap has exactly one line feed
        if (linesToSkip > 0) {
          continue;
        }
        lineStartOffset = softWrap.getStart();
        int widthInColumns = softWrap.getIndentInColumns();
        int widthInPixels = softWrap.getIndentInPixels();
        if (widthInColumns <= column) {
          column -= widthInColumns;
          reserved = widthInPixels;
        }
        else {
          char[] softWrapChars = softWrap.getChars();
          int i = CharArrayUtil.lastIndexOf(softWrapChars, '\n', 0, softWrapChars.length);
          int start = 0;
          if (i >= 0) {
            start = i + 1;
          }
          return new Point(EditorUtil.textWidth(this, softWrap.getText(), start, column + 1, Font.PLAIN, 0), y);
        }
        break;
      }
    }

    if (logLine < 0) {
      lineStartOffset = 0;
    }
    else if (lineStartOffset < 0) {
      lineStartOffset = logLine >= myDocument.getLineCount() ? myDocument.getTextLength() : myDocument.getLineStartOffset(logLine);
    }

    int x = getTabbedTextWidth(lineStartOffset, column, reserved);
    return new Point(x, y);
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

  private int calcEndOffset(int startOffset, int visualColumn) {
    FoldRegion[] regions = myFoldingModel.fetchTopLevel();
    if (regions == null) {
      return startOffset + visualColumn;
    }

    int low = 0;
    int high = regions.length - 1;
    int i = -1;

    while (low <= high) {
      int mid = low + high >>> 1;
      FoldRegion midVal = regions[mid];

      if (midVal.getStartOffset() <= startOffset && midVal.getEndOffset() > startOffset) {
        i = mid;
        break;
      }

      if (midVal.getStartOffset() < startOffset)
        low = mid + 1;
      else if (midVal.getStartOffset() > startOffset)
        high = mid - 1;
    }
    if (i < 0) {
      i = low;
    }

    int result = startOffset;
    int columnsToProcess = visualColumn;
    for (; i < regions.length; i++) {
      FoldRegion region = regions[i];

      // Process text between the last fold region end and current fold region start.
      int nonFoldTextColumnsNumber = region.getStartOffset() - result;
      if (nonFoldTextColumnsNumber >= columnsToProcess) {
        return result + columnsToProcess;
      }
      columnsToProcess -= nonFoldTextColumnsNumber;

      // Process fold region.
      int placeHolderLength = region.getPlaceholderText().length();
      if (placeHolderLength >= columnsToProcess) {
        return region.getEndOffset();
      }
      result = region.getEndOffset();
      columnsToProcess -= placeHolderLength;
    }
    return result + columnsToProcess;
  }

  public int findNearestDirectionBoundary(int offset, boolean lookForward) {
    return myUseNewRendering ? myView.findNearestDirectionBoundary(offset, lookForward) : -1;
  }

  // TODO: tabbed text width is additive, it should be possible to have buckets, containing arguments / values to start with
  private final int[] myLastStartOffsets = new int[2];
  private final int[] myLastTargetColumns = new int[myLastStartOffsets.length];
  private final int[] myLastXOffsets = new int[myLastStartOffsets.length];
  private final int[] myLastXs = new int[myLastStartOffsets.length];
  private int myCurrentCachePosition;
  private int myLastCacheHits;
  private int myTotalRequests; // todo remove

  private int getTabbedTextWidth(int startOffset, int targetColumn, int xOffset) {
    int x = xOffset;
    if (startOffset == 0 && myPrefixText != null) {
      x += myPrefixWidthInPixels;
    }
    if (targetColumn <= 0) return x;

    ++myTotalRequests;
    for(int i = 0; i < myLastStartOffsets.length; ++i) {
      if (startOffset == myLastStartOffsets[i] && targetColumn == myLastTargetColumns[i] && xOffset == myLastXOffsets[i]) {
        ++myLastCacheHits;
        if ((myLastCacheHits & 0xFFF) == 0) {    // todo remove
          PsiFile file = myProject != null ? PsiDocumentManager.getInstance(myProject).getCachedPsiFile(myDocument):null;
          LOG.info("Cache hits:" + myLastCacheHits + ", total requests:" +
                             myTotalRequests + "," + (file != null ? file.getViewProvider().getVirtualFile():null));
        }
        return myLastXs[i];
      }
    }

    int offset = startOffset;
    CharSequence text = myDocument.getImmutableCharSequence();
    int textLength = myDocument.getTextLength();

    // We need to calculate max offset to provide to the IterationState here based on the given start offset and target
    // visual column. The problem is there is a possible case that there is a collapsed fold region at the target interval,
    // so, we can't just use 'startOffset + targetColumn' as a max end offset.
    IterationState state = new IterationState(this, startOffset, calcEndOffset(startOffset, targetColumn), false);
    int fontType = state.getMergedAttributes().getFontType();
    int plainSpaceSize = EditorUtil.getSpaceWidth(Font.PLAIN, this);

    int column = 0;
    outer:
    while (column < targetColumn) {
      if (offset >= textLength) break;

      if (offset >= state.getEndOffset()) {
        state.advance();
        fontType = state.getMergedAttributes().getFontType();
      }
      // We need to consider 'before soft wrap drawing'.
      SoftWrap softWrap = getSoftWrapModel().getSoftWrap(offset);
      if (softWrap != null && offset > startOffset) {
        column++;
        x += getSoftWrapModel().getMinDrawingWidthInPixels(SoftWrapDrawingType.BEFORE_SOFT_WRAP_LINE_FEED);
        // Assuming that first soft wrap symbol is line feed or all soft wrap symbols before the first line feed are spaces.
        break;
      }

      FoldRegion region = state.getCurrentFold();

      if (region != null) {
        char[] placeholder = region.getPlaceholderText().toCharArray();
        for (char aPlaceholder : placeholder) {
          x += EditorUtil.charWidth(aPlaceholder, fontType, this);
          column++;
          if (column >= targetColumn) break outer;
        }
        offset = region.getEndOffset();
      }
      else {
        char c = text.charAt(offset);
        if (c == '\n') {
          break;
        }
        if (c == '\t') {
          int prevX = x;
          x = EditorUtil.nextTabStop(x, this);
          int columnDiff = (x - prevX) / plainSpaceSize;
          if ((x - prevX) % plainSpaceSize > 0) {
            // There is a possible case that tabulation symbol takes more than one visual column to represent and it's shown at
            // soft-wrapped line. Soft wrap sign width may be not divisible by space size, hence, part of tabulation symbol represented
            // as a separate visual column may take less space than space width.
            columnDiff++;
          }
          column += columnDiff;
        }
        else {
          x += EditorUtil.charWidth(c, fontType, this);
          column++;
        }
        offset++;
      }
    }

    if (column != targetColumn) {
      x += EditorUtil.getSpaceWidth(fontType, this) * (targetColumn - column);
    }

    myLastTargetColumns[myCurrentCachePosition] = targetColumn;
    myLastStartOffsets[myCurrentCachePosition] = startOffset;
    myLastXs[myCurrentCachePosition] = x;
    myLastXOffsets[myCurrentCachePosition] = xOffset;
    myCurrentCachePosition = (myCurrentCachePosition + 1) % myLastStartOffsets.length;

    return x;
  }

  private void clearTextWidthCache() {
    for(int i = 0; i < myLastStartOffsets.length; ++i) {
      myLastTargetColumns[i] = -1;
      myLastStartOffsets[i] = - 1;
      myLastXs[i] = -1;
      myLastXOffsets[i] = -1;
    }
  }

  public int visibleLineToY(int line) {
    if (myUseNewRendering) return myView.visualLineToY(line);
    if (line < 0) throw new IndexOutOfBoundsException("Wrong line: " + line);
    return line * getLineHeight();
  }

  @Override
  public void repaint(int startOffset, int endOffset) {
    repaint(startOffset, endOffset, true);
  }
  
  void repaint(int startOffset, int endOffset, boolean invalidateTextLayout) {
    if (myDocument.isInBulkUpdate()) {
      return;
    }
    if (myUseNewRendering) {
      assertIsDispatchThread();
      endOffset = Math.min(endOffset, myDocument.getTextLength());

      if (invalidateTextLayout) {
        myView.invalidateRange(startOffset, endOffset);
      }

      if (!isShowing()) {
        return;
      }
    }
    else {
      if (!isShowing()) {
        return;
      }

      endOffset = Math.min(endOffset, myDocument.getTextLength());
      assertIsDispatchThread();
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
   * Asks to repaint all logical lines from the given <code>[start; end]</code> range.
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
    if (myUseNewRendering) {
      myView.getPreferredSize(); // make sure size is calculated (in case it will be required while bulk mode is active)
    }

    myScrollingModel.onBulkDocumentUpdateStarted();
    
    saveCaretRelativePosition();

    myCaretModel.onBulkDocumentUpdateStarted();
    mySoftWrapModel.onBulkDocumentUpdateStarted();
    myFoldingModel.onBulkDocumentUpdateStarted();
  }

  private void bulkUpdateFinished() {
    myFoldingModel.onBulkDocumentUpdateFinished();
    mySoftWrapModel.onBulkDocumentUpdateFinished();
    if (myUseNewRendering) {
      myView.reset();
    }
    myCaretModel.onBulkDocumentUpdateFinished();

    clearTextWidthCache();

    setMouseSelectionState(MOUSE_SELECTION_STATE_NONE);

    if (!myUseNewRendering) {
      mySizeContainer.reset();
    }
    validateSize();

    updateGutterSize();
    repaintToScreenBottom(0);
    updateCaretCursor();

    if (!Boolean.TRUE.equals(getUserData(DISABLE_CARET_POSITION_KEEPING))) {
      restoreCaretRelativePosition();
    }
  }

  private void beforeChangedUpdate(@NotNull DocumentEvent e) {
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

    // We assume that size container is already notified with the visual line widths during soft wraps processing
    if (!mySoftWrapModel.isSoftWrappingEnabled() && !myUseNewRendering) {
      mySizeContainer.beforeChange(e);
    }

    myImmediatePainter.beforeUpdate(e);
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

    myImmediatePainter.paintUpdate(myEditorComponent.getGraphics(), e);

    if (myErrorStripeNeedsRepaint) {
      myMarkupModel.repaint(e.getOffset(), e.getOffset() + e.getNewLength());
      myErrorStripeNeedsRepaint = false;
    }

    clearTextWidthCache();
    setMouseSelectionState(MOUSE_SELECTION_STATE_NONE);

    // We assume that size container is already notified with the visual line widths during soft wraps processing
    if (!mySoftWrapModel.isSoftWrappingEnabled() && !myUseNewRendering) {
      mySizeContainer.changedUpdate(e);
    }
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

    if (EMPTY_CURSOR != null) {
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

  public boolean hasTabs() {
    return myUseNewRendering || !(myDocument instanceof DocumentImpl) || ((DocumentImpl)myDocument).mightContainTabs();
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
      SwingUtilities.invokeLater(() -> {
        try {
          if (!isDisposed()) {
            myGutterComponent.updateSize();
          }
        }
        finally {
          updatingSize = false;
        }
      });
    }
  }

  void validateSize() {
    if (myUseNewRendering && isReleased) return;
    
    Dimension dim = getPreferredSize();

    if (!dim.equals(myPreferredSize) && !myDocument.isInBulkUpdate()) {
      dim = mySizeAdjustmentStrategy.adjust(dim, myPreferredSize, this);
      myPreferredSize = dim;

      updateGutterSize();

      myEditorComponent.setSize(dim);
      myEditorComponent.fireResized();

      myMarkupModel.recalcEditorDimensions();
      myMarkupModel.repaint(-1, -1);
    }
  }

  void recalculateSizeAndRepaint() {
    if (!myUseNewRendering) mySizeContainer.reset();
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
    final Runnable stopDumbRunnable = () -> stopDumb();
    ApplicationManager.getApplication().invokeLater(stopDumbRunnable, ModalityState.current());
  }

  void resetPaintersWidth() {
    myLinePaintersWidth = 0;
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
    BufferedImage image = UIUtil.createImage(rect.width, rect.height, BufferedImage.TYPE_INT_RGB);
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

    if (myUseNewRendering) {
      myView.paint(g);
    }
    else {
      VisualPosition clipStartVisualPos = xyToVisualPosition(new Point(0, clip.y));
      LogicalPosition clipStartPosition = visualToLogicalPosition(clipStartVisualPos);
      int clipStartOffset = logicalPositionToOffset(clipStartPosition);
      LogicalPosition clipEndPosition = xyToLogicalPosition(new Point(0, clip.y + clip.height + getLineHeight()));
      int clipEndOffset = logicalPositionToOffset(clipEndPosition);
      paintBackgrounds(g, clip, clipStartPosition, clipStartVisualPos, clipStartOffset, clipEndOffset);
      if (paintPlaceholderText(g, clip)) {
        paintCaretCursor(g);
        return;
      }

      paintRightMargin(g, clip);
      paintCustomRenderers(g, clipStartOffset, clipEndOffset);
      paintLineMarkersSeparators(g, clip, myDocumentMarkupModel, clipStartOffset, clipEndOffset);
      paintLineMarkersSeparators(g, clip, myMarkupModel, clipStartOffset, clipEndOffset);
      paintText(g, clip, clipStartPosition, clipStartOffset, clipEndOffset);
      paintSegmentHighlightersBorderAndAfterEndOfLine(g, clip, clipStartOffset, clipEndOffset, myDocumentMarkupModel);
      BorderEffect borderEffect = new BorderEffect(this, g, clipStartOffset, clipEndOffset);
      borderEffect.paintHighlighters(getHighlighter());
      borderEffect.paintHighlighters(myDocumentMarkupModel);
      borderEffect.paintHighlighters(myMarkupModel);

      paintCaretCursor(g);

      paintComposedTextDecoration(g);
    }
  }

  Color getDisposedBackground() {
    return new JBColor(new Color(128, 255, 128), new Color(128, 255, 128));
  }

  private static final char IDEOGRAPHIC_SPACE = '\u3000'; // http://www.marathon-studios.com/unicode/U3000/Ideographic_Space
  private static final String WHITESPACE_CHARS = " \t" + IDEOGRAPHIC_SPACE;

  private void paintCustomRenderers(@NotNull final Graphics2D g, final int clipStartOffset, final int clipEndOffset) {
    myMarkupModel.processRangeHighlightersOverlappingWith(clipStartOffset, clipEndOffset, highlighter -> {
      final CustomHighlighterRenderer customRenderer = highlighter.getCustomRenderer();
      if (customRenderer != null && clipStartOffset < highlighter.getEndOffset() && highlighter.getStartOffset() < clipEndOffset) {
        customRenderer.paint(this, highlighter, g);
      }
      return true;
    });
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

  private void paintComposedTextDecoration(@NotNull Graphics2D g) {
    TextRange composedTextRange = getComposedTextRange();
    if (composedTextRange != null) {
      VisualPosition visStart = offsetToVisualPosition(Math.min(composedTextRange.getStartOffset(), myDocument.getTextLength()));
      int y = visibleLineToY(visStart.line) + getAscent() + 1;
      Point p1 = visualPositionToXY(visStart);
      Point p2 = logicalPositionToXY(offsetToLogicalPosition(Math.min(composedTextRange.getEndOffset(), myDocument.getTextLength())));

      Stroke saved = g.getStroke();
      BasicStroke dotted = new BasicStroke(1, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{0, 2, 0, 2}, 0);
      g.setStroke(dotted);
      UIUtil.drawLine(g, p1.x, y, p2.x, y);
      g.setStroke(saved);
    }
  }

  @Nullable
  public TextRange getComposedTextRange() {
    return myInputMethodRequestsHandler == null || myInputMethodRequestsHandler.composedText == null ?
           null : myInputMethodRequestsHandler.composedTextRange;
  }

  private void paintRightMargin(@NotNull Graphics g, @NotNull Rectangle clip) {
    Color rightMargin = myScheme.getColor(EditorColors.RIGHT_MARGIN_COLOR);
    if (!mySettings.isRightMarginShown() || rightMargin == null) {
      return;
    }
    int x = mySettings.getRightMargin(myProject) * EditorUtil.getSpaceWidth(Font.PLAIN, this);
    if (x >= clip.x && x < clip.x + clip.width) {
      g.setColor(rightMargin);
      UIUtil.drawLine(g, x, clip.y, x, clip.y + clip.height);
    }
  }

  private void paintSegmentHighlightersBorderAndAfterEndOfLine(@NotNull final Graphics g,
                                                               @NotNull Rectangle clip,
                                                               int clipStartOffset,
                                                               int clipEndOffset,
                                                               @NotNull MarkupModelEx docMarkup) {
    if (myDocument.getLineCount() == 0) return;
    final int startLine = yToVisibleLine(clip.y);
    final int endLine = yToVisibleLine(clip.y + clip.height) + 1;

    Processor<RangeHighlighterEx> paintProcessor = highlighter -> {
      paintSegmentHighlighterAfterEndOfLine(g, highlighter, startLine, endLine);
      return true;
    };
    docMarkup.processRangeHighlightersOverlappingWith(clipStartOffset, clipEndOffset, paintProcessor);
    myMarkupModel.processRangeHighlightersOverlappingWith(clipStartOffset, clipEndOffset, paintProcessor);
  }

  private void paintSegmentHighlighterAfterEndOfLine(@NotNull Graphics g,
                                                     @NotNull RangeHighlighterEx segmentHighlighter,
                                                     int startLine,
                                                     int endLine) {
    if (!segmentHighlighter.isAfterEndOfLine()) {
      return;
    }
    int startOffset = segmentHighlighter.getStartOffset();
    int visibleStartLine = offsetToVisualLine(startOffset);

    if (getFoldingModel().isOffsetCollapsed(startOffset)) {
      return;
    }
    if (visibleStartLine >= startLine && visibleStartLine <= endLine) {
      int logStartLine = offsetToLogicalLine(startOffset);
      if (logStartLine >= myDocument.getLineCount()) {
        return;
      }
      LogicalPosition logPosition = offsetToLogicalPosition(myDocument.getLineEndOffset(logStartLine));
      Point end = logicalPositionToXY(logPosition);
      int charWidth = EditorUtil.getSpaceWidth(Font.PLAIN, this);
      int lineHeight = getLineHeight();
      TextAttributes attributes = segmentHighlighter.getTextAttributes();
      if (attributes != null && getBackgroundColor(attributes) != null) {
        g.setColor(getBackgroundColor(attributes));
        g.fillRect(end.x, end.y, charWidth, lineHeight);
      }
      if (attributes != null && attributes.getEffectColor() != null) {
        int y = visibleLineToY(visibleStartLine) + getAscent() + 1;
        if (attributes.getEffectType() == EffectType.WAVE_UNDERSCORE) {
          EffectPainter.WAVE_UNDERSCORE.paint((Graphics2D)g, end.x, y - 1, charWidth - 1, getDescent(), attributes.getEffectColor());
        }
        else if (attributes.getEffectType() == EffectType.BOLD_DOTTED_LINE) {
          g.setColor(getBackgroundColor(attributes));
          EffectPainter.BOLD_DOTTED_UNDERSCORE.paint((Graphics2D)g, end.x, y - 1, charWidth - 1, getDescent(), attributes.getEffectColor());
        }
        else if (attributes.getEffectType() == EffectType.STRIKEOUT) {
          EffectPainter.STRIKE_THROUGH.paint((Graphics2D)g, end.x, y - 1, charWidth - 1, getCharHeight(), attributes.getEffectColor());
        }
        else if (attributes.getEffectType() == EffectType.BOLD_LINE_UNDERSCORE) {
          EffectPainter.BOLD_LINE_UNDERSCORE.paint((Graphics2D)g, end.x, y - 1, charWidth - 1, getDescent(), attributes.getEffectColor());
        }
        else if (attributes.getEffectType() != EffectType.BOXED) {
          EffectPainter.LINE_UNDERSCORE.paint((Graphics2D)g, end.x, y - 1, charWidth - 1, getDescent(), attributes.getEffectColor());
        }
      }
    }
  }

  @Override
  public int getMaxWidthInRange(int startOffset, int endOffset) {
    if (myUseNewRendering) return myView.getMaxWidthInRange(startOffset, endOffset);
    int start = offsetToVisualLine(startOffset);
    int end = offsetToVisualLine(endOffset);

    return getMaxWidthInVisualLineRange(start, end, true);
  }

  int getMaxWidthInVisualLineRange(int startVisualLine, int endVisualLine, boolean addOneColumn) {
    int width = 0;

    for (int i = startVisualLine; i <= endVisualLine; i++) {
      int lastColumn = EditorUtil.getLastVisualLineColumnNumber(this, i) + (addOneColumn ? 1 : 0);
      int lineWidth = visualPositionToXY(new VisualPosition(i, lastColumn)).x;

      if (lineWidth > width) {
        width = lineWidth;
      }
    }

    return width;
  }

  private void paintBackgrounds(@NotNull Graphics g,
                                @NotNull Rectangle clip,
                                @NotNull LogicalPosition clipStartPosition,
                                @NotNull VisualPosition clipStartVisualPos,
                                int clipStartOffset, int clipEndOffset) {
    Color defaultBackground = getBackgroundColor();
    if (myEditorComponent.isOpaque()) {
      g.setColor(defaultBackground);
      g.fillRect(clip.x, clip.y, clip.width, clip.height);
    }
    Color prevBackColor = null;

    int lineHeight = getLineHeight();

    int visibleLine = yToVisibleLine(clip.y);

    Point position = new Point(0, visibleLine * lineHeight);
    CharSequence prefixText = myPrefixText == null ? null : new CharArrayCharSequence(myPrefixText);
    if (clipStartVisualPos.line == 0 && prefixText != null) {
      Color backColor = myPrefixAttributes.getBackgroundColor();
      position.x = drawBackground(g, backColor, prefixText, 0, prefixText.length(), position,
                                  myPrefixAttributes.getFontType(),
                                  defaultBackground, clip);
      prevBackColor = backColor;
    }

    if (clipStartPosition.line >= myDocument.getLineCount() || clipStartPosition.line < 0) {
      if (position.x > 0) flushBackground(g, clip);
      return;
    }

    myLastBackgroundPosition = null;
    myLastBackgroundColor = null;
    mySelectionStartPosition = null;
    mySelectionEndPosition = null;

    int start = clipStartOffset;

    if (!myPurePaintingMode) {
      getSoftWrapModel().registerSoftWrapsIfNecessary();
    }

    LineIterator lIterator = createLineIterator();
    lIterator.start(start);
    if (lIterator.atEnd()) {
      return;
    }

    IterationState iterationState = new IterationState(this, start, clipEndOffset, isPaintSelection());
    TextAttributes attributes = iterationState.getMergedAttributes();
    Color backColor = getBackgroundColor(attributes);
    int fontType = attributes.getFontType();
    int lastLineIndex = Math.max(0, myDocument.getLineCount() - 1);

    // There is a possible case that we need to draw background from the start of soft wrap-introduced visual line. Given position
    // has valid 'y' coordinate then at it shouldn't be affected by soft wrap that corresponds to the visual line start offset.
    // Hence, we store information about soft wrap to be skipped for further processing and adjust 'x' coordinate value if necessary.
    TIntHashSet softWrapsToSkip = new TIntHashSet();
    SoftWrap softWrap = getSoftWrapModel().getSoftWrap(start);
    if (softWrap != null) {
      softWrapsToSkip.add(softWrap.getStart());
      Color color = null;
      if (backColor != null && !backColor.equals(defaultBackground)) {
        color = backColor;
      }

      // There is a possible case that target clip points to soft wrap-introduced visual line and that it's an active
      // line (caret cursor is located on it). We want to draw corresponding 'caret line' background for soft wraps-introduced
      // virtual space then.
      if (color == null && position.y == getCaretModel().getVisualPosition().line * getLineHeight()) {
        color = mySettings.isCaretRowShown() ? getColorsScheme().getColor(EditorColors.CARET_ROW_COLOR) : null;
      }

      if (color != null) {
        drawBackground(g, color, softWrap.getIndentInPixels(), position, defaultBackground, clip);
        prevBackColor = color;
      }
      position.x = softWrap.getIndentInPixels();
    }

    // There is a possible case that caret is located at soft-wrapped line. We don't need to paint caret row background
    // on a last visual line of that soft-wrapped line then. Below is a holder for the flag that indicates if caret row
    // background is already drawn.
    boolean[] caretRowPainted = new boolean[1];

    CharSequence text = myDocument.getImmutableCharSequence();

    while (!iterationState.atEnd() && !lIterator.atEnd()) {
      int hEnd = iterationState.getEndOffset();
      int lEnd = lIterator.getEnd();

      if (hEnd >= lEnd) {
        FoldRegion collapsedFolderAt = myFoldingModel.getCollapsedRegionAtOffset(start);
        if (collapsedFolderAt == null) {
          position.x = drawSoftWrapAwareBackground(g, backColor, prevBackColor, text, start, lEnd - lIterator.getSeparatorLength(), 
                                                   position, fontType, defaultBackground, clip, softWrapsToSkip, caretRowPainted);
          prevBackColor = backColor;

          paintAfterLineEndBackgroundSegments(g, iterationState, position, defaultBackground, lineHeight);

          if (lIterator.getLineNumber() < lastLineIndex) {
            if (backColor != null && !backColor.equals(defaultBackground)) {
              g.setColor(backColor);
              g.fillRect(position.x, position.y, clip.x + clip.width - position.x, lineHeight);
            }
          }
          else {
            if (iterationState.hasPastFileEndBackgroundSegments()) {
              paintAfterLineEndBackgroundSegments(g, iterationState, position, defaultBackground, lineHeight);
            }
            paintAfterFileEndBackground(iterationState,
                                        g,
                                        position, clip,
                                        lineHeight, defaultBackground, caretRowPainted);
            break;
          }

          position.x = 0;
          if (position.y > clip.y + clip.height) break;
          position.y += lineHeight;
          start = lEnd;
        }
        else if (collapsedFolderAt.getEndOffset() == clipEndOffset) {
          drawCollapsedFolderBackground(g, clip, defaultBackground, prevBackColor, position, backColor, fontType, softWrapsToSkip, caretRowPainted, text,
                                        collapsedFolderAt);
          prevBackColor = backColor;
        }

        lIterator.advance();
      }
      else {
        FoldRegion collapsedFolderAt = iterationState.getCurrentFold();
        if (collapsedFolderAt != null) {
          drawCollapsedFolderBackground(g, clip, defaultBackground, prevBackColor, position, backColor, fontType, softWrapsToSkip, caretRowPainted, text,
                                        collapsedFolderAt);
          prevBackColor = backColor;
        }
        else if (hEnd > lEnd - lIterator.getSeparatorLength()) {
          position.x = drawSoftWrapAwareBackground(
            g, backColor, prevBackColor, text, start, lEnd - lIterator.getSeparatorLength(), position, fontType,
            defaultBackground, clip, softWrapsToSkip, caretRowPainted
          );
          prevBackColor = backColor;
        }
        else {
          position.x = drawSoftWrapAwareBackground(
            g, backColor, prevBackColor, text, start, hEnd, position, fontType, defaultBackground, clip, softWrapsToSkip, caretRowPainted
          );
          prevBackColor = backColor;
        }

        iterationState.advance();
        attributes = iterationState.getMergedAttributes();
        backColor = getBackgroundColor(attributes);
        fontType = attributes.getFontType();
        start = iterationState.getStartOffset();
      }
    }

    flushBackground(g, clip);

    if (lIterator.getLineNumber() >= lastLineIndex && position.y <= clip.y + clip.height) {
      paintAfterFileEndBackground(iterationState, g, position, clip, lineHeight, defaultBackground, caretRowPainted);
    }

    // Perform additional activity if soft wrap is added or removed during repainting.
    if (mySoftWrapsChanged) {
      mySoftWrapsChanged = false;
      clearTextWidthCache();
      validateSize();

      // Repaint editor to the bottom in order to ensure that its content is shown correctly after new soft wrap introduction.
      repaintToScreenBottom(EditorUtil.yPositionToLogicalLine(this, position));

      // Repaint gutter at all space that is located after active clip in order to ensure that line numbers are correctly redrawn
      // in accordance with the newly introduced soft wrap(s).
      myGutterComponent.repaint(0, clip.y, myGutterComponent.getWidth(), myGutterComponent.getHeight() - clip.y);
    }
  }

  private void drawCollapsedFolderBackground(@NotNull Graphics g,
                                             @NotNull Rectangle clip,
                                             @NotNull Color defaultBackground,
                                             @Nullable Color prevBackColor,
                                             @NotNull Point position,
                                             @NotNull Color backColor,
                                             int fontType,
                                             @NotNull TIntHashSet softWrapsToSkip,
                                             @NotNull boolean[] caretRowPainted,
                                             @NotNull CharSequence text,
                                             @NotNull FoldRegion collapsedFolderAt) {
    SoftWrap softWrap = mySoftWrapModel.getSoftWrap(collapsedFolderAt.getStartOffset());
    if (softWrap != null) {
      position.x = drawSoftWrapAwareBackground(
        g, backColor, prevBackColor, text, collapsedFolderAt.getStartOffset(), collapsedFolderAt.getStartOffset(), position, fontType,
        defaultBackground, clip, softWrapsToSkip, caretRowPainted
      );
    }
    CharSequence chars = collapsedFolderAt.getPlaceholderText();
    position.x = drawBackground(g, backColor, chars, 0, chars.length(), position, fontType, defaultBackground, clip);
  }

  private void paintAfterLineEndBackgroundSegments(@NotNull Graphics g,
                                                   @NotNull IterationState iterationState,
                                                   @NotNull Point position,
                                                   @NotNull Color defaultBackground,
                                                   int lineHeight) {
    while (iterationState.hasPastLineEndBackgroundSegment()) {
      TextAttributes backgroundAttributes = iterationState.getPastLineEndBackgroundAttributes();
      int width = EditorUtil.getSpaceWidth(backgroundAttributes.getFontType(), this) * iterationState.getPastLineEndBackgroundSegmentWidth();
      Color color = getBackgroundColor(backgroundAttributes);
      if (color != null && !color.equals(defaultBackground)) {
        g.setColor(color);
        g.fillRect(position.x, position.y, width, lineHeight);
      }
      position.x += width;
      iterationState.advanceToNextPastLineEndBackgroundSegment();
    }
  }

  private void paintAfterFileEndBackground(@NotNull IterationState iterationState,
                                           @NotNull Graphics g,
                                           @NotNull Point position,
                                           @NotNull Rectangle clip,
                                           int lineHeight,
                                           @NotNull Color defaultBackground,
                                           @NotNull boolean[] caretRowPainted) {
    Color backColor = iterationState.getPastFileEndBackground();
    if (backColor == null || backColor.equals(defaultBackground)) {
      return;
    }
    if (caretRowPainted[0] && backColor.equals(getColorsScheme().getColor(EditorColors.CARET_ROW_COLOR))) {
      return;
    }
    g.setColor(backColor);
    g.fillRect(position.x, position.y, clip.x + clip.width - position.x, lineHeight);
  }

  private int drawSoftWrapAwareBackground(@NotNull Graphics g,
                                          @Nullable Color backColor,
                                          @Nullable Color prevBackColor,
                                          @NotNull CharSequence text,
                                          int start,
                                          int end,
                                          @NotNull Point position,
                                          @JdkConstants.FontStyle int fontType,
                                          @NotNull Color defaultBackground,
                                          @NotNull Rectangle clip,
                                          @NotNull TIntHashSet softWrapsToSkip,
                                          @NotNull boolean[] caretRowPainted) {
    int startToUse = start;
    // Given 'end' offset is exclusive though SoftWrapModel.getSoftWrapsForRange() uses inclusive end offset.
    // Hence, we decrement it if necessary. Please note that we don't do that if start is equal to end. That is the case,
    // for example, for soft-wrapped collapsed fold region - we need to draw soft wrap before it.
    int softWrapRetrievalEndOffset = end;
    if (end > start) {
      softWrapRetrievalEndOffset--;
    }
    List<? extends SoftWrap> softWraps = getSoftWrapModel().getSoftWrapsForRange(start, softWrapRetrievalEndOffset);
    for (SoftWrap softWrap : softWraps) {
      int softWrapStart = softWrap.getStart();
      if (softWrapsToSkip.contains(softWrapStart)) {
        continue;
      }
      if (startToUse < softWrapStart) {
        position.x = drawBackground(g, backColor, text, startToUse, softWrapStart, position, fontType, defaultBackground, clip);
      }
      boolean drawCustomBackgroundAtSoftWrapVirtualSpace =
        !Comparing.equal(backColor, defaultBackground) && (softWrapStart > start || Comparing.equal(prevBackColor, backColor));
      drawSoftWrap(
        g, softWrap, position, fontType, backColor, drawCustomBackgroundAtSoftWrapVirtualSpace, defaultBackground, clip, caretRowPainted
      );
      startToUse = softWrapStart;
    }

    if (startToUse < end) {
      position.x = drawBackground(g, backColor, text, startToUse, end, position, fontType, defaultBackground, clip);
    }
    return position.x;
  }

  private void drawSoftWrap(@NotNull Graphics g,
                            @NotNull SoftWrap softWrap,
                            @NotNull Point position,
                            @JdkConstants.FontStyle int fontType,
                            @Nullable Color backColor,
                            boolean drawCustomBackgroundAtSoftWrapVirtualSpace,
                            @NotNull Color defaultBackground,
                            @NotNull Rectangle clip,
                            @NotNull boolean[] caretRowPainted) {
    // The main idea is to to do the following:
    //     *) update given drawing position coordinates in accordance with the current soft wrap;
    //     *) draw background at soft wrap-introduced virtual space if necessary;

    CharSequence softWrapText = softWrap.getText();
    int activeRowY = getCaretModel().getVisualPosition().line * getLineHeight();
    int afterSoftWrapWidth = clip.x + clip.width - position.x;
    if (drawCustomBackgroundAtSoftWrapVirtualSpace && backColor != null) {
      drawBackground(g, backColor, afterSoftWrapWidth, position, defaultBackground, clip);
    }
    else if (position.y == activeRowY) {
      // Draw 'active line' background after soft wrap.
      Color caretRowColor = mySettings.isCaretRowShown()? getColorsScheme().getColor(EditorColors.CARET_ROW_COLOR) : null;
      drawBackground(g, caretRowColor, afterSoftWrapWidth, position, defaultBackground, clip);
      caretRowPainted[0] = true;
    }

    paintSelectionOnFirstSoftWrapLineIfNecessary(g, position, clip, defaultBackground, fontType);

    int i = CharArrayUtil.lastIndexOf(softWrapText, "\n", softWrapText.length()) + 1;
    int width = getTextSegmentWidth(softWrapText, i, softWrapText.length(), 0, fontType, clip)
                + getSoftWrapModel().getMinDrawingWidthInPixels(SoftWrapDrawingType.AFTER_SOFT_WRAP);
    position.x = 0;
    position.y += getLineHeight();

    if (drawCustomBackgroundAtSoftWrapVirtualSpace && backColor != null) {
      drawBackground(g, backColor, width, position, defaultBackground, clip);
    }
    else if (position.y == activeRowY) {
      // Draw 'active line' background for the soft wrap-introduced virtual space.
      Color caretRowColor = mySettings.isCaretRowShown()? getColorsScheme().getColor(EditorColors.CARET_ROW_COLOR) : null;
      drawBackground(g, caretRowColor, width, position, defaultBackground, clip);
    }

    position.x = 0;
    paintSelectionOnSecondSoftWrapLineIfNecessary(g, position, clip, defaultBackground, fontType, softWrap);
    position.x = width;
  }


  private VisualPosition getSelectionStartPositionForPaint() {
    if (mySelectionStartPosition == null) {
      // We cache the value to avoid repeated invocations of Editor.logicalPositionToOffset which is currently slow for long lines
      mySelectionStartPosition = getSelectionModel().getSelectionStartPosition();
    }
    return mySelectionStartPosition;
  }

  private VisualPosition getSelectionEndPositionForPaint() {
    if (mySelectionEndPosition == null) {
      // We cache the value to avoid repeated invocations of Editor.logicalPositionToOffset which is currently slow for long lines
      mySelectionEndPosition = getSelectionModel().getSelectionEndPosition();
    }
    return mySelectionEndPosition;
  }

  /**
   * End user is allowed to perform selection by visual coordinates (e.g. by dragging mouse with left button hold). There is a possible
   * case that such a move intersects with soft wrap introduced virtual space. We want to draw corresponding selection background
   * there then.
   * <p/>
   * This method encapsulates functionality of drawing selection background on the first soft wrap line (e.g. on a visual line where
   * it is applied).
   *
   * @param g                 graphics to draw on
   * @param position          current position (assumed to be position of soft wrap appliance)
   * @param clip              target drawing area boundaries
   * @param defaultBackground default background
   * @param fontType          current font type
   */
  private void paintSelectionOnFirstSoftWrapLineIfNecessary(@NotNull Graphics g,
                                                            @NotNull Point position,
                                                            @NotNull Rectangle clip,
                                                            @NotNull Color defaultBackground,
                                                            @JdkConstants.FontStyle int fontType) {
    // There is a possible case that the user performed selection at soft wrap virtual space. We need to paint corresponding background
    // there then.
    VisualPosition selectionStartPosition = getSelectionStartPositionForPaint();
    VisualPosition selectionEndPosition = getSelectionEndPositionForPaint();
    if (selectionStartPosition.equals(selectionEndPosition)) {
      return;
    }

    int currentVisualLine = position.y / getLineHeight();
    int lastColumn = EditorUtil.getLastVisualLineColumnNumber(this, currentVisualLine);

    // Check if the first soft wrap line is within the visual selection.
    if (currentVisualLine < selectionStartPosition.line || currentVisualLine > selectionEndPosition.line
        || currentVisualLine == selectionEndPosition.line && selectionEndPosition.column <= lastColumn) {
      return;
    }

    // Adjust 'x' if selection starts at soft wrap virtual space.
    final int columnsToSkip = selectionStartPosition.column - lastColumn;
    if (columnsToSkip > 0) {
      position.x += getSoftWrapModel().getMinDrawingWidthInPixels(SoftWrapDrawingType.BEFORE_SOFT_WRAP_LINE_FEED);
      position.x += (columnsToSkip - 1) * EditorUtil.getSpaceWidth(Font.PLAIN, this);
    }

    // Calculate selection width.
    final int width;
    if (selectionEndPosition.line > currentVisualLine) {
      width = clip.x + clip.width - position.x;
    }
    else if (selectionStartPosition.line < currentVisualLine || selectionStartPosition.column <= lastColumn) {
      width = getSoftWrapModel().getMinDrawingWidthInPixels(SoftWrapDrawingType.BEFORE_SOFT_WRAP_LINE_FEED)
              + (selectionEndPosition.column - lastColumn - 1) * EditorUtil.getSpaceWidth(fontType, this);
    }
    else {
      width = (selectionEndPosition.column - selectionStartPosition.column) * EditorUtil.getSpaceWidth(fontType, this);
    }

    drawBackground(g, getColorsScheme().getColor(EditorColors.SELECTION_BACKGROUND_COLOR), width, position, defaultBackground, clip);
  }

  /**
   * End user is allowed to perform selection by visual coordinates (e.g. by dragging mouse with left button hold). There is a possible
   * case that such a move intersects with soft wrap introduced virtual space. We want to draw corresponding selection background
   * there then.
   * <p/>
   * This method encapsulates functionality of drawing selection background on the second soft wrap line (e.g. on a visual line after
   * the one where it is applied).
   *
   * @param g                 graphics to draw on
   * @param position          current position (assumed to be position of soft wrap appliance)
   * @param clip              target drawing area boundaries
   * @param defaultBackground default background
   * @param fontType          current font type
   * @param softWrap          target soft wrap which second line virtual space may contain selection
   */
  private void paintSelectionOnSecondSoftWrapLineIfNecessary(@NotNull Graphics g,
                                                             @NotNull Point position,
                                                             @NotNull Rectangle clip,
                                                             @NotNull Color defaultBackground,
                                                             @JdkConstants.FontStyle int fontType,
                                                             @NotNull SoftWrap softWrap) {
    // There is a possible case that the user performed selection at soft wrap virtual space. We need to paint corresponding background
    // there then.
    VisualPosition selectionStartPosition = getSelectionStartPositionForPaint();
    VisualPosition selectionEndPosition = getSelectionEndPositionForPaint();
    if (selectionStartPosition.equals(selectionEndPosition)) {
      return;
    }

    int currentVisualLine = position.y / getLineHeight();

    // Check if the second soft wrap line is within the visual selection.
    if (currentVisualLine < selectionStartPosition.line || currentVisualLine > selectionEndPosition.line
        || currentVisualLine == selectionStartPosition.line && selectionStartPosition.column >= softWrap.getIndentInColumns()) {
      return;
    }

    // Adjust 'x' if selection starts at soft wrap virtual space.
    if (selectionStartPosition.line == currentVisualLine && selectionStartPosition.column > 0) {
      position.x += selectionStartPosition.column * EditorUtil.getSpaceWidth(fontType, this);
    }

    // Calculate selection width.
    final int width;
    if (selectionEndPosition.line > currentVisualLine || selectionEndPosition.column >= softWrap.getIndentInColumns()) {
      width = softWrap.getIndentInPixels() - position.x;
    }
    else {
      width = selectionEndPosition.column * EditorUtil.getSpaceWidth(fontType, this) - position.x;
    }

    drawBackground(g, getColorsScheme().getColor(EditorColors.SELECTION_BACKGROUND_COLOR), width, position, defaultBackground, clip);
  }

  private int drawBackground(@NotNull Graphics g,
                             Color backColor,
                             @NotNull CharSequence text,
                             int start,
                             int end,
                             @NotNull Point position,
                             @JdkConstants.FontStyle int fontType,
                             @NotNull Color defaultBackground,
                             @NotNull Rectangle clip) {
    int width = getTextSegmentWidth(text, start, end, position.x, fontType, clip);
    return drawBackground(g, backColor, width, position, defaultBackground, clip);
  }

  private int drawBackground(@NotNull Graphics g,
                             @Nullable Color backColor,
                             int width,
                             @NotNull Point position,
                             @NotNull Color defaultBackground,
                             @NotNull Rectangle clip) {
    if (backColor != null && !backColor.equals(defaultBackground) && clip.intersects(position.x, position.y, width, getLineHeight())) {
      if (backColor.equals(myLastBackgroundColor) && myLastBackgroundPosition.y == position.y &&
          myLastBackgroundPosition.x + myLastBackgroundWidth == position.x) {
        myLastBackgroundWidth += width;
      }
      else {
        flushBackground(g, clip);
        myLastBackgroundColor = backColor;
        myLastBackgroundPosition = new Point(position);
        myLastBackgroundWidth = width;
      }
    }

    return position.x + width;
  }

  private void flushBackground(@NotNull Graphics g, @NotNull final Rectangle clip) {
    if (myLastBackgroundColor != null) {
      final Point position = myLastBackgroundPosition;
      final int w = myLastBackgroundWidth;
      final int height = getLineHeight();
      if (clip.intersects(position.x, position.y, w, height)) {
        g.setColor(myLastBackgroundColor);
        g.fillRect(position.x, position.y, w, height);
      }
      myLastBackgroundColor = null;
    }
  }

  @NotNull
  private LineIterator createLineIterator() {
    return myDocument.createLineIterator();
  }

  private void paintText(@NotNull Graphics g,
                         @NotNull Rectangle clip,
                         @NotNull LogicalPosition clipStartPosition,
                         int clipStartOffset,
                         int clipEndOffset) {
    myCurrentFontType = null;
    myLastCache = null;

    int lineHeight = getLineHeight();

    int visibleLine = clip.y / lineHeight;

    int startLine = clipStartPosition.line;
    int start = clipStartOffset;

    Point position = new Point(0, visibleLine * lineHeight);
    if (startLine == 0 && myPrefixText != null) {
      position.x = drawStringWithSoftWraps(g, new CharArrayCharSequence(myPrefixText), 0, myPrefixText.length, position, clip,
                                           myPrefixAttributes.getEffectColor(), myPrefixAttributes.getEffectType(),
                                           myPrefixAttributes.getFontType(), myPrefixAttributes.getForegroundColor(), -1,
                                           PAINT_NO_WHITESPACE);
    }
    if (startLine >= myDocument.getLineCount() || startLine < 0) {
      if (position.x > 0) flushCachedChars(g);
      return;
    }

    LineIterator lIterator = createLineIterator();
    lIterator.start(start);
    if (lIterator.atEnd()) {
      return;
    }

    IterationState iterationState = new IterationState(this, start, clipEndOffset, isPaintSelection());
    TextAttributes attributes = iterationState.getMergedAttributes();
    Color currentColor = attributes.getForegroundColor();
    if (currentColor == null) {
      currentColor = getForegroundColor();
    }
    Color effectColor = attributes.getEffectColor();
    EffectType effectType = attributes.getEffectType();
    int fontType = attributes.getFontType();
    g.setColor(currentColor);

    CharSequence chars = myDocument.getImmutableCharSequence();
    LineWhitespacePaintingStrategy context = new LineWhitespacePaintingStrategy();
    context.update(chars, lIterator);

    while (!iterationState.atEnd() && !lIterator.atEnd()) {
      int hEnd = iterationState.getEndOffset();
      int lEnd = lIterator.getEnd();
      if (hEnd >= lEnd) {
        FoldRegion collapsedFolderAt = myFoldingModel.getCollapsedRegionAtOffset(start);
        if (collapsedFolderAt == null) {
          drawStringWithSoftWraps(g, chars, start, lEnd - lIterator.getSeparatorLength(), position, clip, effectColor,
                                                effectType, fontType, currentColor, clipStartOffset, context);
          final VirtualFile file = getVirtualFile();
          if (myProject != null && file != null && !isOneLineMode()) {
            int offset = position.x;
            for (EditorLinePainter painter : EditorLinePainter.EP_NAME.getExtensions()) {
              Collection<LineExtensionInfo> extensions = painter.getLineExtensions(myProject, file, lIterator.getLineNumber());
              if (extensions != null && !extensions.isEmpty()) {
                for (LineExtensionInfo info : extensions) {
                  final String text = info.getText();
                  for (int i = 0; i < text.length(); i++) {
                    char ch = text.charAt(i);
                    offset += EditorUtil.charWidth(ch, Font.ITALIC, this);
                  }
                  position.x = drawString(g, text, 0, text.length(), position, clip,
                                          info.getEffectColor() == null ? effectColor : info.getEffectColor(),
                                          info.getEffectType() == null ? effectType : info.getEffectType(),
                                          info.getFontType(),
                                          info.getColor() == null ? currentColor : info.getColor(),
                                          context);
                }
              }
            }
            myLinePaintersWidth = Math.max(myLinePaintersWidth, offset);
          }

          position.x = 0;
          if (position.y > clip.y + clip.height) {
            break;
          }
          position.y += lineHeight;
          start = lEnd;
        }

        //        myBorderEffect.eolReached(g, this);
        lIterator.advance();
        if (!lIterator.atEnd()) {
          context.update(chars, lIterator);
        }
      }
      else {
        FoldRegion collapsedFolderAt = iterationState.getCurrentFold();
        if (collapsedFolderAt != null) {
          SoftWrap softWrap = mySoftWrapModel.getSoftWrap(collapsedFolderAt.getStartOffset());
          if (softWrap != null) {
            position.x = drawStringWithSoftWraps(
              g, chars, collapsedFolderAt.getStartOffset(), collapsedFolderAt.getStartOffset(), position, clip, effectColor, effectType,
              fontType, currentColor, clipStartOffset, context
            );
          }
          int foldingXStart = position.x;
          position.x = drawString(
            g, collapsedFolderAt.getPlaceholderText(), position, clip, effectColor, effectType, fontType, currentColor,
            PAINT_NO_WHITESPACE);
          //drawStringWithSoftWraps(g, collapsedFolderAt.getPlaceholderText(), position, clip, effectColor, effectType,
          //                        fontType, currentColor, logicalPosition);
          BorderEffect.paintFoldedEffect(g, foldingXStart, position.y, position.x, getLineHeight(), effectColor, effectType);
        }
        else {
          position.x = drawStringWithSoftWraps(g, chars, start, Math.min(hEnd, lEnd - lIterator.getSeparatorLength()), position, clip,
                                               effectColor, effectType, fontType, currentColor, clipStartOffset, context);
        }

        iterationState.advance();
        attributes = iterationState.getMergedAttributes();

        currentColor = attributes.getForegroundColor();
        if (currentColor == null) {
          currentColor = getForegroundColor();
        }

        effectColor = attributes.getEffectColor();
        effectType = attributes.getEffectType();
        fontType = attributes.getFontType();

        start = iterationState.getStartOffset();
      }
    }

    FoldRegion collapsedFolderAt = iterationState.getCurrentFold();
    if (collapsedFolderAt != null) {
      int foldingXStart = position.x;
      int foldingXEnd = drawStringWithSoftWraps(
        g, collapsedFolderAt.getPlaceholderText(), position, clip, effectColor, effectType, fontType, currentColor, clipStartOffset,
        PAINT_NO_WHITESPACE);
      BorderEffect.paintFoldedEffect(g, foldingXStart, position.y, foldingXEnd, getLineHeight(), effectColor, effectType);
      //      myBorderEffect.collapsedFolderReached(g, this);
    }

    final SoftWrap softWrap = mySoftWrapModel.getSoftWrap(clipEndOffset);
    if (softWrap != null) {
      mySoftWrapModel.paint(g, SoftWrapDrawingType.BEFORE_SOFT_WRAP_LINE_FEED, position.x, position.y, getLineHeight());
    }

    flushCachedChars(g);
  }

  private boolean paintPlaceholderText(@NotNull Graphics g, @NotNull Rectangle clip) {
    CharSequence hintText = myPlaceholderText;
    if (myDocument.getTextLength() > 0 || hintText == null || hintText.length() == 0) {
      return false;
    }

    if (KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() == myEditorComponent && !myShowPlaceholderWhenFocused) {
      // There is a possible case that placeholder text was painted and the editor gets focus now. We want to over-paint previously
      // used placeholder text then.
      myLastBackgroundColor = getBackgroundColor();
      myLastBackgroundPosition = new Point(0, 0);
      myLastBackgroundWidth = myLastPaintedPlaceholderWidth;
      flushBackground(g, clip);
      return false;
    }
    else {
      hintText = SwingUtilities.layoutCompoundLabel(g.getFontMetrics(), hintText.toString(), null, 0, 0, 0, 0,
                                                    myEditorComponent.getBounds(), new Rectangle(), new Rectangle(), 0);
      myLastPaintedPlaceholderWidth = drawString(
        g, hintText, 0, hintText.length(), new Point(0, 0), clip, null, null, 
        myPlaceholderAttributes == null ? Font.PLAIN : myPlaceholderAttributes.getFontType(),
        myPlaceholderAttributes == null ? myFoldingModel.getPlaceholderAttributes().getForegroundColor() : 
                                          myPlaceholderAttributes.getForegroundColor(), 
        PAINT_NO_WHITESPACE
      );
      flushCachedChars(g);
      return true;
    }
  }

  private boolean isPaintSelection() {
    return myPaintSelection || !isOneLineMode() || IJSwingUtilities.hasFocus(getContentComponent());
  }

  public void setPaintSelection(boolean paintSelection) {
    myPaintSelection = paintSelection;
  }

  @Override
  @NotNull
  @NonNls
  public String dumpState() {
    return "prefix: '" + (myPrefixText == null ? "none" : new String(myPrefixText))
           + "', allow caret inside tab: " + mySettings.isCaretInsideTabs()
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

  private class CachedFontContent {
    private final CharSequence[] data = new CharSequence[CACHED_CHARS_BUFFER_SIZE];
    private final int[] starts = new int[CACHED_CHARS_BUFFER_SIZE];
    private final int[] ends = new int[CACHED_CHARS_BUFFER_SIZE];
    private final int[] x = new int[CACHED_CHARS_BUFFER_SIZE];
    private final int[] y = new int[CACHED_CHARS_BUFFER_SIZE];
    private final Color[] color = new Color[CACHED_CHARS_BUFFER_SIZE];
    private final boolean[] whitespaceShown = new boolean[CACHED_CHARS_BUFFER_SIZE];

    private int myCount;
    @NotNull private final FontInfo myFontType;
    private final boolean myHasBreakSymbols;
    private final int spaceWidth;

    @Nullable private CharSequence myLastData;

    private CachedFontContent(@NotNull FontInfo fontInfo) {
      myFontType = fontInfo;
      spaceWidth = fontInfo.charWidth(' ');
      myHasBreakSymbols = fontInfo.hasGlyphsToBreakDrawingIteration();
    }

    private void flushContent(@NotNull Graphics g) {
      if (myCount != 0) {
        if (myCurrentFontType != myFontType) {
          myCurrentFontType = myFontType;
          g.setFont(myFontType.getFont());
        }
        Color currentColor = null;
        int whiteSpaceStrokeWidth = JBUI.scale(1);
        BasicStroke whiteSpaceStroke = new BasicStroke(whiteSpaceStrokeWidth);

        for (int i = 0; i < myCount; i++) {
          if (!Comparing.equal(color[i], currentColor)) {
            currentColor = color[i] != null ? color[i] : JBColor.black;

            g.setColor(currentColor);
          }

          drawChars(g, data[i], starts[i], ends[i], x[i], y[i], whitespaceShown[i], whiteSpaceStroke, whiteSpaceStrokeWidth);
          color[i] = null;
          data[i] = null;
        }

        myCount = 0;
        myLastData = null;
      }
    }

    private void addContent(@NotNull Graphics g, CharSequence _data, int _start, int _end, int _x, int _y, @Nullable Color _color, boolean drawWhitespace) {
      final int count = myCount;
      if (count > 0) {
        final int lastCount = count - 1;
        final Color lastColor = color[lastCount];
        if (_data == myLastData && _start == ends[lastCount] && (_color == null || lastColor == null || _color.equals(lastColor))
            && _y == y[lastCount] /* there is a possible case that vertical position is adjusted because of soft wrap */
            && (!myHasBreakSymbols || !myFontType.getSymbolsToBreakDrawingIteration().contains(_data.charAt(ends[lastCount] - 1)))
            && (!myDisableRtl || _start < 1 || _start >= _data.length() || !isRtlCharacter(_data.charAt(_start)) && !isRtlCharacter(_data.charAt(_start - 1)))
            && drawWhitespace == whitespaceShown[lastCount]) {
          ends[lastCount] = _end;
          if (lastColor == null) color[lastCount] = _color;
          return;
        }
      }

      myLastData = _data;
      data[count] = _data;
      x[count] = _x;
      y[count] = _y;
      starts[count] = _start;
      ends[count] = _end;
      color[count] = _color;
      whitespaceShown[count] = drawWhitespace;

      myCount++;
      if (count >= CACHED_CHARS_BUFFER_SIZE - 1) {
        flushContent(g);
      }
    }
  }

  private static boolean isRtlCharacter(char c) {
    byte directionality = Character.getDirectionality(c);
    return directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT
           || directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
           || directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING
           || directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE;
  }

  private void flushCachedChars(@NotNull Graphics g) {
    for (CachedFontContent cache : myFontCache) {
      cache.flushContent(g);
    }
    myLastCache = null;
  }

  private void paintCaretCursor(@NotNull Graphics g) {
    // There is a possible case that visual caret position is changed because of newly added or removed soft wraps.
    // We check if that's the case and ask caret model to recalculate visual position if necessary.

    myCaretCursor.paint(g);
  }

  @NotNull
  CaretCursor getCaretCursor() {
    return myCaretCursor;
  }
  
  @Nullable
  public CaretRectangle[] getCaretLocations(boolean onlyIfShown) {
    return myCaretCursor.getCaretLocations(onlyIfShown);
  }

  private void paintLineMarkersSeparators(@NotNull final Graphics g,
                                          @NotNull final Rectangle clip,
                                          @NotNull MarkupModelEx markupModel,
                                          int clipStartOffset,
                                          int clipEndOffset) {
    markupModel.processRangeHighlightersOverlappingWith(clipStartOffset, clipEndOffset, lineMarker -> {
      paintLineMarkerSeparator(lineMarker, clip, g);
      return true;
    });
  }

  private void paintLineMarkerSeparator(@NotNull RangeHighlighter marker, @NotNull Rectangle clip, @NotNull Graphics g) {
    Color separatorColor = marker.getLineSeparatorColor();
    LineSeparatorRenderer lineSeparatorRenderer = marker.getLineSeparatorRenderer();
    if (separatorColor == null && lineSeparatorRenderer == null) {
      return;
    }
    int line = marker.getLineSeparatorPlacement() == SeparatorPlacement.TOP ? marker.getDocument()
      .getLineNumber(marker.getStartOffset()) : marker.getDocument().getLineNumber(marker.getEndOffset());
    if (line < 0 || line >= myDocument.getLineCount()) {
      return;
    }

    // There is a possible case that particular logical line occupies more than one visual line (because of soft wraps processing),
    // hence, we need to consider that during calculating 'y' position for the last visual line used for the target logical
    // line representation.
    int y;
    SeparatorPlacement placement = marker.getLineSeparatorPlacement();
    if (placement == SeparatorPlacement.TOP) {
      y = visibleLineToY(logicalToVisualLine(line));
    }
    else if (line + 1 >= myDocument.getLineCount()) {
      y = visibleLineToY(offsetToVisualLine(myDocument.getTextLength()) + 1);
    }
    else {
      y = logicalLineToY(line + 1);
    }

    y -= 1;
    if (y + getLineHeight() < clip.y || y > clip.y + clip.height) return;

    int endShift = clip.x + clip.width;
    g.setColor(separatorColor);

    if (mySettings.isRightMarginShown() && myScheme.getColor(EditorColors.RIGHT_MARGIN_COLOR) != null) {
      endShift = Math.min(endShift, mySettings.getRightMargin(myProject) * EditorUtil.getSpaceWidth(Font.PLAIN, this));
    }

    if (lineSeparatorRenderer != null) {
      lineSeparatorRenderer.drawLine(g, 0, endShift, y);
    }
    else {
      UIUtil.drawLine(g, 0, y, endShift, y);
    }
  }

  private int drawStringWithSoftWraps(@NotNull Graphics g,
                                      @NotNull final String text,
                                      @NotNull Point position,
                                      @NotNull Rectangle clip,
                                      Color effectColor,
                                      EffectType effectType,
                                      @JdkConstants.FontStyle int fontType,
                                      Color fontColor,
                                      int startDrawingOffset,
                                      WhitespacePaintingStrategy context) {
    return drawStringWithSoftWraps(g, text, 0, text.length(), position, clip, effectColor, effectType,
                                   fontType, fontColor, startDrawingOffset, context);
  }

  private int drawStringWithSoftWraps(@NotNull Graphics g,
                                      final CharSequence text,
                                      int start,
                                      final int end,
                                      @NotNull Point position,
                                      @NotNull Rectangle clip,
                                      Color effectColor,
                                      EffectType effectType,
                                      @JdkConstants.FontStyle int fontType,
                                      Color fontColor,
                                      int startDrawingOffset,
                                      WhitespacePaintingStrategy context) {
    if (start >= end && getSoftWrapModel().getSoftWrap(start) == null) {
      return position.x;
    }

    // Given 'end' offset is exclusive though SoftWrapModel.getSoftWrapsForRange() uses inclusive end offset.
    // Hence, we decrement it if necessary. Please note that we don't do that if start is equal to end. That is the case,
    // for example, for soft-wrapped collapsed fold region - we need to draw soft wrap before it.
    int softWrapRetrievalEndOffset = end;
    if (start < end) {
      softWrapRetrievalEndOffset--;
    }

    outer:
    for (SoftWrap softWrap : getSoftWrapModel().getSoftWrapsForRange(start, softWrapRetrievalEndOffset)) {
      char[] softWrapChars = softWrap.getChars();
      CharArrayCharSequence softWrapSeq = new CharArrayCharSequence(softWrapChars);

      if (softWrap.getStart() == startDrawingOffset) {
        // If we are here that means that we are located on soft wrap-introduced visual line just after soft wrap. Hence, we need
        // to draw soft wrap indent if any and 'after soft wrap' sign.
        int i = CharArrayUtil.lastIndexOf(softWrapChars, '\n', 0, softWrapChars.length);
        if (i < softWrapChars.length - 1) {
          position.x = 0; // Soft wrap starts new visual line
          position.x = drawString(
            g, softWrapSeq, i + 1, softWrapChars.length, position, clip, null, null, fontType, fontColor, context
          );
        }
        position.x += mySoftWrapModel.paint(g, SoftWrapDrawingType.AFTER_SOFT_WRAP, position.x, position.y, getLineHeight());
        continue;
      }

      // Draw token text before the wrap.
      if (softWrap.getStart() > start) {
        position.x = drawString(
          g, text, start, softWrap.getStart(), position, clip, null, null, fontType, fontColor, context
        );
      }

      start = softWrap.getStart();

      // We don't draw every soft wrap symbol one-by-one but whole visual line. Current variable holds index that points
      // to the first soft wrap symbol that is not drawn yet.
      int softWrapSegmentStartIndex = 0;
      for (int i = 0; i < softWrapChars.length; i++) {
        // Delay soft wraps symbols drawing until EOL is found.
        if (softWrapChars[i] != '\n') {
          continue;
        }

        // Draw soft wrap symbols on current visual line if any.
        if (i - softWrapSegmentStartIndex > 0) {
          drawString(
            g, softWrapSeq, softWrapSegmentStartIndex, i, position, clip, null, null, fontType, fontColor, context
          );
        }
        mySoftWrapModel.paint(g, SoftWrapDrawingType.BEFORE_SOFT_WRAP_LINE_FEED, position.x, position.y, getLineHeight());

        // Reset 'x' coordinate because of new line start.
        position.x = 0;

        // Stop the processing if we drew the whole clip.
        if (position.y > clip.y + clip.height) {
          break outer;
        }
        position.y += getLineHeight();
        softWrapSegmentStartIndex = i + 1;
      }

      // Draw remaining soft wrap symbols from its last line if any.
      if (softWrapSegmentStartIndex < softWrapChars.length) {
        position.x += drawString(
          g, softWrapSeq, softWrapSegmentStartIndex, softWrapChars.length, position, clip, null, null, fontType, fontColor, context
        );
      }
      position.x += mySoftWrapModel.paint(g, SoftWrapDrawingType.AFTER_SOFT_WRAP, position.x, position.y, getLineHeight());
    }
    return position.x = drawString(g, text, start, end, position, clip, effectColor, effectType, fontType, fontColor, context);
  }

  private int drawString(@NotNull Graphics g,
                         final CharSequence text,
                         int start,
                         int end,
                         @NotNull Point position,
                         @NotNull Rectangle clip,
                         @Nullable Color effectColor,
                         @Nullable EffectType effectType,
                         @JdkConstants.FontStyle int fontType,
                         Color fontColor,
                         WhitespacePaintingStrategy context) {
    if (start >= end) return position.x;

    boolean isInClip = getLineHeight() + position.y >= clip.y && position.y <= clip.y + clip.height;

    if (!isInClip) return position.x;

    int y = getAscent() + position.y;
    int x = position.x;
    return drawTabbedString(g, text, start, end, x, y, effectColor, effectType, fontType, fontColor, clip, context);
  }

  public int getAscent() {
    if (myUseNewRendering) return myView.getAscent();
    return getLineHeight() - getDescent();
  }

  private int drawString(@NotNull Graphics g,
                         @NotNull String text,
                         @NotNull Point position,
                         @NotNull Rectangle clip,
                         Color effectColor,
                         EffectType effectType,
                         @JdkConstants.FontStyle int fontType,
                         Color fontColor,
                         WhitespacePaintingStrategy context) {
    boolean isInClip = getLineHeight() + position.y >= clip.y && position.y <= clip.y + clip.height;

    if (!isInClip) return position.x;

    int y = getAscent() + position.y;
    int x = position.x;

    return drawTabbedString(g, text, 0, text.length(), x, y, effectColor, effectType, fontType, fontColor, clip, context);
  }

  private int drawTabbedString(@NotNull Graphics g,
                               CharSequence text,
                               int start,
                               int end,
                               int x,
                               int y,
                               @Nullable Color effectColor,
                               EffectType effectType,
                               @JdkConstants.FontStyle int fontType,
                               Color fontColor,
                               @NotNull final Rectangle clip,
                               WhitespacePaintingStrategy context) {
    int xStart = x;

    for (int i = start; i < end; i++) {
      if (text.charAt(i) != '\t') continue;

      x = drawTablessString(text, start, i, g, x, y, fontType, fontColor, clip, context);

      int x1 = EditorUtil.nextTabStop(x, this);
      drawTabPlacer(g, y, x, x1, i, context);
      x = x1;
      start = i + 1;
    }

    x = drawTablessString(text, start, end, g, x, y, fontType, fontColor, clip, context);

    if (effectColor != null) {
      final Color savedColor = g.getColor();

//      myBorderEffect.flushIfCantProlong(g, this, effectType, effectColor);
      int xEnd = x;
      if (xStart < clip.x && xEnd < clip.x || xStart > clip.x + clip.width && xEnd > clip.x + clip.width) {
        return x;
      }

      if (xEnd > clip.x + clip.width) {
        xEnd = clip.x + clip.width;
      }
      if (xStart < clip.x) {
        xStart = clip.x;
      }

      if (effectType == EffectType.LINE_UNDERSCORE) {
        EffectPainter.LINE_UNDERSCORE.paint((Graphics2D)g, xStart, y, xEnd - xStart, getDescent(), effectColor);
        g.setColor(savedColor);
      }
      else if (effectType == EffectType.BOLD_LINE_UNDERSCORE) {
        EffectPainter.BOLD_LINE_UNDERSCORE.paint((Graphics2D)g, xStart, y, xEnd - xStart, getDescent(), effectColor);
        g.setColor(savedColor);
      }
      else if (effectType == EffectType.STRIKEOUT) {
        EffectPainter.STRIKE_THROUGH.paint((Graphics2D)g, xStart, y, xEnd - xStart, getCharHeight(), effectColor);
        g.setColor(savedColor);
      }
      else if (effectType == EffectType.WAVE_UNDERSCORE) {
        EffectPainter.WAVE_UNDERSCORE.paint((Graphics2D)g, xStart, y, xEnd - xStart, getDescent(), effectColor);
        g.setColor(savedColor);
      }
      else if (effectType == EffectType.BOLD_DOTTED_LINE) {
        g.setColor(getBackgroundColor());
        EffectPainter.BOLD_DOTTED_UNDERSCORE.paint((Graphics2D)g, xStart, y, xEnd - xStart, getDescent(), effectColor);
      }
    }

    return x;
  }

  private int drawTablessString(final CharSequence text,
                                int start,
                                final int end,
                                @NotNull final Graphics g,
                                int x,
                                final int y,
                                @JdkConstants.FontStyle final int fontType,
                                final Color fontColor,
                                @NotNull final Rectangle clip,
                                WhitespacePaintingStrategy context) {
    int endX = x;
    if (start < end) {
      FontInfo font = null;
      boolean drawWhitespace = false;
      for (int j = start; j < end; j++) {
        if (x > clip.x + clip.width) {
          return endX;
        }
        final char c = text.charAt(j);
        FontInfo newFont = EditorUtil.fontForChar(c, fontType, this);
        boolean newDrawWhitespace = context.showWhitespaceAtOffset(j);
        boolean isRtlChar = myDisableRtl && isRtlCharacter(c);
        if (j > start && (endX < clip.x || endX > clip.x + clip.width || newFont != font || newDrawWhitespace != drawWhitespace || isRtlChar)) {
          if (isOverlappingRange(clip, x, endX)) {
            drawCharsCached(g, text, start, j, x, y, fontType, fontColor, drawWhitespace);
          }
          start = j;
          x = endX;
        }
        font = newFont;
        drawWhitespace = newDrawWhitespace;
        endX += font.charWidth(c);

        if (font.hasGlyphsToBreakDrawingIteration() && font.getSymbolsToBreakDrawingIteration().contains(c) || isRtlChar) {
          drawCharsCached(g, text, start, j + 1, x, y, fontType, fontColor, drawWhitespace);
          start = j + 1;
          x = endX;
        }
      }

      if (isOverlappingRange(clip, x, endX)) {
        drawCharsCached(g, text, start, end, x, y, fontType, fontColor, drawWhitespace);
      }
    }

    return endX;
  }

  private static boolean isOverlappingRange(Rectangle clip, int xStart, int xEnd) {
    return !(xStart < clip.x && xEnd < clip.x || xStart > clip.x + clip.width && xEnd > clip.x + clip.width);
  }

  private void drawTabPlacer(Graphics g, int y, int start, int stop, int offset, WhitespacePaintingStrategy context) {
    if (context.showWhitespaceAtOffset(offset)) {
      myTabPainter.paint(g, y, start, stop);
    }
  }

  private void drawCharsCached(@NotNull Graphics g,
                               CharSequence data,
                               int start,
                               int end,
                               int x,
                               int y,
                               @JdkConstants.FontStyle int fontType,
                               Color color,
                               boolean drawWhitespace) {
    FontInfo fnt = EditorUtil.fontForChar(data.charAt(start), fontType, this);
    if (myLastCache != null && spacesOnly(data, start, end) && fnt.charWidth(' ') == myLastCache.spaceWidth) {
      // we don't care about font if we only need to paint spaces and space width matches
      myLastCache.addContent(g, data, start, end, x, y, null, drawWhitespace);
    }
    else {
      drawCharsCached(g, data, start, end, x, y, fnt, color, drawWhitespace);
    }
  }

  private void drawCharsCached(@NotNull Graphics g,
                               @NotNull CharSequence data,
                               int start,
                               int end,
                               int x,
                               int y,
                               @NotNull FontInfo fnt,
                               Color color,
                               boolean drawWhitespace) {
    CachedFontContent cache = null;
    for (CachedFontContent fontCache : myFontCache) {
      if (fontCache.myFontType == fnt) {
        cache = fontCache;
        break;
      }
    }
    if (cache == null) {
      cache = new CachedFontContent(fnt);
      myFontCache.add(cache);
    }

    myLastCache = cache;
    cache.addContent(g, data, start, end, x, y, color, drawWhitespace);
  }

  private static boolean spacesOnly(CharSequence chars, int start, int end) {
    for (int i = start; i < end; i++) {
      if (chars.charAt(i) != ' ') return false;
    }
    return true;
  }

  private void drawChars(@NotNull Graphics g,
                         CharSequence data,
                         int start,
                         int end,
                         int x,
                         int y,
                         boolean drawWhitespace,
                         BasicStroke stroke,
                         int strokeWidth) {
    g.drawString(data.subSequence(start, end).toString(), x, y);

    if (drawWhitespace) {
      Stroke oldStroke = ((Graphics2D)g).getStroke();
      Color oldColor = g.getColor();
      try {
        g.setColor(myScheme.getColor(EditorColors.WHITESPACES_COLOR));
        ((Graphics2D)g).setStroke(stroke);
        final FontMetrics metrics = g.getFontMetrics();
        y -= 1;

        for (int i = start; i < end; i++) {
          final char c = data.charAt(i);
          final int charWidth = isOracleRetina ? GraphicsUtil.charWidth(c, g.getFont()) : metrics.charWidth(c);

          if (c == ' ') {
            g.fillRect(x + (charWidth - strokeWidth >> 1), y - strokeWidth + 1, strokeWidth, strokeWidth);
          }
          else if (c == IDEOGRAPHIC_SPACE) {
            final int charHeight = getCharHeight();
            g.drawRect(x + JBUI.scale(2) + strokeWidth/2, y - charHeight + strokeWidth/2,
                       charWidth - JBUI.scale(4) - (strokeWidth - 1), charHeight - (strokeWidth - 1));
          }

          x += charWidth;
        }
      } finally {
        g.setColor(oldColor);
        ((Graphics2D)g).setStroke(oldStroke);
      }
    }
  }

  private int getTextSegmentWidth(@NotNull CharSequence text,
                                  int start,
                                  int end,
                                  int xStart,
                                  @JdkConstants.FontStyle int fontType,
                                  @NotNull Rectangle clip) {
    int x = xStart;

    for (int i = start; i < end && xStart < clip.x + clip.width; i++) {
      char c = text.charAt(i);
      if (c == '\t') {
        x = EditorUtil.nextTabStop(x, this);
      }
      else {
        x += EditorUtil.charWidth(c, fontType, this);
      }
      if (x > clip.x + clip.width) {
        break;
      }
    }
    return x - xStart;
  }

  @Override
  public int getLineHeight() {
    if (myUseNewRendering) return myView.getLineHeight();
    assertReadAccess();
    int lineHeight = myLineHeight;
    if (lineHeight < 0) {
      FontMetrics fontMetrics = myEditorComponent.getFontMetrics(myScheme.getFont(EditorFontType.PLAIN));
      int fontMetricsHeight = fontMetrics.getHeight();
      lineHeight = (int)(fontMetricsHeight * (isOneLineMode() ? 1 : myScheme.getLineSpacing()));
      if (lineHeight <= 0) {
        lineHeight = fontMetricsHeight;
        if (lineHeight <= 0) {
          lineHeight = 12;
        }
      }
      assert lineHeight > 0 : lineHeight;
      myLineHeight = lineHeight;
    }
    return lineHeight;
  }

  public int getDescent() {
    if (myUseNewRendering) return myView.getDescent();
    if (myDescent != -1) {
      return myDescent;
    }
    FontMetrics fontMetrics = myEditorComponent.getFontMetrics(myScheme.getFont(EditorFontType.PLAIN));
    myDescent = fontMetrics.getDescent();
    return myDescent;
  }

  @NotNull
  public FontMetrics getFontMetrics(@JdkConstants.FontStyle int fontType) {
    if (myPlainFontMetrics == null) {
      assertIsDispatchThread();
      myPlainFontMetrics = myEditorComponent.getFontMetrics(myScheme.getFont(EditorFontType.PLAIN));
      myBoldFontMetrics = myEditorComponent.getFontMetrics(myScheme.getFont(EditorFontType.BOLD));
      myItalicFontMetrics = myEditorComponent.getFontMetrics(myScheme.getFont(EditorFontType.ITALIC));
      myBoldItalicFontMetrics = myEditorComponent.getFontMetrics(myScheme.getFont(EditorFontType.BOLD_ITALIC));
    }

    if (fontType == Font.PLAIN) return myPlainFontMetrics;
    if (fontType == Font.BOLD) return myBoldFontMetrics;
    if (fontType == Font.ITALIC) return myItalicFontMetrics;
    if (fontType == (Font.BOLD | Font.ITALIC)) return myBoldItalicFontMetrics;

    LOG.error("Unknown font type: " + fontType);

    return myPlainFontMetrics;
  }

  private int getCharHeight() {
    if (myUseNewRendering) return myView.getCharHeight();
    if (myCharHeight == -1) {
      assertIsDispatchThread();
      FontMetrics fontMetrics = myEditorComponent.getFontMetrics(myScheme.getFont(EditorFontType.PLAIN));
      myCharHeight = fontMetrics.charWidth('a');
    }
    return myCharHeight;
  }

  public int getPreferredHeight() {
    if (myUseNewRendering) return isReleased ? 0 : myView.getPreferredHeight();
    if (ourIsUnitTestMode && getUserData(DO_DOCUMENT_UPDATE_TEST) == null) {
      return 1;
    }

    if (isOneLineMode()) return getLineHeight();

    // Preferred height of less than a single line height doesn't make sense:
    // at least a single line with a blinking caret on it is to be displayed
    int size = Math.max(getVisibleLineCount(), 1) * getLineHeight();

    if (mySettings.isAdditionalPageAtBottom()) {
      int lineHeight = getLineHeight();
      int visibleAreaHeight = getScrollingModel().getVisibleArea().height;
      // There is a possible case that user with 'show additional page at bottom' scrolls to that virtual page; switched to another
      // editor (another tab); and then returns to the previously used editor (the one scrolled to virtual page). We want to preserve
      // correct view size then because viewport position is set to the end of the original text otherwise.
      if (visibleAreaHeight > 0 || myVirtualPageHeight <= 0) {
        myVirtualPageHeight = Math.max(visibleAreaHeight - 2 * lineHeight, lineHeight);
      }

      return size + Math.max(myVirtualPageHeight, 0);
    }

    return size + mySettings.getAdditionalLinesCount() * getLineHeight();
  }

  public Dimension getPreferredSize() {
    if (myUseNewRendering) return isReleased ? new Dimension() : myView.getPreferredSize();
    if (ourIsUnitTestMode && getUserData(DO_DOCUMENT_UPDATE_TEST) == null) {
      return new Dimension(1, 1);
    }

    final Dimension draft = getSizeWithoutCaret();
    final int additionalSpace = shouldRespectAdditionalColumns()
                                ? mySettings.getAdditionalColumnsCount() * EditorUtil.getSpaceWidth(Font.PLAIN, this)
                                : 0;

    if (!myDocument.isInBulkUpdate()) {
      for (Caret caret : myCaretModel.getAllCarets()) {
        if (caret.isUpToDate()) {
          int caretX = visualPositionToXY(caret.getVisualPosition()).x;
          draft.width = Math.max(caretX, draft.width);
        }
      }
    }
    draft.width += additionalSpace;
    return draft;
  }

  private boolean shouldRespectAdditionalColumns() {
    return !mySoftWrapModel.isSoftWrappingEnabled()
           || mySoftWrapModel.isRespectAdditionalColumns()
           || mySizeContainer.getContentSize().getWidth() > myScrollingModel.getVisibleArea().getWidth();
  }

  private Dimension getSizeWithoutCaret() {
    Dimension size = mySizeContainer.getContentSize();
    return new Dimension(size.width, getPreferredHeight());
  }

  @NotNull
  @Override
  public Dimension getContentSize() {
    if (myUseNewRendering) return myView.getPreferredSize();
    Dimension size = mySizeContainer.getContentSize();
    return new Dimension(size.width, size.height + mySettings.getAdditionalLinesCount() * getLineHeight());
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
    return logicalPositionToOffset(pos, true);
  }

  public int logicalPositionToOffset(@NotNull LogicalPosition pos, boolean softWrapAware) {
    if (myUseNewRendering) return myView.logicalPositionToOffset(pos);
    if (softWrapAware) {
      return mySoftWrapModel.logicalPositionToOffset(pos);
    }
    assertReadAccess();
    if (myDocument.getLineCount() == 0) return 0;

    if (pos.line < 0) throw new IndexOutOfBoundsException("Wrong line: " + pos.line);
    if (pos.column < 0) throw new IndexOutOfBoundsException("Wrong column:" + pos.column);

    if (pos.line >= myDocument.getLineCount()) {
      return myDocument.getTextLength();
    }

    int start = myDocument.getLineStartOffset(pos.line);
    if (pos.column == 0) return start;
    int end = myDocument.getLineEndOffset(pos.line);

    int x = getDocument().getLineNumber(start) == 0 ? getPrefixTextWidthInPixels() : 0;

    int result = EditorUtil.calcSoftWrapUnawareOffset(this, myDocument.getImmutableCharSequence(), start, end, pos.column,
                                                      EditorUtil.getTabSize(this), x, new int[]{0}, null);
    if (result >= 0) {
      return result;
    }

    return end;
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
    return logicalToVisualPosition(logicalPos, true);
  }

  @NotNull
  public VisualPosition logicalToVisualPosition(@NotNull LogicalPosition logicalPos, boolean softWrapAware) {
    if (myUseNewRendering) return myView.logicalToVisualPosition(logicalPos, false);
    return doLogicalToVisualPosition(logicalPos, softWrapAware,0);
  }

  @NotNull
  private VisualPosition doLogicalToVisualPosition(@NotNull LogicalPosition logicalPos, boolean softWrapAware,
                                                   // TODO den remove as soon as the problem is fixed.
                                                   int stackDepth) {
    assertReadAccess();
    if (!myFoldingModel.isFoldingEnabled() && !mySoftWrapModel.isSoftWrappingEnabled()) {
      return new VisualPosition(logicalPos.line, logicalPos.column);
    }

    int offset = logicalPositionToOffset(logicalPos);

    FoldRegion outermostCollapsed = myFoldingModel.getCollapsedRegionAtOffset(offset);
    if (outermostCollapsed != null && offset > outermostCollapsed.getStartOffset()) {
      if (offset < getDocument().getTextLength()) {
        offset = outermostCollapsed.getStartOffset();
        LogicalPosition foldStart = offsetToLogicalPosition(offset);
        // TODO den remove as soon as the problem is fixed.
        if (stackDepth > 15) {
          LOG.error("Detected potential StackOverflowError at logical->visual position mapping. Given logical position: '" +
                    logicalPos + "'. State: " + dumpState());
          stackDepth = -1;
        }
        return doLogicalToVisualPosition(foldStart, true, stackDepth+1);
      }
      else {
        offset = outermostCollapsed.getEndOffset() + 3;  // WTF?
      }
    }

    int line = logicalPos.line;
    int column = logicalPos.column;

    int foldedLinesCountBefore = myFoldingModel.getFoldedLinesCountBefore(offset);
    line -= foldedLinesCountBefore;
    if (line < 0) {
      LogMessageEx.error(
        LOG, "Invalid LogicalPosition -> VisualPosition processing", String.format(
        "Given logical position: %s; matched line: %d; fold lines before: %d, state: %s",
        logicalPos, line, foldedLinesCountBefore, dumpState()
      ));
    }

    FoldRegion[] topLevel = myFoldingModel.fetchTopLevel();
    LogicalPosition anchorFoldingPosition = logicalPos;
    for (int idx = myFoldingModel.getLastCollapsedRegionBefore(offset); idx >= 0 && topLevel != null; idx--) {
      FoldRegion region = topLevel[idx];
      if (region.isValid()) {
        if (region.getDocument().getLineNumber(region.getEndOffset()) == anchorFoldingPosition.line && region.getEndOffset() <= offset) {
          LogicalPosition foldStart = offsetToLogicalPosition(region.getStartOffset());
          LogicalPosition foldEnd = offsetToLogicalPosition(region.getEndOffset());
          column += foldStart.column + region.getPlaceholderText().length() - foldEnd.column;
          offset = region.getStartOffset();
          anchorFoldingPosition = foldStart;
        }
        else {
          break;
        }
      }
    }

    VisualPosition softWrapUnawarePosition = new VisualPosition(line, Math.max(0, column));
    if (softWrapAware) {
      return mySoftWrapModel.adjustVisualPosition(logicalPos, softWrapUnawarePosition);
    }
    return softWrapUnawarePosition;
  }

  @Nullable
  private FoldRegion getLastCollapsedBeforePosition(@NotNull VisualPosition visualPos) {
    FoldRegion[] topLevelCollapsed = myFoldingModel.fetchTopLevel();

    if (topLevelCollapsed == null) return null;

    int start = 0;
    int end = topLevelCollapsed.length - 1;
    int i = 0;

    while (start <= end) {
      i = (start + end) / 2;
      FoldRegion region = topLevelCollapsed[i];
      if (!region.isValid()) {
        // Folding model is inconsistent (update in progress).
        return null;
      }
      int regionVisualLine = offsetToVisualLine(region.getEndOffset() - 1);
      if (regionVisualLine < visualPos.line) {
        start = i + 1;
      }
      else {
        if (regionVisualLine > visualPos.line) {
          end = i - 1;
        }
        else {
          VisualPosition visFoldEnd = offsetToVisualPosition(region.getEndOffset() - 1);
          if (visFoldEnd.column < visualPos.column) {
            start = i + 1;
          }
          else {
            if (visFoldEnd.column > visualPos.column) {
              end = i - 1;
            }
            else {
              i--;
              break;
            }
          }
        }
      }
    }

    while (i >= 0 && i < topLevelCollapsed.length) {
      if (topLevelCollapsed[i].isValid()) break;
      i--;
    }

    if (i >= 0 && i < topLevelCollapsed.length) {
      FoldRegion region = topLevelCollapsed[i];
      VisualPosition visFoldEnd = offsetToVisualPosition(region.getEndOffset() - 1);
      if (visFoldEnd.line > visualPos.line || visFoldEnd.line == visualPos.line && visFoldEnd.column > visualPos.column) {
        i--;
        if (i >= 0) {
          return topLevelCollapsed[i];
        }
        return null;
      }
      return region;
    }

    return null;
  }

  @Override
  @NotNull
  public LogicalPosition visualToLogicalPosition(@NotNull VisualPosition visiblePos) {
    return visualToLogicalPosition(visiblePos, true);
  }

  @NotNull
  public LogicalPosition visualToLogicalPosition(@NotNull VisualPosition visiblePos, boolean softWrapAware) {
    if (myUseNewRendering) return myView.visualToLogicalPosition(visiblePos);
    assertReadAccess();
    if (softWrapAware) {
      return mySoftWrapModel.visualToLogicalPosition(visiblePos);
    }
    if (!myFoldingModel.isFoldingEnabled()) return new LogicalPosition(visiblePos.line, visiblePos.column);

    int line = visiblePos.line;
    int column = visiblePos.column;

    FoldRegion lastCollapsedBefore = getLastCollapsedBeforePosition(visiblePos);

    if (lastCollapsedBefore != null) {
      int logFoldEndLine = offsetToLogicalLine(lastCollapsedBefore.getEndOffset());
      int visFoldEndLine = logicalToVisualLine(logFoldEndLine);

      line = logFoldEndLine + visiblePos.line - visFoldEndLine;
      if (visFoldEndLine == visiblePos.line) {
        LogicalPosition logFoldEnd = offsetToLogicalPosition(lastCollapsedBefore.getEndOffset(), false);
        VisualPosition visFoldEnd = logicalToVisualPosition(logFoldEnd, false);
        if (visiblePos.column >= visFoldEnd.column) {
          column = logFoldEnd.column + visiblePos.column - visFoldEnd.column;
        }
        else {
          return offsetToLogicalPosition(lastCollapsedBefore.getStartOffset(), false);
        }
      }
    }

    if (column < 0) column = 0;

    return new LogicalPosition(line, column);
  }

  int offsetToLogicalLine(int offset) {
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
    return calcColumnNumber(offset, lineIndex, true, myDocument.getImmutableCharSequence());
  }

  public int calcColumnNumber(int offset, int lineIndex, boolean softWrapAware, @NotNull CharSequence documentCharSequence) {
    if (myUseNewRendering) return myView.offsetToLogicalPosition(offset).column;
    if (myDocument.getTextLength() == 0) return 0;

    int lineStartOffset = myDocument.getLineStartOffset(lineIndex);
    if (lineStartOffset == offset) return 0;
    int lineEndOffset = myDocument.getLineEndOffset(lineIndex);
    if (lineEndOffset < offset) offset = lineEndOffset; // handling the case when offset is inside non-normalized line terminator
    int column = EditorUtil.calcColumnNumber(this, documentCharSequence, lineStartOffset, offset);

    if (softWrapAware) {
      int line = offsetToLogicalLine(offset);
      return mySoftWrapModel.adjustLogicalPosition(new LogicalPosition(line, column), offset).column;
    }
    else {
      return column;
    }
  }

  private LogicalPosition getLogicalPositionForScreenPos(int x, int y, boolean trimToLineWidth) {
    if (x < 0) {
      x = 0;
    }

    LogicalPosition pos = xyToLogicalPosition(new Point(x, y));

    int column = pos.column;
    int line = pos.line;
    int softWrapLinesBeforeTargetLogicalLine = pos.softWrapLinesBeforeCurrentLogicalLine;
    int softWrapLinesOnTargetLogicalLine = pos.softWrapLinesOnCurrentLogicalLine;
    int softWrapColumns = pos.softWrapColumnDiff;
    boolean leansForward = pos.leansForward;
    boolean leansRight = pos.visualPositionLeansRight;

    final int totalLines = myDocument.getLineCount();
    if (totalLines <= 0) {
      return new LogicalPosition(0, 0);
    }

    if (line >= totalLines && totalLines > 0) {
      int visibleLineCount = getVisibleLineCount();
      int newY = visibleLineCount > 0 ? visibleLineToY(visibleLineCount - 1) : 0;
      if (newY > 0 && newY == y) {
        newY = visibleLineToY(getVisibleLogicalLinesCount());
      }
      if (newY >= y) {
        LogMessageEx.error(LOG, "cycled moveCaretToScreenPos() detected",
                           String.format("x=%d, y=%d\nvisibleLineCount=%d, newY=%d\nstate=%s", x, y, visibleLineCount, newY, dumpState()));
        throw new IllegalStateException("cycled moveCaretToScreenPos() detected");
      }
      return getLogicalPositionForScreenPos(x, newY, trimToLineWidth);
    }

    if (!mySettings.isVirtualSpace() && trimToLineWidth) {
      int lineEndOffset = myDocument.getLineEndOffset(line);
      int lineEndColumn = calcColumnNumber(lineEndOffset, line);
      if (column > lineEndColumn) {
        column = lineEndColumn;
        leansForward = true;
        leansRight = true;
        if (softWrapColumns != 0) {
          softWrapColumns -= column - lineEndColumn;
        }
      }
    }

    if (!mySettings.isCaretInsideTabs()) {
      int offset = logicalPositionToOffset(new LogicalPosition(line, column));
      CharSequence text = myDocument.getImmutableCharSequence();
      if (offset >= 0 && offset < myDocument.getTextLength()) {
        if (text.charAt(offset) == '\t') {
          column = calcColumnNumber(offset, line);
        }
      }
    }
    return pos.visualPositionAware ?
           new LogicalPosition(
             line, column, softWrapLinesBeforeTargetLogicalLine, softWrapLinesOnTargetLogicalLine, softWrapColumns,
             pos.foldedLines, pos.foldingColumnDiff, leansForward, leansRight
           ) :
           new LogicalPosition(line, column, leansForward);
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

  private boolean checkIgnore(@NotNull MouseEvent e, boolean isFinalCheck) {
    if (!myIgnoreMouseEventsConsecutiveToInitial) {
      myInitialMouseEvent = null;
      return false;
    }

    if (myInitialMouseEvent!= null && (e.getComponent() != myInitialMouseEvent.getComponent() || !e.getPoint().equals(myInitialMouseEvent.getPoint()))) {
      myIgnoreMouseEventsConsecutiveToInitial = false;
      myInitialMouseEvent = null;
      return false;
    }

    if (isFinalCheck) {
      myIgnoreMouseEventsConsecutiveToInitial = false;
      myInitialMouseEvent = null;
    }

    e.consume();

    return true;
  }

  private void processMouseReleased(@NotNull MouseEvent e) {
    if (checkIgnore(e, true)) return;

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

    return new DataContext() {
      @Override
      public Object getData(String dataId) {
        if (CommonDataKeys.PROJECT.is(dataId)) {
          return myProject;
        }
        return original.getData(dataId);
      }
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
          myEditorComponent.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
          return;
        }
      }
      if (!IdeGlassPaneImpl.hasPreProcessedCursor(myEditorComponent)) {
        myEditorComponent.setCursor(UIUtil.getTextCursor(getBackgroundColor()));
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
      VisualPosition newVisualCaret = myUseNewRendering ? getTargetPosition(x, y, !multiCaretSelection) : null;
      LogicalPosition newLogicalCaret = myUseNewRendering ? visualToLogicalPosition(newVisualCaret) : 
                                        getLogicalPositionForScreenPos(x, y, !multiCaretSelection);
      if (multiCaretSelection) {
        myMultiSelectionInProgress = true;
        myRectangularSelectionInProgress = columnSelectionDrag || addRectangularSelectionEvent;
        myTargetMultiSelectionPosition = xyToVisualPosition(new Point(Math.max(x, 0), Math.max(y, 0)));
      }
      else {
        if (myUseNewRendering) {
          getCaretModel().moveToVisualPosition(newVisualCaret);
        }
        else {
          getCaretModel().moveToLogicalPosition(newLogicalCaret);
        }
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
    @NotNull private final MyRepaintRunnable myRepaintRunnable = new MyRepaintRunnable();
    private ScheduledFuture<?> mySchedulerHandle;

    private class MyRepaintRunnable implements Runnable {
      @Override
      public void run() {
        if (myEditor != null) {
          myEditor.myCaretCursor.repaint();
        }
      }
    }

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
      Point pos1 = visualPositionToXY(caretPosition);
      Point pos2 = visualPositionToXY(new VisualPosition(caretPosition.line, Math.max(0, caretPosition.column + (isRtl ? -1 : 1))));
      caretPoints.add(new CaretRectangle(pos1, Math.abs(pos2.x - pos1.x), caret, isRtl));
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
      if (!myUseNewRendering) {
        repaint();
      }
    }

    private void repaint() {
      if (myUseNewRendering) {
        myView.repaintCarets();
      }
      else {
        for (CaretRectangle location : myLocations) {
          myEditorComponent.repaintEditorComponent(location.myPoint.x, location.myPoint.y, location.myWidth, getLineHeight());
        }
      }
    }

    @Nullable
    CaretRectangle[] getCaretLocations(boolean onlyIfShown) {
      if (onlyIfShown && (!isEnabled() || !myIsShown || isRendererMode() || !IJSwingUtilities.hasFocus(getContentComponent()))) return null;
      return myLocations;
    }    

    private void paint(@NotNull Graphics g) {
      CaretRectangle[] locations = getCaretLocations(true);
      if (locations == null) return;

      for (CaretRectangle location : myLocations) {
        paintAt(g, location.myPoint.x, location.myPoint.y, location.myWidth, location.myCaret);
      }
    }

    void paintAt(@NotNull Graphics g, int x, int y, int width, Caret caret) {
      int lineHeight = getLineHeight();

      Rectangle viewRectangle = getScrollingModel().getVisibleArea();
      if (x - viewRectangle.x < 0) {
        return;
      }


      g.setColor(myScheme.getColor(EditorColors.CARET_COLOR));

      Graphics2D originalG = IdeBackgroundUtil.getOriginalGraphics(g);
      if (!paintBlockCaret()) {
        if (UIUtil.isRetina()) {
          originalG.fillRect(x, y, mySettings.getLineCursorWidth(), lineHeight);
        }
        else {
          g.fillRect(x, y, JBUI.scale(mySettings.getLineCursorWidth()), lineHeight);
        }
      }
      else {
        Color caretColor = myScheme.getColor(EditorColors.CARET_COLOR);
        if (caretColor == null) caretColor = new JBColor(Gray._0, Gray._255);
        g.setColor(caretColor);
        originalG.fillRect(x, y, width, lineHeight - 1);
        final LogicalPosition startPosition = caret == null ? getCaretModel().getLogicalPosition() : caret.getLogicalPosition();
        final int offset = logicalPositionToOffset(startPosition);
        CharSequence chars = myDocument.getImmutableCharSequence();
        if (chars.length() > offset && myDocument.getTextLength() > offset) {
          FoldRegion folding = myFoldingModel.getCollapsedRegionAtOffset(offset);
          final char ch;
          if (folding == null || folding.isExpanded()) {
            ch = chars.charAt(offset);
          }
          else {
            VisualPosition visual = caret == null ? getCaretModel().getVisualPosition() : caret.getVisualPosition();
            VisualPosition foldingPosition = offsetToVisualPosition(folding.getStartOffset());
            if (visual.line == foldingPosition.line) {
              ch = folding.getPlaceholderText().charAt(visual.column - foldingPosition.column);
            }
            else {
              ch = chars.charAt(offset);
            }
          }
          //don't worry it's cheap. Cache is not required
          IterationState state = new IterationState(EditorImpl.this, offset, offset + 1, true);
          TextAttributes attributes = state.getMergedAttributes();
          FontInfo info = EditorUtil.fontForChar(ch, attributes.getFontType(), EditorImpl.this);
          g.setFont(info.getFont());
          //todo[kb]
          //in case of italic style we paint out of the cursor block. Painting the symbol to a dedicated buffered image
          //solves the problem, but still looks weird because it leaves colored pixels at right.
          g.setColor(ColorUtil.isDark(caretColor) ? CURSOR_FOREGROUND_LIGHT : CURSOR_FOREGROUND_DARK);
          g.drawChars(new char[]{ch}, 0, 1, x, y + getAscent());
        }
      }
    }
  }

  private boolean paintBlockCaret() {
    return myIsInsertMode == mySettings.isBlockCursor();
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


      myTimer = UIUtil.createNamedTimer("Editor scroll timer", TIMER_PERIOD, new ActionListener() {
        @Override
        public void actionPerformed(@NotNull ActionEvent e) {
          myCommandProcessor.executeCommand(myProject, new DocumentRunnable(myDocument, myProject) {
            @Override
            public void run() {
              // We experienced situation when particular editor was disposed but the timer was still on.
              if (isDisposed()) {
                myTimer.stop();
                return;
              }
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
        }
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

  private static final Field decrButtonField = ReflectionUtil.getDeclaredField(BasicScrollBarUI.class, "decrButton");
  private static final Field incrButtonField = ReflectionUtil.getDeclaredField(BasicScrollBarUI.class, "incrButton");

  class MyScrollBar extends JBScrollBar implements IdeGlassPane.TopComponent {
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
    public boolean canBePreprocessed(MouseEvent e) {
      return JBScrollPane.canBePreprocessed(e, this);
    }

    @Override
    public void setUI(ScrollBarUI ui) {
      if (myPersistentUI == null) myPersistentUI = ui;
      super.setUI(myPersistentUI);
      setOpaque(false);
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

  public static BasicScrollBarUI createEditorScrollbarUI(@NotNull EditorImpl editor) {
    return new EditorScrollBarUI(editor);
  }

  private static class EditorScrollBarUI extends ButtonlessScrollBarUI.Transparent {
    @NotNull private final EditorImpl myEditor;

    public EditorScrollBarUI(@NotNull EditorImpl editor) {
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

  private class MyEditable implements CutProvider, CopyProvider, PasteProvider, DeleteProvider {
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
    if (Registry.is("ide.scroll.new.layout")) {
      myScrollPane.putClientProperty(JBScrollPane.Flip.class,
                                     type == VERTICAL_SCROLLBAR_LEFT
                                     ? JBScrollPane.Flip.HORIZONTAL
                                     : null);
      JScrollBar vsb = myScrollPane.getVerticalScrollBar();
      if (vsb != null) vsb.setOpaque(true);
    }
    else if (type == VERTICAL_SCROLLBAR_LEFT) {
      myScrollPane.setLayout(new LeftHandScrollbarLayout());
    }
    else {
      myScrollPane.setLayout(new ScrollPaneLayout());
    }
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

  public EditorDropHandler getDropHandler() {
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

  public boolean isHighlighterAvailable(@NotNull RangeHighlighter highlighter) {
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
      return execute(() -> myDelegate.getInsertPositionOffset());
    }

    @NotNull
    @Override
    public AttributedCharacterIterator getCommittedText(final int beginIndex, final int endIndex,
                                                        final AttributedCharacterIterator.Attribute[] attributes) {
      return execute(() -> myDelegate.getCommittedText(beginIndex, endIndex, attributes));
    }

    @Override
    public int getCommittedTextLength() {
      return execute(() -> myDelegate.getCommittedTextLength());
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
      Point p = getContentComponent().getLocationOnScreen();
      r.translate(p.x, p.y);

      return r;
    }

    @Override
    @Nullable
    public TextHitInfo getLocationOffset(int x, int y) {
      if (composedText != null) {
        Point p = getContentComponent().getLocationOnScreen();
        p.x = x - p.x;
        p.y = y - p.y;
        int pos = logicalPositionToOffset(xyToLogicalPosition(p));
        if (composedTextRange.containsOffset(pos)) {
          return TextHitInfo.leading(pos - composedTextRange.getStartOffset());
        }
      }
      return null;
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
        getCaretModel().runForEachCaret(new CaretAction() {
          @Override
          public void perform(Caret caret) {
            int caretOffset = caret.getOffset();
            if (caretOffset > 0) {
              caret.setSelection(caretOffset - 1, caretOffset);
            }
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
      myCaretStateBeforeLastPress = isToggleCaretEvent(e) ? myCaretModel.getCaretsAndSelections() : Collections.<CaretState>emptyList();
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
      if (e.isConsumed()) {
        return;
      }

      myScrollingTimer.stop();
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
        VisualPosition visualPosition = myUseNewRendering ? getTargetPosition(x, y, true) : null;
        LogicalPosition pos = myUseNewRendering ? visualToLogicalPosition(visualPosition) : getLogicalPositionForScreenPos(x, y, true);
        if (toggleCaret) {
          if (!myUseNewRendering) {
            visualPosition = logicalToVisualPosition(pos);
          }
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
            if (myUseNewRendering) {
              getCaretModel().moveToVisualPosition(visualPosition);
            }
            else {
              getCaretModel().moveToLogicalPosition(pos);
            }
          }
        }
        else if (myCaretModel.supportsMultipleCarets() && e.getSource() != myGutterComponent && isCreateRectangularSelectionEvent(e)) {
          mySelectionModel.setBlockSelection(myCaretModel.getLogicalPosition(), pos);
        }
        else {
          getCaretModel().removeSecondaryCarets();
          if (myUseNewRendering) {
            getCaretModel().moveToVisualPosition(visualPosition);
          }
          else {
            getCaretModel().moveToLogicalPosition(pos);
          }
        }
      }

      if (e.isPopupTrigger()) return false;

      requestFocus();

      int caretOffset = getCaretModel().getOffset();

      int newStart = mySelectionModel.getSelectionStart();
      int newEnd = mySelectionModel.getSelectionEnd();
      
      boolean isNavigation = oldStart == oldEnd && newStart == newEnd && oldStart != newStart;

      myMouseSelectedRegion = myFoldingModel.getFoldingPlaceholderAt(new Point(x, y));
      myMousePressedInsideSelection = mySelectionModel.hasSelection() && caretOffset >= mySelectionModel.getSelectionStart() &&
                                      caretOffset <= mySelectionModel.getSelectionEnd();

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
    return KeymapUtil.isMouseActionEvent(e, IdeActions.ACTION_EDITOR_ADD_OR_REMOVE_CARET) || isAddRectangularSelectionEvent(e);
  }

  private static boolean isAddRectangularSelectionEvent(@NotNull MouseEvent e) {
    return KeymapUtil.isMouseActionEvent(e, IdeActions.ACTION_EDITOR_ADD_RECTANGULAR_SELECTION_ON_MOUSE_DRAG);
  }

  private static boolean isCreateRectangularSelectionEvent(@NotNull MouseEvent e) {
    return KeymapUtil.isMouseActionEvent(e, IdeActions.ACTION_EDITOR_CREATE_RECTANGULAR_SELECTION);
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
   * @return <code>true</code> if action that produces given event will trigger editor selection change; <code>false</code> otherwise
   */
  private boolean tweakSelectionEvent(@NotNull MouseEvent e) {
    return getSelectionModel().hasSelection() && e.getButton() == MouseEvent.BUTTON1 && e.isShiftDown()
           && getMouseEventArea(e) == EditorMouseEventArea.LINE_NUMBERS_AREA;
  }

  /**
   * Checks if editor selection should be changed because of click at the given point at gutter and proceeds if necessary.
   * <p/>
   * The main idea is that selection can be changed during left mouse clicks on the gutter line numbers area with hold
   * <code>Shift</code> button. The selection should be adjusted if necessary.
   *
   * @param e event for mouse click on gutter area
   * @return <code>true</code> if editor's selection is changed because of the click; <code>false</code> otherwise
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

  public boolean useEditorAntialiasing() {
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
      runMouseDraggedCommand(e);
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
    private final FontPreferences myFontPreferences = new FontPreferences();
    private final FontPreferences myConsoleFontPreferences = new FontPreferences();
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

    private void updatePreferences(FontPreferences preferences, String fontName, int fontSize, FontPreferences delegatePreferences) {
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
      preferences.setUseLigatures(delegatePreferences == null ? false : delegatePreferences.useLigatures());
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
    public void setAttributes(TextAttributesKey key, TextAttributes attributes) {
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
      clearSettingsCache();
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

  static boolean handleDrop(@NotNull EditorImpl editor, @NotNull final Transferable t) {
    final EditorDropHandler dropHandler = editor.getDropHandler();
    if (dropHandler != null && dropHandler.canHandleDrop(t.getTransferDataFlavors())) {
      dropHandler.handleDrop(t, editor.getProject(), null);
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

    CommandProcessor.getInstance().executeCommand(editor.myProject, () -> ApplicationManager.getApplication().runWriteAction(() -> {
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
        LOG.assertTrue(pasteHandler instanceof EditorTextInsertHandler);
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
    }), EditorBundle.message("paste.command.name"), DND_COMMAND_KEY, UndoConfirmationPolicy.DEFAULT, editor.getDocument());

    return true;
  }

  private static class MyTransferHandler extends TransferHandler {
    private static EditorImpl getEditor(@NotNull JComponent comp) {
      EditorComponentImpl editorComponent = (EditorComponentImpl)comp;
      return editorComponent.getEditor();
    }

    @Override
    public boolean importData(@NotNull final JComponent comp, @NotNull final Transferable t) {
      return handleDrop(getEditor(comp), t);
    }
    
    @Override
    public boolean canImport(@NotNull JComponent comp, @NotNull DataFlavor[] transferFlavors) {
      Editor editor = getEditor(comp);
      final EditorDropHandler dropHandler = ((EditorImpl)editor).getDropHandler();
      if (dropHandler != null && dropHandler.canHandleDrop(transferFlavors)) {
        return true;
      }
      if (editor.isViewer()) return false;

      int offset = editor.getCaretModel().getOffset();
      if (editor.getDocument().getRangeGuard(offset, offset) != null) return false;

      for (DataFlavor transferFlavor : transferFlavors) {
        if (transferFlavor.equals(DataFlavor.stringFlavor)) return true;
      }

      return false;
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

      editor.clearDnDContext();
    }
  }

  private class EditorDocumentAdapter implements PrioritizedDocumentListener {
    @Override
    public void beforeDocumentChange(@NotNull DocumentEvent e) {
      beforeChangedUpdate(e);
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

  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private class EditorSizeContainer {
    /**
     * Holds logical line widths in pixels.
     */
    private TIntArrayList myLineWidths;
    private int maxCalculatedLine = -1;

    /**
     * Holds value that indicates if line widths recalculation should be performed.
     */
    private volatile boolean myIsDirty;

    /**
     * Holds number of the last logical line affected by the last document change.
     */
    private int myOldEndLine;

    private Dimension mySize;
    private int myMaxWidth = -1;

    public synchronized void reset() {
      int lineCount = getDocument().getLineCount();
      myLineWidths = new TIntArrayList(lineCount + 300);
      insertNewLines(lineCount, 0);
      maxCalculatedLine = -1;
      myIsDirty = true;
    }

    private void insertNewLines(int lineCount, int index) {
      int[] values = new int[lineCount];
      Arrays.fill(values, -1);
      myLineWidths.insert(index, values);
      if (index <= maxCalculatedLine) {
        maxCalculatedLine += lineCount;
      }
    }

    @SuppressWarnings("NonPrivateFieldAccessedInSynchronizedContext")
    public synchronized void beforeChange(@NotNull DocumentEvent e) {
      if (myDocument.isInBulkUpdate()) {
        myMaxWidth = mySize == null ? -1 : mySize.width;
      }

      myOldEndLine = offsetToLogicalLine(e.getOffset() + e.getOldLength());
    }

    /**
     * Notifies current size container about document content change.
     * <p/>
     * Every change is assumed to be identified by three characteristics - start, ole end and new end lines.
     * <b>Example:</b>
     * <pre>
     * <ol>
     *   <li>
     *      Consider that we have the following document initially:
     *      <pre>
     *        line 1
     *        line 2
     *        line 3
     *      </pre>
     *   </li>
     *   <li>
     *      Let's assume that the user selected the last two lines and typed 'new line' (that effectively removed selected text).
     *      Current document state:
     *      <pre>
     *        line 1
     *        new line
     *      </pre>
     *   </li>
     *   <li>
     *      Current method is expected to be called with the following parameters:
     *          <ul>
     *            <li><b>startLine</b> is 1'</li>
     *            <li><b>oldEndLine</b> is 2'</li>
     *            <li><b>newEndLine</b> is 1'</li>
     *          </ul>
     *   </li>
     * </ol>
     * </pre>
     *
     * @param startLine  logical line that contains changed fragment start offset
     * @param newEndLine logical line that contains changed fragment end
     * @param oldEndLine logical line that contained changed fragment end
     */
    public synchronized void update(int startLine, int newEndLine, int oldEndLine) {
      final int lineWidthSize = myLineWidths.size();
      if (lineWidthSize == 0 || myDocument.getTextLength() <= 0) {
        reset();
      }
      else {
        final int min = Math.min(oldEndLine, newEndLine);
        final boolean toAddNewLines = min >= lineWidthSize;

        if (toAddNewLines) {
          insertNewLines(min - lineWidthSize + 1, lineWidthSize);
        }

        for (int i = min; i > startLine - 1; i--) {
          myLineWidths.set(i, -1);
          if (maxCalculatedLine == i) maxCalculatedLine--;
        }
        if (newEndLine > oldEndLine) {
          insertNewLines(newEndLine - oldEndLine, oldEndLine + 1);
        }
        else if (oldEndLine > newEndLine && !toAddNewLines && newEndLine + 1 < lineWidthSize) {
          int length = Math.min(oldEndLine, lineWidthSize) - newEndLine - 1;
          int index = newEndLine + 1;
          myLineWidths.remove(index, length);
          if (index <= maxCalculatedLine) {
            maxCalculatedLine -= length;
          }
        }
        myIsDirty = true;
      }
    }

    /**
     * Notifies current container about visual width change of the target logical line.
     * <p/>
     * Please note that there is a possible case that particular logical line is represented in more than one visual lines,
     * hence, this method may be called multiple times with the same logical line argument but different with values. Current
     * container is expected to store max of the given values then.
     *
     * @param logicalLine   logical line which visual width is changed
     * @param widthInPixels visual width of the given logical line
     */
    public synchronized void updateLineWidthIfNecessary(int logicalLine, int widthInPixels) {
      if (logicalLine < myLineWidths.size()) {
        int currentWidth = myLineWidths.get(logicalLine);
        if (widthInPixels > currentWidth) {
          myLineWidths.set(logicalLine, widthInPixels);
        }
        if (widthInPixels > myMaxWidth) {
          myMaxWidth = widthInPixels;
        }
        maxCalculatedLine = Math.max(maxCalculatedLine, logicalLine);
      }
    }

    public synchronized void changedUpdate(@NotNull DocumentEvent e) {
      int startLine = e.getOldLength() == 0 ? myOldEndLine : myDocument.getLineNumber(e.getOffset());
      int newEndLine = e.getNewLength() == 0 ? startLine : myDocument.getLineNumber(e.getOffset() + e.getNewLength());
      int oldEndLine = myOldEndLine;

      update(startLine, newEndLine, oldEndLine);
    }

    @SuppressWarnings({"NonPrivateFieldAccessedInSynchronizedContext", "AssignmentToForLoopParameter"})
    private void validateSizes() {
      if (!myIsDirty && !(myLinePaintersWidth > myMaxWidth)) return;

      synchronized (this) {
        if (!myIsDirty) return;
        int lineCount = Math.min(myLineWidths.size(), myDocument.getLineCount());

        if (myMaxWidth != -1 && myDocument.isInBulkUpdate()) {
          mySize = new Dimension(myMaxWidth, getLineHeight() * lineCount);
          myIsDirty = false;
          return;
        }

        final CharSequence text = myDocument.getImmutableCharSequence();
        int documentLength = myDocument.getTextLength();
        int x = 0;
        boolean lastLineLengthCalculated = false;

        List<? extends SoftWrap> softWraps = getSoftWrapModel().getRegisteredSoftWraps();
        int softWrapsIndex = -1;

        CharWidthCache charWidthCache = new CharWidthCache(EditorImpl.this);

        for (int line = 0; line < lineCount; line++) {
          if (myLineWidths.getQuick(line) != -1) continue;
          if (line == lineCount - 1) {
            lastLineLengthCalculated = true;
          }

          x = 0;
          int offset = myDocument.getLineStartOffset(line);

          if (offset >= myDocument.getTextLength()) {
            myLineWidths.set(line, 0);
            maxCalculatedLine = Math.max(maxCalculatedLine, line);
            break;
          }

          if (softWrapsIndex < 0) {
            softWrapsIndex = getSoftWrapModel().getSoftWrapIndex(offset);
            if (softWrapsIndex < 0) {
              softWrapsIndex = -softWrapsIndex - 1;
            }
          }

          int endLine;
          if (maxCalculatedLine < line + 1) {
            endLine = lineCount;
          }
          else {
            for (endLine = line + 1; endLine < maxCalculatedLine; endLine++) {
              if (myLineWidths.getQuick(endLine) != -1) {
                break;
              }
            }
          }
          int endOffset = endLine >= lineCount ? documentLength : myDocument.getLineEndOffset(endLine);
          for (
            FoldRegion region = myFoldingModel.getCollapsedRegionAtOffset(endOffset);
            region != null && endOffset < myDocument.getTextLength();
            region = myFoldingModel.getCollapsedRegionAtOffset(endOffset))
          {
            final int lineNumber = myDocument.getLineNumber(region.getEndOffset());
            endOffset = myDocument.getLineEndOffset(lineNumber);
          }
          if (endOffset > myDocument.getTextLength()) {
            break;
          }

          IterationState state = new IterationState(EditorImpl.this, offset, endOffset, false);
          int fontType = state.getMergedAttributes().getFontType();

          int maxPreviousSoftWrappedWidth = -1;

          while (offset < documentLength && line < lineCount) {
            char c = text.charAt(offset);
            if (offset >= state.getEndOffset()) {
              state.advance();
              fontType = state.getMergedAttributes().getFontType();
            }

            while (softWrapsIndex < softWraps.size() && line < lineCount) {
              SoftWrap softWrap = softWraps.get(softWrapsIndex);
              if (softWrap.getStart() > offset) {
                break;
              }
              softWrapsIndex++;
              if (softWrap.getStart() == offset) {
                maxPreviousSoftWrappedWidth = Math.max(maxPreviousSoftWrappedWidth, x);
                x = softWrap.getIndentInPixels();
              }
            }

            FoldRegion collapsed = state.getCurrentFold();
            if (collapsed != null) {
              String placeholder = collapsed.getPlaceholderText();
              for (int i = 0; i < placeholder.length(); i++) {
                x += charWidthCache.charWidth(placeholder.charAt(i), fontType);
              }
              offset = collapsed.getEndOffset();
              line = myDocument.getLineNumber(offset);
            }
            else if (c == '\t') {
              x = EditorUtil.nextTabStop(x, EditorImpl.this);
              offset++;
            }
            else if (c == '\n') {
              int width = Math.max(x, maxPreviousSoftWrappedWidth);
              myLineWidths.set(line, width);
              maxCalculatedLine = Math.max(maxCalculatedLine, line);
              if (line + 1 >= lineCount || myLineWidths.getQuick(line + 1) != -1) break;
              offset++;
              x = 0;
              //noinspection AssignmentToForLoopParameter
              line++;
              if (line == lineCount - 1) {
                lastLineLengthCalculated = true;
              }
            }
            else {
              x += charWidthCache.charWidth(c, fontType);
              offset++;
            }
          }
        }

        if (lineCount > 0 && lastLineLengthCalculated) {
          myLineWidths.set(lineCount - 1,
                           x);    // Last line can be non-zero length and won't be caught by in-loop procedure since latter only react on \n's
          maxCalculatedLine = Math.max(maxCalculatedLine, lineCount - 1);
        }

        // There is a following possible situation:
        //   1. Big document is opened at editor;
        //   2. Soft wraps are calculated for the current visible area;
        //   2. The user scrolled down;
        //   3. The user significantly reduced visible area width (say, reduced it twice);
        //   4. Soft wraps are calculated for the current visible area;
        // We need to consider only the widths for the logical lines that are completely shown at the current visible area then.
        // I.e. we shouldn't use widths of the lines that are not shown for max width calculation because previous widths are calculated
        // for another visible area width.
        int startToUse = 0;
        int endToUse = Math.min(lineCount, myLineWidths.size());
        if (endToUse > 0 && getSoftWrapModel().isSoftWrappingEnabled()) {
          Rectangle visibleArea = getScrollingModel().getVisibleArea();
          startToUse = EditorUtil.yPositionToLogicalLine(EditorImpl.this, visibleArea.getLocation());
          endToUse = Math.min(endToUse, EditorUtil.yPositionToLogicalLine(EditorImpl.this, visibleArea.y + visibleArea.height));
          if (endToUse <= startToUse) {
            // There is a possible case that there is the only soft-wrapped line, i.e. end == start. We still want to update the
            // size container's width then.
            endToUse = Math.min(myLineWidths.size(), startToUse + 1);
          }
        }
        int maxWidth = 0;
        for (int i = startToUse; i < endToUse; i++) {
          maxWidth = Math.max(maxWidth, myLineWidths.getQuick(i));
        }

        mySize = new Dimension(maxWidth, getLineHeight() * Math.max(getVisibleLineCount(), 1));

        myIsDirty = false;
      }
    }

    @NotNull
    private Dimension getContentSize() {
      validateSizes();
      return new Dimension(Math.max(mySize.width, myLinePaintersWidth), mySize.height);
    }
  }

  @Override
  @NotNull
  public EditorGutter getGutter() {
    return getGutterComponentEx();
  }

  @Override
  public int calcColumnNumber(@NotNull CharSequence text, int start, int offset, int tabSize) {
    if (myUseNewRendering) return myView.offsetToLogicalPosition(offset).column;
    IterationState state = new IterationState(this, start, offset, false);
    int fontType = state.getMergedAttributes().getFontType();
    int column = 0;
    int x = 0;
    int plainSpaceSize = EditorUtil.getSpaceWidth(Font.PLAIN, this);
    for (int i = start; i < offset; i++) {
      if (i >= state.getEndOffset()) {
        state.advance();
        fontType = state.getMergedAttributes().getFontType();
      }

      SoftWrap softWrap = getSoftWrapModel().getSoftWrap(i);
      if (softWrap != null) {
        x = softWrap.getIndentInPixels();
      }

      char c = text.charAt(i);
      if (c == '\t') {
        int prevX = x;
        x = EditorUtil.nextTabStop(x, this);
        column += EditorUtil.columnsNumber(c, x, prevX, plainSpaceSize);
      }
      else {
        x += EditorUtil.charWidth(c, fontType, this);
        column++;
      }
    }

    return column;
  }

  public boolean isInDistractionFreeMode() {
    return EditorUtil.isRealFileEditor(this)
           && (Registry.is("editor.distraction.free.mode") || isInPresentationMode());
  }

  boolean isInPresentationMode() {
    return UISettings.getInstance().PRESENTATION_MODE && EditorUtil.isRealFileEditor(this);
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
      AnAction action = CustomActionsSchema.getInstance().getCorrectedAction(myContextMenuGroupId);
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
  public void validateState() {
    myView.validateState();

    if (myDocument.isInBulkUpdate()) return;
    List<? extends SoftWrap> softWraps = mySoftWrapModel.getRegisteredSoftWraps();
    int lastSoftWrapOffset = -1;
    for (SoftWrap wrap : softWraps) {
      int softWrapOffset = wrap.getStart();
      LOG.assertTrue(softWrapOffset > lastSoftWrapOffset, "Soft wraps are not ordered");
      LOG.assertTrue(softWrapOffset < myDocument.getTextLength(), "Soft wrap is after document's end");
      FoldRegion foldRegion = myFoldingModel.getCollapsedRegionAtOffset(softWrapOffset);
      LOG.assertTrue(foldRegion == null || foldRegion.getStartOffset() == softWrapOffset, "Soft wrap is inside fold region");
      LOG.assertTrue(softWrapOffset != DocumentUtil.getLineEndOffset(softWrapOffset, myDocument)
                     || foldRegion != null, "Soft wrap before line break");
      LOG.assertTrue(softWrapOffset != DocumentUtil.getLineStartOffset(softWrapOffset, myDocument) ||
                     myFoldingModel.isOffsetCollapsed(softWrapOffset - 1), "Soft wrap after line break");
      lastSoftWrapOffset = softWrapOffset;
    }
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
      if (mySettings.isWheelFontChangeEnabled() && !MouseGestureManager.getInstance().hasTrackpad()) {
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
    public JScrollBar createVerticalScrollBar() {
      return new MyScrollBar(Adjustable.VERTICAL);
    }

    @NotNull
    @Override
    public JScrollBar createHorizontalScrollBar() {
      if (Registry.is("ide.scroll.new.layout")) {
        return super.createHorizontalScrollBar();
      }
      return new MyScrollBar(Adjustable.HORIZONTAL);
    }

    @Override
    protected void setupCorners() {
      super.setupCorners();
      setBorder(new TablessBorder());
    }

    @Override
    protected boolean isOverlaidScrollbar(@Nullable JScrollBar scrollbar) {
      ScrollBarUI vsbUI = scrollbar == null ? null : scrollbar.getUI();
      return vsbUI instanceof ButtonlessScrollBarUI && !((ButtonlessScrollBarUI)vsbUI).alwaysShowTrack();
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
      boolean thereIsSomethingAbove = !SystemInfo.isMac || UISettings.getInstance().SHOW_MAIN_TOOLBAR || UISettings.getInstance().SHOW_NAVIGATION_BAR ||
                                      toolWindowIsNotEmpty();
      //noinspection ConstantConditions
      Component header = myHeaderPanel == null ? null : ArrayUtil.getFirstElement(myHeaderPanel.getComponents());
      boolean paintTop = thereIsSomethingAbove && header == null && UISettings.getInstance().EDITOR_TAB_PLACEMENT != SwingConstants.TOP;
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

      if (height != 0) {
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
      if (myUseNewRendering) {
        myView.drawChars(g, data, start, end, x, y, color, fontInfo);
      }
      else {
        drawCharsCached(g, new CharArrayCharSequence(data), start, end, x, y, fontInfo, color, false);
      }
    }
  }

  private interface WhitespacePaintingStrategy {
    boolean showWhitespaceAtOffset(int offset);
  }

  private static final WhitespacePaintingStrategy PAINT_NO_WHITESPACE = new WhitespacePaintingStrategy() {
    @Override
    public boolean showWhitespaceAtOffset(int offset) {
      return false;
    }
  };

  // Strategy, controlled by current editor settings. Usable only for the current line.
  public class LineWhitespacePaintingStrategy implements WhitespacePaintingStrategy {
    private final boolean myWhitespaceShown = mySettings.isWhitespacesShown();
    private final boolean myLeadingWhitespaceShown = mySettings.isLeadingWhitespaceShown();
    private final boolean myInnerWhitespaceShown = mySettings.isInnerWhitespaceShown();
    private final boolean myTrailingWhitespaceShown = mySettings.isTrailingWhitespaceShown();

    // Offsets on current line where leading whitespace ends and trailing whitespace starts correspondingly.
    private int currentLeadingEdge;
    private int currentTrailingEdge;

    // Updates the state, to be used for the line, iterator is currently at.
    public void update(CharSequence chars, LineIterator iterator) {
      int lineStart = iterator.getStart();
      int lineEnd = iterator.getEnd() - iterator.getSeparatorLength();
      update(chars, lineStart, lineEnd);
    }
    
    public void update(CharSequence chars, int lineStart, int lineEnd) {
      if (myWhitespaceShown
          && (myLeadingWhitespaceShown || myInnerWhitespaceShown || myTrailingWhitespaceShown)
          && !(myLeadingWhitespaceShown && myInnerWhitespaceShown && myTrailingWhitespaceShown)) {
        currentTrailingEdge = CharArrayUtil.shiftBackward(chars, lineStart, lineEnd - 1, WHITESPACE_CHARS) + 1;
        currentLeadingEdge = CharArrayUtil.shiftForward(chars, lineStart, currentTrailingEdge, WHITESPACE_CHARS);
      }
    }

    @Override
    public boolean showWhitespaceAtOffset(int offset) {
      return myWhitespaceShown
             && (offset < currentLeadingEdge ? myLeadingWhitespaceShown :
                 offset >= currentTrailingEdge ? myTrailingWhitespaceShown :
                 myInnerWhitespaceShown);
    }
  }
}
