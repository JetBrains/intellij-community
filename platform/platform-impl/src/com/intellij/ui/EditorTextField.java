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
package com.intellij.ui;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.ide.ui.laf.darcula.ui.DarculaEditorTextFieldBorder;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.*;
import com.intellij.openapi.editor.colors.impl.DelegateColorScheme;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FocusChangeListener;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.ex.AbstractDelegatingToRootTraversalPolicy;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.MacUIUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * @author max
 */
public class EditorTextField extends NonOpaquePanel implements DocumentListener, TextComponent, DataProvider,
                                                       DocumentBasedComponent, FocusListener {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.EditorTextField");
  public static final Key<Boolean> SUPPLEMENTARY_KEY = Key.create("Supplementary");

  private Document myDocument;
  private final Project myProject;
  private FileType myFileType;
  private EditorEx myEditor = null;
  private Component myNextFocusable = null;
  private boolean myWholeTextSelected = false;
  private final List<DocumentListener> myDocumentListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final List<FocusListener> myFocusListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private boolean myIsListenerInstalled = false;
  private boolean myIsViewer;
  private boolean myIsSupplementary;
  private boolean myInheritSwingFont = true;
  private Color myEnforcedBgColor = null;
  private boolean myOneLineMode; // use getter to access this field! It is allowed to override getter and change initial behaviour
  private boolean myEnsureWillComputePreferredSize;
  private Dimension myPassivePreferredSize;
  private CharSequence myHintText;
  private boolean myIsRendererWithSelection = false;
  private Color myRendererBg;
  private Color myRendererFg;
  private int myPreferredWidth = -1;
  private final List<EditorSettingsProvider> mySettingsProviders = new ArrayList<EditorSettingsProvider>();

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
    super.addFocusListener(new FocusListener() {
      @Override
      public void focusGained(FocusEvent e) {
        requestFocus();
      }

      @Override
      public void focusLost(FocusEvent e) {
      }
    });

    setFocusTraversalPolicyProvider(true);
    DelegatingToRootTraversalPolicy policy =
      SystemInfo.isJavaVersionAtLeast("1.7") ? new Jdk7DelegatingToRootTraversalPolicy() : new DelegatingToRootTraversalPolicy();
    setFocusTraversalPolicy(policy);

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

  public void addDocumentListener(DocumentListener listener) {
    myDocumentListeners.add(listener);
    installDocumentListener();
  }

  public void removeDocumentListener(DocumentListener listener) {
    myDocumentListeners.remove(listener);
    uninstallDocumentListener(false);
  }

  @Override
  public void beforeDocumentChange(DocumentEvent event) {
    for (DocumentListener documentListener : myDocumentListeners) {
      documentListener.beforeDocumentChange(event);
    }
  }

  @Override
  public void documentChanged(DocumentEvent event) {
    for (DocumentListener documentListener : myDocumentListeners) {
      documentListener.documentChanged(event);
    }
  }

  public Project getProject() {
    return myProject;
  }

  @Override
  public Document getDocument() {
    return myDocument;
  }

  public void setDocument(Document document) {
    if (myDocument != null) {
      uninstallDocumentListener(true);
    }

    myDocument = document;
    installDocumentListener();
    if (myEditor == null) return;

    //MainWatchPanel watches the oldEditor's focus in order to remove debugger combobox when focus is lost
    //we should first transfer focus to new oldEditor and only then remove current oldEditor
    //MainWatchPanel check that oldEditor.getParent == newEditor.getParent and does not remove oldEditor in such cases

    boolean isFocused = isFocusOwner();
    Editor editor = myEditor;
    myEditor = createEditor();
    releaseEditor(editor);
    add(myEditor.getComponent(), BorderLayout.CENTER);

    validate();
    if (isFocused) {
      myEditor.getContentComponent().requestFocus();
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

  public void setText(@Nullable final String text) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
          @Override
          public void run() {
            myDocument.replaceString(0, myDocument.getTextLength(), text == null ? "" : text);
            if (myEditor != null) {
              final CaretModel caretModel = myEditor.getCaretModel();
              if (caretModel.getOffset() >= myDocument.getTextLength()) {
                caretModel.moveToOffset(myDocument.getTextLength());
              }
            }
          }
        }, null, null, UndoConfirmationPolicy.DEFAULT, getDocument());
      }
    });
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

  void releaseEditor(@NotNull final Editor editor) {
    if (myProject != null && myIsViewer) {
      final PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(editor.getDocument());
      if (psiFile != null) {
        DaemonCodeAnalyzer.getInstance(myProject).setHighlightingEnabled(psiFile, true);
      }
    }

    remove(editor.getComponent());

    editor.getContentComponent().removeFocusListener(this);

    final Application application = ApplicationManager.getApplication();
    final Runnable runnable = new Runnable() {
      @Override
      public void run() {
        if (!editor.isDisposed()) {
          EditorFactory.getInstance().releaseEditor(editor);
        }
      }
    };

    if (application.isUnitTestMode() || application.isDispatchThread()) {
      runnable.run();
    }
    else {
      application.invokeLater(runnable);
    }
  }

  @Override
  public void addNotify() {
    releaseEditor();

    boolean isFocused = isFocusOwner();

    initEditor();

    super.addNotify();

    if (myNextFocusable != null) {
      myEditor.getContentComponent().setNextFocusableComponent(myNextFocusable);
      myNextFocusable = null;
    }
    revalidate();
    if (isFocused) {
      requestFocus();
    }
  }

  private void initEditor() {
    myEditor = createEditor();
    myEditor.getContentComponent().setEnabled(isEnabled());
    add(myEditor.getComponent(), BorderLayout.CENTER);
  }

  @Override
  public void removeNotify() {
    super.removeNotify();
    releaseEditor();
  }

  private void releaseEditor() {
    if (myEditor == null) return;
    
    final Editor editor = myEditor;
    myEditor = null;
    
    // releasing an editor implies removing it from a component hierarchy
    // invokeLater in required because releaseEditor() may be called from
    // removeNotify(), so we need to let swing complete its removeNotify() chain
    // and only then execute another removal from the hierarchy. Otherwise
    // swing goes nuts because of nested removals and indices get corrupted
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        releaseEditor(editor);
      }
    });
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

  protected void initOneLineMode(final EditorEx editor) {
    final boolean isOneLineMode = isOneLineMode();

    // set mode in editor
    editor.setOneLineMode(isOneLineMode);

    EditorColorsManager colorsManager = EditorColorsManager.getInstance();
    final EditorColorsScheme defaultScheme =
      UIUtil.isUnderDarcula() ? colorsManager.getGlobalScheme() : colorsManager.getScheme(EditorColorsManager.DEFAULT_SCHEME_NAME);
    EditorColorsScheme customGlobalScheme = isOneLineMode? defaultScheme : null;

    editor.setColorsScheme(editor.createBoundColorSchemeDelegate(customGlobalScheme));

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

  protected EditorEx createEditor() {
    LOG.assertTrue(myDocument != null);

    final EditorFactory factory = EditorFactory.getInstance();
    EditorEx editor;
    if (myIsViewer) {
      editor = myProject == null ? (EditorEx)factory.createViewer(myDocument) : (EditorEx)factory.createViewer(myDocument, myProject);
    }
    else {
      editor = myProject == null ? (EditorEx)factory.createEditor(myDocument) : (EditorEx)factory.createEditor(myDocument, myProject);
    }

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

    if (myProject != null && myFileType != null) {
      editor.setHighlighter(EditorHighlighterFactory.getInstance().createEditorHighlighter(myProject, myFileType));
    }

    editor.getSettings().setCaretRowShown(false);

    editor.setOneLineMode(myOneLineMode);
    editor.getCaretModel().moveToOffset(myDocument.getTextLength());

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
    
    editor.setPlaceholder(myHintText);

    initOneLineMode(editor);

    if (myIsRendererWithSelection) {
      ((EditorImpl)editor).setPaintSelection(true);
      editor.getColorsScheme().setColor(EditorColors.SELECTION_BACKGROUND_COLOR, myRendererBg);
      editor.getColorsScheme().setColor(EditorColors.SELECTION_FOREGROUND_COLOR, myRendererFg);
      editor.getSelectionModel().setSelection(0, myDocument.getTextLength());
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

      if (UIUtil.isUnderAquaLookAndFeel() || UIUtil.isUnderDarcula() || UIUtil.isUnderIntelliJLaF()) {
        editor.setBorder(UIUtil.isUnderDarcula() || UIUtil.isUnderIntelliJLaF() ?  new DarculaEditorTextFieldBorder() : new MacUIUtil.EditorTextFieldBorder(this));
        editor.addFocusListener(new FocusChangeListener() {
          @Override
          public void focusGained(Editor editor) {
            repaint();
          }

          @Override
          public void focusLost(Editor editor) {
            repaint();
          }
        });
      }
      else if (UIUtil.isUnderAlloyLookAndFeel() || UIUtil.isUnderJGoodiesLookAndFeel()) {
        editor.setBorder(BorderFactory.createCompoundBorder(UIUtil.getTextFieldBorder(), BorderFactory.createEmptyBorder(1, 1, 1, 1)));
      }
      else {
        editor.setBorder(BorderFactory.createCompoundBorder(UIUtil.getTextFieldBorder(), BorderFactory.createEmptyBorder(2, 2, 2, 2)));
      }
    }
  }

  private void setupEditorFont(final EditorEx editor) {
    if (myInheritSwingFont) {
      editor.getColorsScheme().setEditorFontName(getFont().getFontName());
      editor.getColorsScheme().setEditorFontSize(getFont().getSize());
    }
  }

  protected boolean shouldHaveBorder() {
    return true;
  }

  @Override
  public void setEnabled(boolean enabled) {
    if (isEnabled() != enabled) {
      super.setEnabled(enabled);
      setViewerEnabled(enabled);
      EditorEx editor = myEditor;
      if (editor == null) {
        return;
      }
      releaseEditor(editor);
      initEditor();
      revalidate();
    }
  }

  protected void setViewerEnabled(boolean enabled) {
    myIsViewer = !enabled;
  }

  @Override
  public Color getBackground() {
    Color color = getBackgroundColor(isEnabled(), EditorColorsUtil.getGlobalOrDefaultColorScheme());
    return color != null ? color : super.getBackground();
  }

  private Color getBackgroundColor(boolean enabled, final EditorColorsScheme colorsScheme){
    if (myEnforcedBgColor != null) return myEnforcedBgColor;
    if (UIUtil.getParentOfType(CellRendererPane.class, this) != null && (UIUtil.isUnderDarcula() || UIUtil.isUnderIntelliJLaF())) {
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
    if (super.isPreferredSizeSet()) {
      return super.getPreferredSize();
    }

    boolean toReleaseEditor = false;
    if (myEditor == null && myEnsureWillComputePreferredSize) {
      myEnsureWillComputePreferredSize = false;
      initEditor();
      toReleaseEditor = true;
    }


    Dimension size = new Dimension(100, 20);
    if (myEditor != null) {
      final Dimension preferredSize = new Dimension(myEditor.getComponent().getPreferredSize());

      if (myPreferredWidth != -1) {
        preferredSize.width = myPreferredWidth;
      }

      JBInsets.addTo(preferredSize, getInsets());
      size = preferredSize;
    } else if (myPassivePreferredSize != null) {
      size = myPassivePreferredSize;
    }

    if (toReleaseEditor) {
      releaseEditor();
      myPassivePreferredSize = size;
    }

    return size;
  }

  @Override
  public Dimension getMinimumSize() {
    if (super.isMinimumSizeSet()) {
      return super.getMinimumSize();
    }

    Dimension size = new Dimension(1, 20);
    if (myEditor != null) {
      size.height = myEditor.getLineHeight();

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
      myEditor.getContentComponent().requestFocus();
      myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    }
    else {
      super.requestFocus();
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

  @Override
  public Object getData(String dataId) {
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

  public void setFileType(FileType fileType) {
    setNewDocumentAndFileType(fileType, getDocument());
  }

  public void setNewDocumentAndFileType(final FileType fileType, Document document) {
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
  
  public void addSettingsProvider(EditorSettingsProvider provider) {
    mySettingsProviders.add(provider);
  }

  public boolean removeSettingsProvider(EditorSettingsProvider provider) {
    return mySettingsProviders.remove(provider);
  }

  private static class Jdk7DelegatingToRootTraversalPolicy extends DelegatingToRootTraversalPolicy {
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
      return super.getDefaultComponent(aContainer);
    }
  }

  private static class DelegatingToRootTraversalPolicy extends AbstractDelegatingToRootTraversalPolicy {
    @Override
    public Component getDefaultComponent(final Container aContainer) {
      final Editor editor = aContainer instanceof EditorTextField ? ((EditorTextField)aContainer).getEditor():null;
      if (editor != null) return editor.getContentComponent();
      return aContainer;
    }
  }

}
