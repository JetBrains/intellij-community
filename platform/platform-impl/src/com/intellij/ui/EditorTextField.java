// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.ide.ui.laf.darcula.ui.DarculaEditorTextFieldBorder;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorColorsUtil;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ex.AbstractDelegatingToRootTraversalPolicy;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author max
 */
public class EditorTextField extends NonOpaquePanel implements EditorTextComponent, DocumentListener, DataProvider, TextAccessor,
                                                               FocusListener, MouseListener {
  public static final Key<Boolean> SUPPLEMENTARY_KEY = Key.create("Supplementary");

  private Document myDocument;
  private final Project myProject;
  private FileType myFileType;
  private EditorEx myEditor;
  private Component myNextFocusable;
  private boolean myWholeTextSelected;
  private final List<DocumentListener> myDocumentListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final List<FocusListener> myFocusListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final List<MouseListener> myMouseListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private boolean myIsListenerInstalled;
  private boolean myIsViewer;
  private boolean myIsSupplementary;
  private boolean myInheritSwingFont = true;
  private Color myEnforcedBgColor;
  private boolean myOneLineMode; // use getter to access this field! It is allowed to override getter and change initial behaviour
  private boolean myEnsureWillComputePreferredSize;
  private Dimension myPassivePreferredSize;
  private CharSequence myHintText;
  private boolean myIsRendererWithSelection;
  private Color myRendererBg;
  private Color myRendererFg;
  private int myPreferredWidth = -1;
  private int myCaretPosition = -1;
  private final List<EditorSettingsProvider> mySettingsProviders = new ArrayList<>();
  private Disposable myDisposable;

  public EditorTextField() {
    this("");
  }

  public EditorTextField(@NotNull String text) {
    this(EditorFactory.getInstance().createDocument(text), null, FileTypes.PLAIN_TEXT);
  }

  public EditorTextField(@NotNull String text, Project project, FileType fileType) {
    this(EditorFactory.getInstance().createDocument(text), project, fileType, false, true);
  }

  public EditorTextField(Document document, Project project, FileType fileType) {
    this(document, project, fileType, false, true);
  }

  public EditorTextField(Project project, FileType fileType) {
    this(null, project, fileType, false, true);
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
    // todo[dsl,max]
    setFocusable(true);
    // dsl: this is a weird way of doing things....
    super.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        requestFocus();
      }
    });

    setFocusTraversalPolicyProvider(true);
    setFocusTraversalPolicy(new Jdk7DelegatingToRootTraversalPolicy());

    setFont(UIManager.getFont("TextField.font"));
  }

  public void setSupplementary(boolean supplementary) {
    myIsSupplementary = supplementary;
    if (myEditor != null) {
      myEditor.putUserData(SUPPLEMENTARY_KEY, supplementary);
    }
  }

  public void setFontInheritedFromLAF(boolean b) {
    myInheritSwingFont = b;
    setDocument(myDocument); // reinit editor.
  }

  @NotNull
  @Override
  public String getText() {
    return myDocument.getText();
  }

  @Override
  public void setBackground(Color bg) {
    super.setBackground(bg);
    myEnforcedBgColor = bg;
    if (myEditor != null) {
      myEditor.setBackgroundColor(bg);
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
  @NotNull
  public Document getDocument() {
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
    if (myEditor != null) {
      //MainWatchPanel watches the oldEditor's focus in order to remove debugger combobox when focus is lost
      //we should first transfer focus to new oldEditor and only then remove current oldEditor
      //MainWatchPanel check that oldEditor.getParent == newEditor.getParent and does not remove oldEditor in such cases

      boolean isFocused = isFocusOwner();
      EditorEx newEditor = createEditor();
      releaseEditor(myEditor);
      myEditor = newEditor;
      add(myEditor.getComponent(), BorderLayout.CENTER);

      validate();
      if (isFocused) {
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(newEditor.getContentComponent(), true));
      }
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
  public void setText(@Nullable final String text) {
    CommandProcessor.getInstance().executeCommand(getProject(), () ->
      ApplicationManager.getApplication().runWriteAction(() -> {
      myDocument.replaceString(0, myDocument.getTextLength(), StringUtil.notNullize(text));
      if (myEditor != null) {
        final CaretModel caretModel = myEditor.getCaretModel();
        if (caretModel.getOffset() >= myDocument.getTextLength()) {
          caretModel.moveToOffset(myDocument.getTextLength());
        }
      }
    }), null, null, UndoConfirmationPolicy.DEFAULT, getDocument());
  }

  /**
   * Allows to define {@link EditorEx#setPlaceholder(CharSequence) editor's placeholder}. The trick here is that the editor
   * is instantiated lazily by the editor text field and provided placeholder text is applied to the editor during its
   * actual construction then.
   *
   * @param text    {@link EditorEx#setPlaceholder(CharSequence) editor's placeholder} text to use
   */
  public void setPlaceholder(@Nullable CharSequence text) {
    myHintText = text;
    if (myEditor != null) {
      myEditor.setPlaceholder(text);
    }
  }

  public void selectAll() {
    if (myEditor != null) {
      doSelectAll(myEditor);
    }
    else {
      myWholeTextSelected = true;
    }
  }

  private static void doSelectAll(@NotNull Editor editor) {
    editor.getCaretModel().removeSecondaryCarets();
    editor.getCaretModel().getPrimaryCaret().setSelection(0, editor.getDocument().getTextLength(), false);
  }

  public void removeSelection() {
    if (myEditor != null) {
      myEditor.getSelectionModel().removeSelection();
    }
    else {
      myWholeTextSelected = false;
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
    if (myEditor != null) {
      myEditor.getCaretModel().moveToOffset(position);
    }
    else {
      myCaretPosition = position;
    }
  }
  public CaretModel getCaretModel() {
    return myEditor.getCaretModel();
  }

  @Override
  public boolean isFocusOwner() {
    if (myEditor != null) {
      return IJSwingUtilities.hasFocus(myEditor.getContentComponent());
    }
    return super.isFocusOwner();
  }

  @Override
  public void addNotify() {
    myDisposable = Disposer.newDisposable("ETF dispose");
    Disposer.register(myDisposable, this::releaseEditorLater);
    if (myProject != null) {
      ProjectManagerListener listener = new ProjectManagerListener() {
        @Override
        public void projectClosing(@NotNull Project project) {
          releaseEditor(myEditor);
          myEditor = null;
        }
      };
      ProjectManager.getInstance().addProjectManagerListener(myProject, listener);
      Disposer.register(myDisposable, ()->ProjectManager.getInstance().removeProjectManagerListener(myProject, listener));
    }

    if (myEditor != null) {
      releaseEditorLater();
    }

    boolean isFocused = isFocusOwner();

    initEditor();

    super.addNotify();

    if (myNextFocusable != null) {
      myEditor.getContentComponent().setNextFocusableComponent(myNextFocusable);
      myNextFocusable = null;
    }
    revalidate();
    if (isFocused) {
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> requestFocus());
    }
  }

  private void initEditor() {
    myEditor = createEditor();
    myEditor.getContentComponent().setEnabled(isEnabled());
    if (myCaretPosition >= 0) {
      myEditor.getCaretModel().moveToOffset(myCaretPosition);
      myEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    }
    String tooltip = getToolTipText();
    if (StringUtil.isNotEmpty(tooltip)) {
      myEditor.getContentComponent().setToolTipText(tooltip);
    }
    add(myEditor.getComponent(), BorderLayout.CENTER);
  }

  @Override
  public void removeNotify() {
    super.removeNotify();
    if (myDisposable != null) {
      Disposer.dispose(myDisposable);
    }
  }

  private void releaseEditor(Editor editor) {
    if (editor == null) return;

    // todo IMHO this should be removed completely
    if (myProject != null && !myProject.isDisposed() && myIsViewer) {
      final PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(editor.getDocument());
      if (psiFile != null) {
        DaemonCodeAnalyzer.getInstance(myProject).setHighlightingEnabled(psiFile, true);
      }
    }

    remove(editor.getComponent());

    editor.getContentComponent().removeFocusListener(this);
    editor.getContentComponent().removeMouseListener(this);

    if (!editor.isDisposed()) {
      EditorFactory.getInstance().releaseEditor(editor);
    }
  }

  void releaseEditorLater() {
    // releasing an editor implies removing it from a component hierarchy
    // invokeLater is required because releaseEditor() may be called from
    // removeNotify(), so we need to let swing complete its removeNotify() chain
    // and only then execute another removal from the hierarchy. Otherwise
    // swing goes nuts because of nested removals and indices get corrupted
    EditorEx editor = myEditor;
    ApplicationManager.getApplication().invokeLater(() -> releaseEditor(editor), ModalityState.stateForComponent(this));
    myEditor = null;
  }

  @Override
  public void setFont(Font font) {
    super.setFont(font);
    if (myEditor != null) {
      setupEditorFont(myEditor);
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

    EditorColorsScheme colorsScheme = editor.getColorsScheme();
    editor.getSettings().setCaretRowShown(false);

    // color scheme settings:
    setupEditorFont(editor);
    updateBorder(editor);
    editor.setBackgroundColor(getBackgroundColor(isEnabled(), colorsScheme));
  }

  public void setOneLineMode(boolean oneLineMode) {
    myOneLineMode = oneLineMode;
  }

  protected Document createDocument() {
    final PsiFileFactory factory = PsiFileFactory.getInstance(myProject);
    final long stamp = LocalTimeCounter.currentTime();
    final PsiFile psiFile = factory.createFileFromText("Dummy." + myFileType.getDefaultExtension(), myFileType, "", stamp, true, false);
    return PsiDocumentManager.getInstance(myProject).getDocument(psiFile);
  }

  protected EditorEx createEditor() {
    Document document = getDocument();
    final EditorFactory factory = EditorFactory.getInstance();
    EditorEx editor = (EditorEx)(myIsViewer ? factory.createViewer(document, myProject) : factory.createEditor(document, myProject));

    final EditorSettings settings = editor.getSettings();
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
    editor.setCaretEnabled(!myIsViewer);
    settings.setLineCursorWidth(1);

    if (myProject != null) {
      PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(editor.getDocument());
      if (psiFile != null) {
        DaemonCodeAnalyzer.getInstance(myProject).setHighlightingEnabled(psiFile, !myIsViewer);
      }
    }

    if (myProject != null) {
      EditorHighlighterFactory highlighterFactory = EditorHighlighterFactory.getInstance();
      VirtualFile virtualFile = myDocument == null ? null : FileDocumentManager.getInstance().getFile(myDocument);
      EditorHighlighter highlighter = virtualFile != null ? highlighterFactory.createEditorHighlighter(myProject, virtualFile) :
                                      myFileType != null ? highlighterFactory.createEditorHighlighter(myProject, myFileType) : null;
      if (highlighter != null) editor.setHighlighter(highlighter);
    }

    editor.getSettings().setCaretRowShown(false);

    editor.setOneLineMode(myOneLineMode);
    editor.getCaretModel().moveToOffset(document.getTextLength());

    if (!shouldHaveBorder()) {
      editor.setBorder(null);
    }

    if (myIsViewer) {
      editor.getSelectionModel().removeSelection();
    }
    else if (myWholeTextSelected) {
      doSelectAll(editor);
      myWholeTextSelected = false;
    }

    editor.putUserData(SUPPLEMENTARY_KEY, myIsSupplementary);
    editor.getContentComponent().setFocusCycleRoot(false);
    editor.getContentComponent().addFocusListener(this);
    editor.getContentComponent().addMouseListener(this);

    editor.setPlaceholder(myHintText);

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

    return editor;
  }

  protected void updateBorder(@NotNull final EditorEx editor) {
    if (editor.isOneLineMode()
        && !Boolean.TRUE.equals(getClientProperty("JComboBox.isTableCellEditor"))
        && (SwingUtilities.getAncestorOfClass(JTable.class, this) == null || Boolean.TRUE.equals(getClientProperty("JBListTable.isTableCellEditor")))) {
      final Container parent = getParent();
      if (parent instanceof JTable || parent instanceof CellRendererPane) return;

      setupBorder(editor);
    }
  }

  protected void setupBorder(@NotNull EditorEx editor) {
    if (UIUtil.isUnderAquaLookAndFeel() || UIUtil.isUnderDarcula() || UIUtil.isUnderIntelliJLaF()) {
      if (UIUtil.isUnderDefaultMacTheme()) {
        editor.setBorder(new DarculaUIUtil.MacEditorTextFieldBorder(this, editor));
      } else if (UIUtil.isUnderWin10LookAndFeel()) {
        editor.setBorder(new DarculaUIUtil.WinEditorTextFieldBorder(this, editor));
      } else {
        editor.setBorder(new DarculaEditorTextFieldBorder(this, editor));
      }

    }
    else {
      editor.setBorder(BorderFactory.createCompoundBorder(UIUtil.getTextFieldBorder(), BorderFactory.createEmptyBorder(2, 2, 2, 2)));
    }
  }

  private void setupEditorFont(final EditorEx editor) {
    if (myInheritSwingFont) {
      ((EditorImpl)editor).setUseEditorAntialiasing(false);
      editor.getColorsScheme().setEditorFontName(getFont().getFontName());
      editor.getColorsScheme().setEditorFontSize(getFont().getSize());
      return;
    }
    UISettings settings = UISettings.getInstance();
    if (settings.getPresentationMode()) editor.setFontSize(settings.getPresentationModeFontSize());
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
      EditorEx editor = myEditor;
      if (editor != null) {
        releaseEditor(editor);
        initEditor();
        revalidate();
      }
    }
  }

  protected void setViewerEnabled(boolean enabled) {
    myIsViewer = !enabled;
  }

  public boolean isViewer() {
    return myIsViewer;
  }

  @Override
  public Color getBackground() {
    Color color = getBackgroundColor(isEnabled(), EditorColorsUtil.getGlobalOrDefaultColorScheme());
    return color != null ? color : super.getBackground();
  }

  private Color getBackgroundColor(boolean enabled, final EditorColorsScheme colorsScheme){
    if (myEnforcedBgColor != null) return myEnforcedBgColor;
    if (ComponentUtil.getParentOfType((Class<? extends CellRendererPane>)CellRendererPane.class, (Component)this) != null && (UIUtil.isUnderDarcula() || UIUtil.isUnderIntelliJLaF())) {
      return getParent().getBackground();
    }

    if (UIUtil.isUnderDarcula()/* || UIUtil.isUnderIntelliJLaF()*/) return UIUtil.getTextFieldBackground();

    return enabled
           ? colorsScheme.getDefaultBackground()
           : UIUtil.getInactiveTextFieldBackgroundColor();
  }

  @Override
  protected void addImpl(Component comp, Object constraints, int index) {
    if (myEditor == null || comp != myEditor.getComponent()) {
      assert false : "You are not allowed to add anything to EditorTextField";
    }

    super.addImpl(comp, constraints, index);
  }

  @Override
  public Dimension getPreferredSize() {
    if (isPreferredSizeSet()) {
      return super.getPreferredSize();
    }

    boolean toReleaseEditor = false;
    if (myEditor == null && myEnsureWillComputePreferredSize) {
      myEnsureWillComputePreferredSize = false;
      initEditor();
      toReleaseEditor = true;
    }

    Dimension size = JBUI.size(100, 10);
    if (myEditor != null) {
      Dimension preferredSize = myEditor.getComponent().getPreferredSize();

      if (myPreferredWidth != -1) {
        preferredSize.width = myPreferredWidth;
      }

      JBInsets.addTo(preferredSize, getInsets());
      size = preferredSize;
    }
    else if (myPassivePreferredSize != null) {
      size = myPassivePreferredSize;
    }

    if (toReleaseEditor) {
      releaseEditor(myEditor);
      myEditor = null;
      myPassivePreferredSize = size;
    }

    return size;
  }

  @Override
  public Dimension getMinimumSize() {
    if (isMinimumSizeSet()) {
      return super.getMinimumSize();
    }

    Dimension size = JBUI.size(1, 10);
    if (myEditor != null) {
      size.height = myEditor.getLineHeight();

      if (UIUtil.isUnderDefaultMacTheme() || UIUtil.isUnderDarcula() || UIUtil.isUnderIntelliJLaF()) {
        size.height = Math.max(size.height, JBUIScale.scale(16));
      }

      JBInsets.addTo(size, getInsets());
      JBInsets.addTo(size, myEditor.getInsets());
    }

    return size;
  }

  public void setPreferredWidth(int preferredWidth) {
    myPreferredWidth = preferredWidth;
  }

  @Override
  public Component getNextFocusableComponent() {
    if (myEditor == null && myNextFocusable == null) return super.getNextFocusableComponent();
    if (myEditor == null) return myNextFocusable;
    return myEditor.getContentComponent().getNextFocusableComponent();
  }

  @Override
  public void setNextFocusableComponent(Component aComponent) {
    if (myEditor != null) {
      myEditor.getContentComponent().setNextFocusableComponent(aComponent);
      return;
    }
    myNextFocusable = aComponent;
  }


  @Override
  protected boolean processKeyBinding(KeyStroke ks, KeyEvent e, int condition, boolean pressed) {
    if (e.isConsumed() || myEditor != null && !myEditor.processKeyTyped(e)) {
      return super.processKeyBinding(ks, e, condition, pressed);
    }
    return true;
  }

  @Override
  public void requestFocus() {
    if (myEditor != null) {
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myEditor.getContentComponent(), true));
      myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    }
  }

  @Override
  public boolean requestFocusInWindow() {
    if (myEditor != null) {
      final boolean b = myEditor.getContentComponent().requestFocusInWindow();
      myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
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
  @Nullable
  public Editor getEditor() {
    return myEditor;
  }

  public FileType getFileType() {
    return myFileType;
  }

  @NotNull
  public JComponent getFocusTarget() {
    return myEditor == null ? this : myEditor.getContentComponent();
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
  public Object getData(@NotNull String dataId) {
    if (myEditor != null && myEditor.isRendererMode()) {
      if (PlatformDataKeys.COPY_PROVIDER.is(dataId)) {
        return myEditor.getCopyProvider();
      }
      return null;
    }

    if (CommonDataKeys.EDITOR.is(dataId)) {
      return myEditor;
    }

    return null;
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

  public boolean removeSettingsProvider(@NotNull EditorSettingsProvider provider) {
    return mySettingsProviders.remove(provider);
  }

  private static class Jdk7DelegatingToRootTraversalPolicy extends AbstractDelegatingToRootTraversalPolicy {
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
      Editor editor = aContainer instanceof EditorTextField ? ((EditorTextField)aContainer).getEditor() : null;
      if (editor != null) return editor.getContentComponent();
      return aContainer;
    }
  }
}
