/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.wm.ex.AbstractDelegatingToRootTraversalPolicy;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.IJSwingUtilities;
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
                                                       DocumentBasedComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.EditorTextField");
  public static final Key<Boolean> SUPPLEMENTARY_KEY = Key.create("Supplementary");

  private Document myDocument;
  private final Project myProject;
  private FileType myFileType;
  private EditorEx myEditor = null;
  private Component myNextFocusable = null;
  private boolean myWholeTextSelected = false;
  private final List<DocumentListener> myDocumentListeners = new ArrayList<DocumentListener>();
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
    addFocusListener(new FocusListener() {
      @Override
      public void focusGained(FocusEvent e) {
        requestFocus();
      }

      @Override
      public void focusLost(FocusEvent e) {
      }
    });

    pleaseHandleShiftTab();

    setFont(UIManager.getFont("TextField.font"));
  }

  private void pleaseHandleShiftTab() {
    setFocusTraversalPolicyProvider(true);
    setFocusTraversalPolicy(new DelegatingToRootTraversalPolicy());
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
      /*
      final UndoManager undoManager = myProject != null
      ? UndoManager.getInstance(myProject)
      : UndoManager.getGlobalInstance();
      undoManager.clearUndoRedoQueue(myDocument);
      */

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
      myEditor.getSelectionModel().setSelection(0, myDocument.getTextLength());
    }
    else {
      myWholeTextSelected = true;
    }
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

  void releaseEditor(final Editor editor) {
    if (myProject != null && myIsViewer) {
      final PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(editor.getDocument());
      if (psiFile != null) {
        DaemonCodeAnalyzer.getInstance(myProject).setHighlightingEnabled(psiFile, true);
      }
    }

    remove(editor.getComponent());
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
    } else {
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
    final JComponent component = myEditor.getComponent();
    add(component, BorderLayout.CENTER);
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

    final EditorColorsManager mgr = EditorColorsManager.getInstance();
    final EditorColorsScheme defaultScheme = UIUtil.isUnderDarcula()
                                             ? mgr.getScheme(mgr.getGlobalScheme().getName())
                                             : mgr.getScheme(EditorColorsManager.DEFAULT_SCHEME_NAME);
    final EditorColorsScheme customGlobalScheme = isOneLineMode ? defaultScheme : null;

    // Probably we need change scheme only for color schemas with white BG, but on the other hand
    // FindUsages dialog always uses FindUsages color scheme based on a default one and should be also fixed
    //
    //final EditorColorsScheme customGlobalScheme;
    //final EditorColorsScheme currentScheme = EditorColorsManager.getInstance().getGlobalScheme();
    //if (currentScheme.getDefaultBackground() == Color.WHITE) {
    //  customGlobalScheme = currentScheme;
    //} else {
    //  final EditorColorsScheme defaultScheme = EditorColorsManager.getInstance().getScheme(EditorColorsManager.DEFAULT_SCHEME_NAME);
    //  customGlobalScheme = isOneLineMode ? defaultScheme : null;
    //}
    editor.setColorsScheme(editor.createBoundColorSchemeDelegate(customGlobalScheme));

    final EditorColorsScheme colorsScheme = editor.getColorsScheme();
    colorsScheme.setColor(EditorColors.CARET_ROW_COLOR, null);
    editor.setColorsScheme(new DelegateColorScheme(colorsScheme) {
      @Override
      public TextAttributes getAttributes(TextAttributesKey key) {
        final TextAttributes attributes = super.getAttributes(key);
        if (!isEnabled() && attributes != null) {
          return new TextAttributes(UIUtil.getInactiveTextColor(), attributes.getBackgroundColor(), attributes.getEffectColor(), attributes.getEffectType(), attributes.getFontType());
        }

        return attributes;
      }
    });

    // color scheme settings:
    setupEditorFont(editor);
    updateBorder(editor);
    editor.setBackgroundColor(getBackgroundColor(!myIsViewer, colorsScheme));
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
    settings.setFoldingOutlineShown(false);
    settings.setLineNumbersShown(false);
    settings.setLineMarkerAreaShown(false);
    settings.setIndentGuidesShown(false);
    settings.setVirtualSpace(false);
    settings.setWheelFontChangeEnabled(false);
    editor.setHorizontalScrollbarVisible(false);
    editor.setVerticalScrollbarVisible(false);
    editor.setCaretEnabled(!myIsViewer);
    settings.setLineCursorWidth(1);

    if (myProject != null && myIsViewer) {
      final PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(editor.getDocument());
      if (psiFile != null) {
        DaemonCodeAnalyzer.getInstance(myProject).setHighlightingEnabled(psiFile, false);
      }
    }

    if (myProject != null && myFileType != null) {
      editor.setHighlighter(EditorHighlighterFactory.getInstance().createEditorHighlighter(myProject, myFileType));
    }

    final EditorColorsScheme colorsScheme = editor.getColorsScheme();
    colorsScheme.setColor(EditorColors.CARET_ROW_COLOR, null);
    if (!isEnabled()) {
      editor.setColorsScheme(new DelegateColorScheme(colorsScheme) {
        @Override
        public Color getColor(ColorKey key) {
          return super.getColor(key);
        }

        @Override
        public TextAttributes getAttributes(TextAttributesKey key) {
          final TextAttributes attributes = super.getAttributes(key);
          if (!isEnabled()) {
            return new TextAttributes(UIUtil.getInactiveTextColor(), attributes.getBackgroundColor(), attributes.getEffectColor(), attributes.getEffectType(), attributes.getFontType());
          }

          return attributes;
        }
      });
    }

    editor.setOneLineMode(myOneLineMode);
    editor.getCaretModel().moveToOffset(myDocument.getTextLength());

    if (!shouldHaveBorder()) {
      editor.setBorder(null);
    }

    if (myIsViewer) {
      editor.getSelectionModel().removeSelection();
    }
    else if (myWholeTextSelected) {
      editor.getSelectionModel().setSelection(0, myDocument.getTextLength());
    }

    editor.putUserData(SUPPLEMENTARY_KEY, myIsSupplementary);
    editor.getContentComponent().setFocusCycleRoot(false);
    
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

      if (UIUtil.isUnderAquaLookAndFeel() || UIUtil.isUnderDarcula()) {
        editor.setBorder(UIUtil.isUnderDarcula() ?  new DarculaEditorTextFieldBorder() : new MacUIUtil.EditorTextFieldBorder(this));
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
      myIsViewer = !enabled;
      if (myEditor == null) {
        return;
      }
      Editor editor = myEditor;
      releaseEditor(editor);
      myEditor = createEditor();
      add(myEditor.getComponent(), BorderLayout.CENTER);
      revalidate();
    }
  }

  private Color getBackgroundColor(boolean enabled, final EditorColorsScheme colorsScheme){
    if (myEnforcedBgColor != null) return myEnforcedBgColor;
    if (UIUtil.getParentOfType(CellRendererPane.class, this) != null && UIUtil.isUnderDarcula()) {
      return getParent().getBackground();
    }
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

      final Insets insets = getInsets();
      if (insets != null) {
        preferredSize.width += insets.left;
        preferredSize.width += insets.right;
        preferredSize.height += insets.top;
        preferredSize.height += insets.bottom;
      }
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

      size = UIUtil.addInsets(size, getInsets());
      size = UIUtil.addInsets(size, myEditor.getInsets());
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
  public Object getData(String dataId) {
    if (myEditor != null && myEditor.isRendererMode()) return null;

    if (PlatformDataKeys.EDITOR.is(dataId)) {
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

  private static class DelegatingToRootTraversalPolicy extends AbstractDelegatingToRootTraversalPolicy {
    @Override
    public Component getDefaultComponent(final Container aContainer) {
      final Editor editor = aContainer instanceof EditorTextField ? ((EditorTextField)aContainer).getEditor():null;
      if (editor != null) return editor.getContentComponent();
      return aContainer;
    }
  }

}
