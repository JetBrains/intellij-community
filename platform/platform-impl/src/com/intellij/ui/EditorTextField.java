/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
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

/**
 * @author max
 */
public class EditorTextField extends JPanel implements DocumentListener, TextComponent, DataProvider,
                                                       DocumentBasedComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.EditorTextField");
  public static final Key<Boolean> SUPPLEMENTARY_KEY = Key.create("Supplementary");

  private Document myDocument;
  private final Project myProject;
  private FileType myFileType;
  private EditorEx myEditor = null;
  private Component myNextFocusable = null;
  private boolean myWholeTextSelected = false;
  private final ArrayList<DocumentListener> myDocumentListeners = new ArrayList<DocumentListener>();
  private boolean myIsListenerInstalled = false;
  private boolean myIsViewer;
  private boolean myIsSupplementary;
  private boolean myInheritSwingFont = true;
  private Color myEnforcedBgColor = null;

  public EditorTextField() {
    this("");
  }

  public EditorTextField(@NotNull String text) {
    this(EditorFactory.getInstance().createDocument(text), null, FileTypes.PLAIN_TEXT);
  }

  public EditorTextField(@NotNull String text, Project project, FileType fileType) {
    this(EditorFactory.getInstance().createDocument(text), project, fileType, false);
  }

  public EditorTextField(Document document, Project project, FileType fileType) {
    this(document, project, fileType, false);
  }

  public EditorTextField(Document document, Project project, FileType fileType, boolean isViewer) {
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
      public void focusGained(FocusEvent e) {
        requestFocus();
      }

      public void focusLost(FocusEvent e) {
      }
    });

    pleaseHandleShiftTab();
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

  public void beforeDocumentChange(DocumentEvent event) {
    for (DocumentListener documentListener : myDocumentListeners) {
      documentListener.beforeDocumentChange(event);
    }
  }

  public void documentChanged(DocumentEvent event) {
    for (DocumentListener documentListener : myDocumentListeners) {
      documentListener.documentChanged(event);
    }
  }

  public Project getProject() {
    return myProject;
  }

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

  public void setText(final String text) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
          public void run() {
            myDocument.replaceString(0, myDocument.getTextLength(), text);
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

  public void addNotify() {
    releaseEditor();

    boolean isFocused = isFocusOwner();

    myEditor = createEditor();
    add(myEditor.getComponent(), BorderLayout.CENTER);

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

  public void removeNotify() {
    super.removeNotify();
    releaseEditor();
  }

  private void releaseEditor() {
    if (myEditor != null) {
      // Theoretically this condition is always true but under some Linux VMs it seems removeNotify is called twice
      // or called for components addNotify haven't been called for.
      releaseEditor(myEditor);
      myEditor = null;
    }
  }

  public void setFont(Font font) {
    super.setFont(font);
    if (myEditor != null) {
      setupEditorFont(myEditor);
    }
  }

  protected EditorEx createEditor() {
    LOG.assertTrue(myDocument != null);

    final EditorFactory factory = EditorFactory.getInstance();
    EditorEx editor;
    if (!myIsViewer) {
      editor = myProject != null
               ? (EditorEx)factory.createEditor(myDocument, myProject)
               : (EditorEx)factory.createEditor(myDocument);
    }
    else {
      editor = myProject != null
               ? (EditorEx)factory.createViewer(myDocument, myProject)
               : (EditorEx)factory.createViewer(myDocument);
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
    editor.setHorizontalScrollbarVisible(false);
    editor.setVerticalScrollbarVisible(false);
    editor.setCaretEnabled(!myIsViewer);
    settings.setLineCursorWidth(1);

    setupEditorFont(editor);

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

    editor.setOneLineMode(true);
    editor.getCaretModel().moveToOffset(myDocument.getTextLength());

    if (!shouldHaveBorder()) {
      editor.setBorder(null);
    }

    updateBorder(editor);

    if (myIsViewer) {
      editor.getSelectionModel().removeSelection();
    }
    else if (myWholeTextSelected) {
      editor.getSelectionModel().setSelection(0, myDocument.getTextLength());
    }

    editor.setBackgroundColor(getBackgroundColor(!myIsViewer));

    editor.putUserData(SUPPLEMENTARY_KEY, myIsSupplementary);
    editor.getContentComponent().setFocusCycleRoot(false);

    return editor;
  }

  protected void updateBorder(@NotNull final EditorEx editor) {
    if (UIUtil.isUnderAquaLookAndFeel() && editor.isOneLineMode()) {
      final Container parent = getParent();
      if (parent instanceof JTable || parent instanceof CellRendererPane) return;

      editor.setBorder(new MacUIUtil.EditorTextFieldBorder(this));
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

  private Color getBackgroundColor(boolean enabled){
    if (myEnforcedBgColor != null) return myEnforcedBgColor;
    return enabled
           ? EditorColorsManager.getInstance().getGlobalScheme().getDefaultBackground()
           : UIUtil.getInactiveTextFieldBackgroundColor();
  }

  public Dimension getPreferredSize() {
    if (myEditor != null) {
      final Dimension preferredSize = new Dimension(myEditor.getComponent().getPreferredSize());
      final Insets insets = getInsets();
      if (insets != null) {
        preferredSize.width += insets.left;
        preferredSize.width += insets.right;
        preferredSize.height += insets.top;
        preferredSize.height += insets.bottom;
      }

      return preferredSize;
    }
    return new Dimension(100, 20);
  }

  public Component getNextFocusableComponent() {
    if (myEditor == null && myNextFocusable == null) return super.getNextFocusableComponent();
    if (myEditor == null) return myNextFocusable;
    return myEditor.getContentComponent().getNextFocusableComponent();
  }

  public void setNextFocusableComponent(Component aComponent) {
    if (myEditor != null) {
      myEditor.getContentComponent().setNextFocusableComponent(aComponent);
      return;
    }
    myNextFocusable = aComponent;
  }


  protected boolean processKeyBinding(KeyStroke ks, KeyEvent e, int condition, boolean pressed) {
    if (e.isConsumed() || !myEditor.processKeyTyped(e)) {
      return super.processKeyBinding(ks, e, condition, pressed);
    }
    return true;
  }

  public void requestFocus() {
    if (myEditor != null) {
      myEditor.getContentComponent().requestFocus();
      myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    }
    else {
      super.requestFocus();
    }
  }

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

  public Object getData(String dataId) {
    if (myEditor != null && myEditor.isRendererMode()) return null;

    if (PlatformDataKeys.EDITOR.is(dataId)) {
      return myEditor;
    }

    return null;
  }

  public void setNewDocumentAndFileType(final FileType fileType, Document document) {
    myFileType = fileType;
    setDocument(document);
  }

  private static class DelegatingToRootTraversalPolicy extends FocusTraversalPolicy {
    @Override
    public Component getComponentAfter(final Container aContainer, final Component aComponent) {
      final Container cycleRootAncestor = aContainer.getFocusCycleRootAncestor();
      return cycleRootAncestor.getFocusTraversalPolicy().getComponentAfter(cycleRootAncestor, aContainer);
    }

    @Override
    public Component getComponentBefore(final Container aContainer, final Component aComponent) {
      final Container cycleRootAncestor = aContainer.getFocusCycleRootAncestor();
      return cycleRootAncestor.getFocusTraversalPolicy().getComponentBefore(cycleRootAncestor, aContainer);
    }

    @Override
    public Component getFirstComponent(final Container aContainer) {
      final Container cycleRootAncestor = aContainer.getFocusCycleRootAncestor();
      return cycleRootAncestor.getFocusTraversalPolicy().getFirstComponent(cycleRootAncestor);
    }

    @Override
    public Component getLastComponent(final Container aContainer) {
      final Container cycleRootAncestor = aContainer.getFocusCycleRootAncestor();
      return cycleRootAncestor.getFocusTraversalPolicy().getLastComponent(cycleRootAncestor);
    }

    @Override
    public Component getDefaultComponent(final Container aContainer) {
      final Editor editor = aContainer instanceof EditorTextField ? ((EditorTextField)aContainer).getEditor():null;
      if (editor != null) return editor.getContentComponent();
      return aContainer;
    }
  }

}
