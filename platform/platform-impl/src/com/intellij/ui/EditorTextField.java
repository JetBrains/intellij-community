// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.ide.DataManager;
import com.intellij.ide.ui.UISettingsUtils;
import com.intellij.ide.ui.laf.darcula.ui.DarculaEditorTextFieldBorder;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.UiCompatibleDataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteIntentReadAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.impl.UndoManagerImpl;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.zoomIndicator.ZoomIndicatorManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectCloseListener;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ex.AbstractDelegatingToRootTraversalPolicy;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.toolWindow.InternalDecoratorImpl;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.dsl.builder.DslComponentProperty;
import com.intellij.ui.dsl.builder.VerticalComponentGap;
import com.intellij.ui.dsl.gridLayout.UnscaledGapsKt;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.LineSeparator;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Use {@code editor.putUserData(IncrementalFindAction.SEARCH_DISABLED, Boolean.TRUE);} to disable search/replace component.
 */
public class EditorTextField extends NonOpaquePanel implements EditorTextComponent, DocumentListener, UiCompatibleDataProvider, TextAccessor,
                                                               FocusListener, MouseListener {
  public static final Key<Boolean> SUPPLEMENTARY_KEY = Key.create("Supplementary");
  private static final Key<LineSeparator> LINE_SEPARATOR_KEY = Key.create("ETF_LINE_SEPARATOR");
  private static final Key<Boolean> MANAGED_BY_FIELD = Key.create("MANAGED_BY_FIELD");
  private static final Logger LOG = Logger.getInstance(EditorTextField.class);

  private Document myDocument;
  private final Project myProject;
  private FileType myFileType;
  private EditorEx myEditor;
  private final Set<Editor> myEditorsToBeReleased = new HashSet<>();
  private Component myNextFocusable;
  private @NotNull DeferredSelection deferredSelection = DeferredSelection.None.getInstance();
  private final List<DocumentListener> myDocumentListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final List<FocusListener> myFocusListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final List<MouseListener> myMouseListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private boolean myIsListenerInstalled;
  private boolean myIsViewer;
  private boolean myIsSupplementary;
  private boolean myInheritSwingFont = true;
  private boolean myIgnoreSetBgColor;
  private Color myEnforcedBgColor;
  private boolean myOneLineMode; // use getter to access this field! It is allowed to override getter and change initial behaviour
  private boolean myShowPlaceholderWhenFocused;
  private boolean myEnsureWillComputePreferredSize;
  private Dimension myPassivePreferredSize;
  private @Nls CharSequence myHintText;
  private boolean myIsRendererWithSelection;
  private Color myRendererBg;
  private Color myRendererFg;
  private int myPreferredWidth = -1;
  private int myCaretPosition = -1;
  private final List<EditorSettingsProvider> mySettingsProviders = new ArrayList<>();
  private Disposable myDisposable;
  private Disposable myManualDisposable;
  private boolean myInHierarchy;
  private @Nls String myAccessibleName;

  public EditorTextField() {
    this("");
  }

  public EditorTextField(@NotNull String text) {
    this(text, null, FileTypes.PLAIN_TEXT);
  }

  public EditorTextField(@NotNull String text, Project project, FileType fileType) {
    this(EditorFactory.getInstance().createDocument(StringUtil.convertLineSeparators(text)), project, fileType);
    LINE_SEPARATOR_KEY.set(myDocument, detectLineSeparators(myDocument, text));
  }

  public EditorTextField(Document document, Project project, FileType fileType) {
    this(document, project, fileType, false);
  }

  public EditorTextField(Project project, FileType fileType) {
    this((Document)null, project, fileType);
  }

  public EditorTextField(Document document, Project project, FileType fileType, boolean isViewer) {
    this(document, project, fileType, isViewer, true);
  }

  public EditorTextField(Document document, Project project, FileType fileType, boolean isViewer, boolean oneLineMode) {
    myOneLineMode = oneLineMode;
    myIsViewer = isViewer;
    setDocument(document);
    myProject = project;
    myFileType = fileType;
    setLayout(new BorderLayout());
    enableEvents(AWTEvent.KEY_EVENT_MASK);
    setFocusable(false);

    setFocusTraversalPolicyProvider(true);
    setFocusTraversalPolicy(new Jdk7DelegatingToRootTraversalPolicy());

    setFont(UIManager.getFont("TextField.font"));
    addHierarchyListener(e -> {
      if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && e.getChanged().isShowing()) {
        if (myEditor == null) initEditor();
      }
    });
    putClientProperty(DslComponentProperty.VISUAL_PADDINGS, UnscaledGapsKt.UnscaledGaps(3));
    putClientProperty(DslComponentProperty.VERTICAL_COMPONENT_GAP, VerticalComponentGap.BOTH);
  }

  private @Nullable Project getProjectIfValid() {
    return myProject == null || myProject.isDisposed() ? null : myProject;
  }

  //prevent from editor reinitialisation on add/remove
  public void setDisposedWith(@NotNull Disposable disposable) {
    assert myManualDisposable == null;
    Disposer.register(disposable, () -> {
      myManualDisposable = null;
      deInitEditor();
    });
    myManualDisposable = disposable;
  }

  public void setSupplementary(boolean supplementary) {
    myIsSupplementary = supplementary;
    Editor editor = getEditor();
    if (editor != null) {
      editor.putUserData(SUPPLEMENTARY_KEY, supplementary);
    }
  }

  public void setFontInheritedFromLAF(boolean b) {
    myInheritSwingFont = b;
    setDocument(myDocument); // reinit editor.
  }

  /**
   * @see EditorEx#setShowPlaceholderWhenFocused(boolean)
   */
  public void setShowPlaceholderWhenFocused(boolean b) {
    myShowPlaceholderWhenFocused = b;
    EditorEx editor = getEditor(false);
    if (editor != null) {
      editor.setShowPlaceholderWhenFocused(myShowPlaceholderWhenFocused);
    }
  }

  @Override
  public @NotNull String getText() {
    Document document = getDocument();
    String text = document.getText();
    LineSeparator separator = LINE_SEPARATOR_KEY.get(document);
    if (separator != null) {
      return StringUtil.convertLineSeparators(text, separator.getSeparatorString());
    }
    return text;
  }

  @Override
  public void updateUI() {
    try {
      myIgnoreSetBgColor = myEnforcedBgColor == null;
      super.updateUI();
    }
    finally {
      myIgnoreSetBgColor = false;
    }
  }

  @Override
  public void setBackground(Color bg) {
    if (myIgnoreSetBgColor) {
      bg = getBackground();
      super.setBackground(bg);
    } else {
      super.setBackground(bg);
      myEnforcedBgColor = bg;
    }
    EditorEx editor = getEditor(false);
    if (editor != null) {
      editor.setBackgroundColor(bg);
    }
  }

  @Override
  public JComponent getComponent() {
    return this;
  }

  @Override
  public void addDocumentListener(@NotNull DocumentListener listener) {
    myDocumentListeners.add(listener);
    installDocumentListener();
  }

  @Override
  public void removeDocumentListener(@NotNull DocumentListener listener) {
    myDocumentListeners.remove(listener);
    uninstallDocumentListener(false);
  }

  @Override
  public void beforeDocumentChange(@NotNull DocumentEvent event) {
    for (DocumentListener documentListener : myDocumentListeners) {
      documentListener.beforeDocumentChange(event);
    }
  }

  @Override
  public void documentChanged(@NotNull DocumentEvent event) {
    for (DocumentListener documentListener : myDocumentListeners) {
      documentListener.documentChanged(event);
    }
  }

  public Project getProject() {
    return myProject;
  }

  @Override
  public @NotNull Document getDocument() {
    if (myDocument == null) {
      myDocument = createDocument();
    }
    return myDocument;
  }

  public void setDocument(Document document) {
    if (myDocument != null) {
      uninstallDocumentListener(true);
    }

    myDocument = document;
    installDocumentListener();
    Editor editor = getEditor();
    if (editor != null) {
      boolean wasFocused = isFocusOwner();

      EditorEx newEditor = createEditor();
      scheduleEditorRelease();
      myEditor = newEditor;
      add(newEditor.getComponent(), BorderLayout.CENTER);

      if (myNextFocusable != null) {
        newEditor.getContentComponent().setNextFocusableComponent(myNextFocusable);
        myNextFocusable = null;
      }
      if (wasFocused) {
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> requestFocus());
      }

      releaseScheduledEditors();

      validate();
    }
  }

  private void installDocumentListener() {
    if (myDocument != null && !myDocumentListeners.isEmpty() && !myIsListenerInstalled) {
      myIsListenerInstalled = true;
      myDocument.addDocumentListener(this);
    }
  }

  private void uninstallDocumentListener(boolean force) {
    if (myDocument != null && myIsListenerInstalled && (force || myDocumentListeners.isEmpty())) {
      myIsListenerInstalled = false;
      myDocument.removeDocumentListener(this);
    }
  }

  @Override
  public void setText(final @Nullable String text) {
    WriteIntentReadAction.run((Runnable)() ->
      CommandProcessor.getInstance().executeCommand(getProject(), () ->
        ApplicationManager.getApplication().runWriteAction(() -> {
          LineSeparator separator = LINE_SEPARATOR_KEY.get(myDocument);
          if (separator == null) {
            separator = detectLineSeparators(myDocument, text);
          }
          LINE_SEPARATOR_KEY.set(myDocument, separator);
          myDocument.replaceString(0, myDocument.getTextLength(), normalize(text, separator));
          Editor editor = getEditor();
          if (editor != null) {
            final CaretModel caretModel = editor.getCaretModel();
            if (caretModel.getOffset() >= myDocument.getTextLength()) {
              caretModel.moveToOffset(myDocument.getTextLength());
            }
          }
        }), null, null, UndoConfirmationPolicy.DEFAULT, getDocument())
    );
  }

  private static @NotNull String normalize(@Nullable String text, @Nullable LineSeparator separator) {
    if (text == null || separator == null) return StringUtil.notNullize(text);
    return StringUtil.convertLineSeparators(text);
  }

  public static @Nullable LineSeparator detectLineSeparators(@Nullable Document document, @Nullable String text) {
    if (text == null) return null;
    boolean doNotNormalizeDetect = document instanceof DocumentImpl && ((DocumentImpl)document).acceptsSlashR();
    if (doNotNormalizeDetect) return null;
    return StringUtil.detectSeparators(text);
  }

  /**
   * Allows to define {@link EditorEx#setPlaceholder(CharSequence) editor's placeholder}. The trick here is that the editor
   * is instantiated lazily by the editor text field and provided placeholder text is applied to the editor during its
   * actual construction then.
   *
   * @param text    {@link EditorEx#setPlaceholder(CharSequence) editor's placeholder} text to use
   */
  public void setPlaceholder(@Nls @Nullable CharSequence text) {
    myHintText = text;
    EditorEx editor = getEditor(false);
    if (editor != null) {
      editor.setPlaceholder(text);
    }
  }

  private sealed interface DeferredSelection permits DeferredSelection.None, DeferredSelection.All, DeferredSelection.Custom {
    void execute(@NotNull Editor editor);

    final class All implements DeferredSelection {
      private static final @NotNull All INSTANCE = new DeferredSelection.All();

      private All() {
      }

      @Override
      public void execute(@NotNull Editor editor) {
        doSelect(editor, TextRange.create(0, editor.getDocument().getTextLength()));
      }

      public static @NotNull All getInstance() {
        return INSTANCE;
      }
    }

    final class None implements DeferredSelection {
      private static final @NotNull None INSTANCE = new DeferredSelection.None();

      private None() {
      }

      @Override
      public void execute(@NotNull Editor editor) {
        // do nothing
      }

      public static @NotNull None getInstance() {
        return INSTANCE;
      }
    }

    record Custom(@NotNull TextRange range) implements DeferredSelection {
      @Override
      public void execute(@NotNull Editor editor) {
        doSelect(editor, range);
      }
    }
  }

  /**
   * Select a given text range or defers the selection until the editor component gets created.
   */
  public void select(@NotNull TextRange range) {
    Editor editor = getEditor();
    if (editor != null) {
      doSelect(editor, range);
    } else {
      deferredSelection = new DeferredSelection.Custom(range);
    }
  }

  /**
   * Select all text or defers the selection until the editor component gets created.
   */
  public void selectAll() {
    Editor editor = getEditor();
    if (editor != null) {
      doSelect(editor, TextRange.create(0, editor.getDocument().getTextLength()));
    }
    else {
      deferredSelection = DeferredSelection.All.getInstance();
    }
  }

  private static void doSelect(@NotNull Editor editor, @NotNull TextRange range) {
    editor.getCaretModel().removeSecondaryCarets();
    editor.getCaretModel().getPrimaryCaret().setSelection(range.getStartOffset(), range.getEndOffset(), false);
  }

  public void removeSelection() {
    Editor editor = getEditor();
    if (editor != null) {
      editor.getSelectionModel().removeSelection();
    }
    else {
      deferredSelection = DeferredSelection.None.getInstance();
    }
  }

  /**
   * @see javax.swing.text.JTextComponent#setCaretPosition(int)
   */
  public void setCaretPosition(int position) {
    Document document = getDocument();
    if (position > document.getTextLength() || position < 0) {
      throw new IllegalArgumentException("bad position: " + position);
    }
    Editor editor = getEditor();
    if (editor != null) {
      editor.getCaretModel().moveToOffset(position);
    }
    else {
      myCaretPosition = position;
    }
  }
  public CaretModel getCaretModel() {
    Editor editor = getEditor(true);
    return editor == null ? null : editor.getCaretModel();
  }

  @Override
  public boolean isFocusOwner() {
    Editor editor = getEditor();
    if (editor != null) {
      return IJSwingUtilities.hasFocus(editor.getContentComponent());
    }
    return super.isFocusOwner();
  }

  protected void onEditorAdded(@NotNull Editor editor) {

  }

  private void initEditorDisposables() {
    Disposable uiDisposable = PlatformDataKeys.UI_DISPOSABLE.getData(DataManager.getInstance().getDataContext(this));
    if (uiDisposable != null) {
      // If this component is added to a dialog (for example, the settings dialog),
      // then we have to release the editor simultaneously on close.
      // Otherwise, a corresponding dynamic plugin cannot be unloaded.
      Disposer.register(uiDisposable, this::releaseEditorNow);
    }

    myDisposable = Disposer.newDisposable("ETF dispose");
    Disposer.register(myDisposable, this::releaseEditorLater);
    Project project = getProjectIfValid();
    if (project != null) {
      project.getMessageBus().connect(myDisposable).subscribe(ProjectCloseListener.TOPIC, new ProjectCloseListener() {
        @Override
        public void projectClosing(@NotNull Project p) {
          if (p == project) {
            releaseEditorNow();
          }
        }
      });
    }
    Disposer.register(myDisposable, () -> {
      WriteIntentReadAction.run((Runnable)() -> {
        // remove traces of this editor from UndoManager to avoid leaks
        Document document = myDocument;
        if (document != null) {
          if (project != null && !project.isDisposed()) {
            ((UndoManagerImpl)UndoManager.getInstance(project)).clearDocumentReferences(document);
          }
          ((UndoManagerImpl)UndoManager.getGlobalInstance()).clearDocumentReferences(document);
        }
      });
    });
  }

  private EditorEx initEditor() {
    if (myDisposable == null) {
      initEditorDisposables();
    }

    if (myEditor != null) {
      releaseEditorLater();
    }

    boolean isFocused = isFocusOwner();

    EditorEx editor = initEditorInner();
    onEditorAdded(editor);

    if (myNextFocusable != null) {
      editor.getContentComponent().setNextFocusableComponent(myNextFocusable);
      myNextFocusable = null;
    }
    revalidate();
    if (isFocused) {
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> requestFocus());
    }
    return editor;
  }

  private EditorEx initEditorInner() {
    EditorEx editor = createEditor();
    editor.getContentComponent().setEnabled(isEnabled());
    if (myCaretPosition >= 0) {
      //  at com.intellij.openapi.application.impl.ApplicationImpl.assertReadAccessAllowed(ApplicationImpl.java:1009)
      //	at com.intellij.openapi.editor.impl.CaretImpl.getOffset(CaretImpl.java:661)
      //	at com.intellij.openapi.editor.impl.CaretImpl.doMoveToLogicalPosition(CaretImpl.java:419)
      //	at com.intellij.openapi.editor.impl.CaretImpl.moveToLogicalPosition(CaretImpl.java:610)
      //	at com.intellij.openapi.editor.impl.CaretImpl.lambda$moveToOffset$0(CaretImpl.java:105)
      //	at com.intellij.openapi.editor.impl.CaretModelImpl.doWithCaretMerging(CaretModelImpl.java:413)
      //	at com.intellij.openapi.editor.impl.CaretImpl.moveToOffset(CaretImpl.java:103)
      //	at com.intellij.openapi.editor.CaretModel.moveToOffset(CaretModel.java:91)
      //	at com.intellij.openapi.editor.CaretModel.moveToOffset(CaretModel.java:77)
      //	at com.intellij.ui.EditorTextField.initEditorInner(EditorTextField.java:562)
      ReadAction.run(() -> {
        editor.getCaretModel().moveToOffset(myCaretPosition);
      });
      editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    }
    String tooltip = getToolTipText();
    if (StringUtil.isNotEmpty(tooltip)) {
      editor.getContentComponent().setToolTipText(tooltip);
    }
    myEditor = editor;
    add(editor.getComponent(), BorderLayout.CENTER);
    return editor;
  }

  @Override
  public void removeNotify() {
    myInHierarchy = false;
    super.removeNotify();
    InternalDecoratorImpl.componentWithEditorBackgroundRemoved(this);
    if (myManualDisposable == null) deInitEditor();
  }

  private void deInitEditor() {
    if (myDisposable != null) {
      Disposer.dispose(myDisposable);
      myDisposable = null;
    }
  }

  private void releaseEditor(@NotNull Editor editor) {
    // todo IMHO this should be removed completely
    Project project = getProjectIfValid();
    if (project != null && myIsViewer) {
      final PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      if (psiFile != null) {
        DaemonCodeAnalyzer.getInstance(project).setHighlightingEnabled(psiFile, true);
      }
    }

    remove(editor.getComponent());

    editor.getContentComponent().removeFocusListener(this);
    editor.getContentComponent().removeMouseListener(this);

    if (!editor.isDisposed()) {
      EditorFactory.getInstance().releaseEditor(editor);
    }
  }

  private void releaseEditorNow() {
    scheduleEditorRelease();
    releaseScheduledEditors();
  }

  private boolean scheduleEditorRelease() {
    EditorEx editor = myEditor;
    if (editor == null) return false;
    myEditorsToBeReleased.add(editor);
    myEditor = null;
    return true;
  }

  void releaseEditorLater() {
    // releasing an editor implies removing it from a component hierarchy invokeLater is required because releaseEditor() may be called from
    // removeNotify(), so we need to let swing complete its removeNotify() chain and only then execute another removal from the hierarchy.
    // Otherwise, swing goes nuts because of nested removals and indices get corrupted
    if (scheduleEditorRelease()) {
      ApplicationManager.getApplication().invokeLater(this::releaseScheduledEditors, ModalityState.stateForComponent(this));
    }
  }

  private void releaseScheduledEditors() {
    for (Editor editorToRelease : myEditorsToBeReleased) {
      releaseEditor(editorToRelease);
    }
    myEditorsToBeReleased.clear();
  }

  @Override
  public void setFont(Font font) {
    super.setFont(font);
    EditorEx editor = getEditor(false);
    if (editor != null) {
      setupEditorFont(editor);
    }
  }

  /**
   * This option will be used for embedded editor creation. It's ok to override this method if you don't want to configure
   * it using class constructor
   * @return is one line mode or not
   */
  protected boolean isOneLineMode() {
    return myOneLineMode;
  }

  private void initOneLineMode(final EditorEx editor) {
    final boolean isOneLineMode = isOneLineMode();

    // set mode in editor
    editor.setOneLineMode(isOneLineMode);

    EditorColorsManager colorsManager = EditorColorsManager.getInstance();
    EditorColorsScheme customGlobalScheme = colorsManager.getSchemeForCurrentUITheme();
    editor.setColorsScheme(editor.createBoundColorSchemeDelegate(isOneLineMode ? customGlobalScheme : null));

    editor.getSettings().setCaretRowShown(false);
    editor.getSettings().setDndEnabled(false);

    // color scheme settings:
    setupEditorFont(editor);
    updateBorder(editor);
    editor.setBackgroundColor(getBackground());
  }

  public void setOneLineMode(boolean oneLineMode) {
    myOneLineMode = oneLineMode;
  }

  protected Document createDocument() {
    Project project = getProjectIfValid();
    final PsiFileFactory factory = PsiFileFactory.getInstance(project);
    final long stamp = LocalTimeCounter.currentTime();
    if (myFileType == null) {
      LOG.error("Provide not-null FileType");
      myFileType = FileTypes.PLAIN_TEXT;
    }
    final PsiFile psiFile = factory.createFileFromText("Dummy." + myFileType.getDefaultExtension(), myFileType, "", stamp, true, false);
    return PsiDocumentManager.getInstance(project).getDocument(psiFile);
  }

  protected @NotNull EditorEx createEditor() {
    Project project = getProjectIfValid();
    Document document = getDocument();
    final EditorFactory factory = EditorFactory.getInstance();
    EditorEx editor = (EditorEx)(myIsViewer ? factory.createViewer(document, project) : factory.createEditor(document, project));
    editor.putUserData(MANAGED_BY_FIELD, Boolean.TRUE);

    setupTextFieldEditor(editor);
    editor.setCaretEnabled(!myIsViewer);

    if (project != null) {
      PsiFile psiFile = ReadAction.compute(() -> PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument()));
      if (psiFile != null) {
        DaemonCodeAnalyzer.getInstance(project).setHighlightingEnabled(psiFile, !myIsViewer);
      }
    }

    if (project != null) {
      EditorHighlighterFactory highlighterFactory = EditorHighlighterFactory.getInstance();
      VirtualFile virtualFile = myDocument == null ? null : FileDocumentManager.getInstance().getFile(myDocument);
      EditorHighlighter highlighter = ReadAction.compute(() -> {
        return virtualFile != null ? highlighterFactory.createEditorHighlighter(project, virtualFile) :
               myFileType != null ? highlighterFactory.createEditorHighlighter(project, myFileType) : null;
      });
      if (highlighter != null) editor.setHighlighter(highlighter);
    }

    WriteIntentReadAction.run((Runnable)() -> {
      editor.getSettings().setCaretRowShown(false);

      editor.setOneLineMode(myOneLineMode);
      editor.getCaretModel().moveToOffset(document.getTextLength());

      if (!shouldHaveBorder()) {
        editor.setBorder(null);
      }

      if (myIsViewer) {
        editor.getSelectionModel().removeSelection();
      }
      else {
        deferredSelection.execute(editor);
        deferredSelection = DeferredSelection.None.getInstance();
      }

      editor.putUserData(SUPPLEMENTARY_KEY, myIsSupplementary);
      editor.getContentComponent().setFocusCycleRoot(false);
      editor.getContentComponent().addFocusListener(this);
      editor.getContentComponent().addMouseListener(this);

      editor.setPlaceholder(myHintText);
      editor.setShowPlaceholderWhenFocused(myShowPlaceholderWhenFocused);

      String accessibleName = myAccessibleName;
      // Fallback to using the label as accessible name, similar to AccessibleJComponent.getAccessibleName.
      if (accessibleName == null) {
          Object label = getClientProperty("labeledBy");
          if (label instanceof Accessible a) {
            AccessibleContext ac = a.getAccessibleContext();
            if (ac != null) {
              accessibleName = ac.getAccessibleName();
            }
          }
      }
      if (accessibleName != null) {
        editor.getContentComponent().getAccessibleContext().setAccessibleName(accessibleName);
      }

      initOneLineMode(editor);

      if (myIsRendererWithSelection) {
        ((EditorImpl)editor).setPaintSelection(true);
        editor.getColorsScheme().setColor(EditorColors.SELECTION_BACKGROUND_COLOR, myRendererBg);
        editor.getColorsScheme().setColor(EditorColors.SELECTION_FOREGROUND_COLOR, myRendererFg);
        editor.getSelectionModel().setSelection(0, document.getTextLength());
        editor.setBackgroundColor(myRendererBg);
      }

      for (EditorSettingsProvider provider : mySettingsProviders) {
        provider.customizeSettings(editor);
      }
    });

    return editor;
  }

  public static boolean managesEditor(@NotNull Editor editor) {
    return editor.getUserData(MANAGED_BY_FIELD) == Boolean.TRUE;
  }

  public static void setupTextFieldEditor(@NotNull EditorEx editor) {
    EditorSettings settings = editor.getSettings();
    settings.setAdditionalLinesCount(0);
    settings.setAdditionalColumnsCount(1);
    settings.setRightMarginShown(false);
    settings.setRightMargin(-1);
    settings.setFoldingOutlineShown(false);
    settings.setLineNumbersShown(false);
    settings.setLineMarkerAreaShown(false);
    settings.setIndentGuidesShown(false);
    settings.setVirtualSpace(false);
    settings.setWheelFontChangeEnabled(false);
    settings.setAdditionalPageAtBottom(false);
    editor.setHorizontalScrollbarVisible(false);
    editor.setVerticalScrollbarVisible(false);
    settings.setLineCursorWidth(1);
  }

  protected void updateBorder(final @NotNull EditorEx editor) {
    if (editor.isOneLineMode()
        && !Boolean.TRUE.equals(getClientProperty(ComboBox.IS_TABLE_CELL_EDITOR_PROPERTY))
        && (SwingUtilities.getAncestorOfClass(JTable.class, this) == null || Boolean.TRUE.equals(getClientProperty("JBListTable.isTableCellEditor")))) {
      final Container parent = getParent();
      if (parent instanceof JTable || parent instanceof CellRendererPane) return;

      setupBorder(editor);
    }
  }

  protected void setupBorder(@NotNull EditorEx editor) {
    editor.setBorder(new DarculaEditorTextFieldBorder(this, editor));
  }

  private void setupEditorFont(final EditorEx editor) {
    if (myInheritSwingFont) {
      ((EditorImpl)editor).setUseEditorAntialiasing(false);
      editor.getColorsScheme().setEditorFontName(getFont().getFontName());
      editor.getColorsScheme().setEditorFontSize(getFont().getSize());
      return;
    }
    float currentEditorFontSize = UISettingsUtils.getInstance().getScaledEditorFontSize();
    if (editor.getColorsScheme().getEditorFontSize2D() != currentEditorFontSize) {
      editor.putUserData(ZoomIndicatorManager.SUPPRESS_ZOOM_INDICATOR_ONCE, true);
      editor.setFontSize(currentEditorFontSize);
    }
  }

  protected boolean shouldHaveBorder() {
    return true;
  }

  @Override
  public void setEnabled(boolean enabled) {
    if (isEnabled() != enabled) {
      super.setEnabled(enabled);
      setFocusTraversalPolicyProvider(enabled);
      setViewerEnabled(enabled);
      resetEditor();
    }
  }

  protected void setViewerEnabled(boolean enabled) {
    myIsViewer = !enabled;
  }

  private void resetEditor() {
    final EditorEx editor = myEditor;

    if (editor != null) {
      scheduleEditorRelease();
      initEditorInner();
      releaseScheduledEditors();
      revalidate();
    }
  }

  public boolean isViewer() {
    return myIsViewer;
  }

  public void setViewer(boolean viewer) {
    if (myIsViewer != viewer) {
      myIsViewer = viewer;

      resetEditor();
    }
  }

  @Override
  public Color getBackground() {
    Color color = myEnforcedBgColor != null ? myEnforcedBgColor : UIUtil.getTextFieldBackground();
    return color != null ? color : super.getBackground();
  }

  @Override
  protected void addImpl(Component comp, Object constraints, int index) {
    if (myEditor == null || comp != myEditor.getComponent()) {
      assert false : "You are not allowed to add anything to EditorTextField";
    }

    super.addImpl(comp, constraints, index);
  }

  @Override
  public void validate() {
    getEditor(true);
    super.validate();
  }

  @Override
  public Dimension getPreferredSize() {
    if (isPreferredSizeSet()) {
      return super.getPreferredSize();
    }

    Editor editor = getEditor(true);
    boolean toReleaseEditor = false;
    if (editor == null && myEnsureWillComputePreferredSize) {
      myEnsureWillComputePreferredSize = false;
      editor = initEditorInner();
      toReleaseEditor = true;
    }

    Dimension size = JBUI.size(100, 10);
    if (editor != null) {
      Dimension preferredSize = editor.getComponent().getPreferredSize();

      JBInsets.addTo(preferredSize, getInsets());
      size = preferredSize;
    }
    else if (myPassivePreferredSize != null) {
      size = myPassivePreferredSize;
    }

    if (toReleaseEditor) {
      releaseEditorNow();
      myPassivePreferredSize = size;
    }

    if (myPreferredWidth != -1) {
      size.width = myPreferredWidth;
    }
    return size;
  }

  @Override
  public Dimension getMinimumSize() {
    if (isMinimumSizeSet()) {
      return super.getMinimumSize();
    }

    Dimension size = JBUI.size(1, 10);
    Editor editor = getEditor();
    if (editor != null) {
      size.height = editor.getLineHeight();

      if (StartupUiUtil.isUnderDarcula() || UIUtil.isUnderIntelliJLaF()) {
        size.height = Math.max(size.height, JBUIScale.scale(16));
      }

      JBInsets.addTo(size, getInsets());
      JBInsets.addTo(size, editor.getInsets());
    }

    return size;
  }

  public void setPreferredWidth(int preferredWidth) {
    myPreferredWidth = preferredWidth;
  }

  @Override
  public Component getNextFocusableComponent() {
    Editor editor = getEditor();
    if (editor == null && myNextFocusable == null) return super.getNextFocusableComponent();
    if (editor == null) return myNextFocusable;
    return editor.getContentComponent().getNextFocusableComponent();
  }

  @Override
  public void setNextFocusableComponent(Component aComponent) {
    Editor editor = getEditor();
    if (editor != null) {
      editor.getContentComponent().setNextFocusableComponent(aComponent);
      return;
    }
    myNextFocusable = aComponent;
  }


  @Override
  protected boolean processKeyBinding(KeyStroke ks, KeyEvent e, int condition, boolean pressed) {
    EditorEx editor = getEditor(false);
    if (e.isConsumed() || editor != null && !editor.processKeyTyped(e)) {
      return super.processKeyBinding(ks, e, condition, pressed);
    }
    return true;
  }

  //use addSettingsProvider or onEditorAdded
  @Override
  public final void addNotify() {
    myInHierarchy = true;
    if (myManualDisposable == null && myEditor == null && !Registry.is("editor.text.field.init.on.shown")) {
      initEditor();
    }
    super.addNotify();
    InternalDecoratorImpl.componentWithEditorBackgroundAdded(this);
  }

  public @Nullable EditorEx getEditor(boolean initializeIfSafe) {
    EditorEx editor = myEditor;
    if (editor == null && initializeIfSafe && (myInHierarchy || myManualDisposable != null)) {
      return initEditor();
    }
    return editor;
  }

  @Override
  public void requestFocus() {
    Editor editor = getEditor(true);
    if (editor != null) {
      IdeFocusManager.getGlobalInstance().requestFocus(editor.getContentComponent(), true);
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    }
    else {
      super.requestFocus();
    }
  }

  @Override
  public boolean requestFocusInWindow() {
    Editor editor = getEditor();
    if (editor != null) {
      final boolean b = editor.getContentComponent().requestFocusInWindow();
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      return b;
    }
    else {
      return super.requestFocusInWindow();
    }
  }

  /**
   *
   * @return null if the editor is not initialized (e.g. if the field is not added to a container)
   * @see #createEditor()
   * @see #addNotify()
   */
  public @Nullable Editor getEditor() {
    return getEditor(false);
  }

  public FileType getFileType() {
    return myFileType;
  }

  public @NotNull JComponent getFocusTarget() {
    Editor editor = getEditor();
    return editor == null ? this : editor.getContentComponent();
  }

  @Override
  public synchronized void addFocusListener(FocusListener l) {
    myFocusListeners.add(l);
  }

  @Override
  public synchronized void removeFocusListener(FocusListener l) {
    myFocusListeners.remove(l);
  }

  @Override
  public void focusGained(FocusEvent e) {
    for (FocusListener listener : myFocusListeners) {
      listener.focusGained(e);
    }
  }

  @Override
  public void focusLost(FocusEvent e) {
    for (FocusListener listener : myFocusListeners) {
      listener.focusLost(e);
    }
  }

  @SuppressWarnings("NonSynchronizedMethodOverridesSynchronizedMethod")
  @Override
  public void addMouseListener(MouseListener l) {
    myMouseListeners.add(l);
  }

  @SuppressWarnings("NonSynchronizedMethodOverridesSynchronizedMethod")
  @Override
  public void removeMouseListener(MouseListener l) {
    myMouseListeners.remove(l);
  }

  @Override
  public void mouseClicked(MouseEvent e) {
    for (MouseListener listener : myMouseListeners) {
      listener.mouseClicked(e);
    }
  }

  @Override
  public void mousePressed(MouseEvent e) {
    for (MouseListener listener : myMouseListeners) {
      listener.mousePressed(e);
    }
  }

  @Override
  public void mouseReleased(MouseEvent e) {
    for (MouseListener listener : myMouseListeners) {
      listener.mouseReleased(e);
    }
  }

  @Override
  public void mouseEntered(MouseEvent e) {
    for (MouseListener listener : myMouseListeners) {
      listener.mouseEntered(e);
    }
  }

  @Override
  public void mouseExited(MouseEvent e) {
    for (MouseListener listener : myMouseListeners) {
      listener.mouseExited(e);
    }
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    EditorEx editor = getEditor(false);
    if (editor != null && editor.isRendererMode()) {
      sink.set(PlatformDataKeys.COPY_PROVIDER, editor.getCopyProvider());
    }
    if (editor == null) {
      sink.setNull(CommonDataKeys.EDITOR);
    }
    else {
      sink.set(CommonDataKeys.EDITOR, editor);
    }
  }

  public void setFileType(@NotNull FileType fileType) {
    setNewDocumentAndFileType(fileType, getDocument());
  }

  public void setNewDocumentAndFileType(@NotNull FileType fileType, Document document) {
    myFileType = fileType;
    setDocument(document);
  }

  public void ensureWillComputePreferredSize() {
    myEnsureWillComputePreferredSize = true;
  }

  public void setAsRendererWithSelection(Color backgroundColor, Color foregroundColor) {
    myIsRendererWithSelection = true;
    myRendererBg = backgroundColor;
    myRendererFg = foregroundColor;
  }

  public void addSettingsProvider(@NotNull EditorSettingsProvider provider) {
    mySettingsProviders.add(provider);
  }

  private static final class Jdk7DelegatingToRootTraversalPolicy extends AbstractDelegatingToRootTraversalPolicy {
    private boolean invokedFromBeforeOrAfter;
    @Override
    public Component getFirstComponent(Container aContainer) {
      return getDefaultComponent(aContainer);
    }

    @Override
    public Component getLastComponent(Container aContainer) {
      return getDefaultComponent(aContainer);
    }

    @Override
    public Component getComponentAfter(Container aContainer, Component aComponent) {
      if (aContainer instanceof EditorTextField) {
        int count = aContainer.getComponentCount();
        if (count > 1 && aComponent != aContainer.getComponent(count - 1)) {
          return getDefaultComponent(aContainer);
        }
      }

      invokedFromBeforeOrAfter = true;
      Component after;
      try {
        after = super.getComponentAfter(aContainer, aComponent);
      } finally {
        invokedFromBeforeOrAfter = false;
      }
      return after != aComponent? after: null;  // escape our container
    }

    @Override
    public Component getComponentBefore(Container aContainer, Component aComponent) {
      Component before = super.getComponentBefore(aContainer, aComponent);
      return before != aComponent ? before: null;  // escape our container
    }

    @Override
    public Component getDefaultComponent(Container aContainer) {
      if (invokedFromBeforeOrAfter) return null;     // escape our container
      if (!(aContainer.isVisible() && aContainer.isDisplayable())) {
        return null; // shamelessly copied from ContainerOrderFocusTraversalPolicy to fix the case of focus trying to get inside an invisible EditorTextField
      }
      Editor editor = aContainer instanceof EditorTextField ? ((EditorTextField)aContainer).getEditor() : null;
      if (editor != null) return editor.getContentComponent();
      return aContainer;
    }
  }

  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null) {
      accessibleContext = new AccessibleJPanel() {
        @Override
        public String getAccessibleName() {
          // Accessible name is not needed on the wrapper, only on the editor component itself. Otherwise, it may be read twice.
          return null;
        }

        @Override
        public void setAccessibleName(@Nls String s) {
          myAccessibleName = s;
          EditorEx editor = getEditor(false);
          if (editor != null) {
            editor.getContentComponent().getAccessibleContext().setAccessibleName(s);
          }
        }
      };
    }
    return accessibleContext;
  }
}
