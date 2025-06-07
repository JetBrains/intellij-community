// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.event.MarkupModelListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.EditorComboBoxEditor;
import com.intellij.ui.EditorComboBoxRenderer;
import com.intellij.ui.EditorTextField;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.EdtInvocationManager;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class XDebuggerExpressionComboBox extends XDebuggerEditorBase {
  private final JComponent myComponent;
  private final ComboBox<XExpression> myComboBox;
  private final CollectionComboBoxModel<XExpression> myModel = new CollectionComboBoxModel<>();
  private XDebuggerComboBoxEditor myEditor;
  private XExpression myExpression;
  private Function<? super Document, ? extends Document> myDocumentProcessor = Function.identity();

  public XDebuggerExpressionComboBox(@NotNull Project project, @NotNull XDebuggerEditorsProvider debuggerEditorsProvider, @Nullable @NonNls String historyId,
                                     @Nullable XSourcePosition sourcePosition, boolean showEditor, boolean languageInside) {
    super(project, debuggerEditorsProvider, EvaluationMode.EXPRESSION, historyId, sourcePosition);
    myComboBox = createComboBox(myModel, 100);
    myComboBox.setEditable(true);
    myExpression = XExpressionImpl.EMPTY_EXPRESSION;
    Dimension minimumSize = new Dimension(myComboBox.getMinimumSize());
    minimumSize.width = 100;
    myComboBox.setMinimumSize(minimumSize);
    initEditor(showEditor, languageInside);
    fillComboBox();
    myComponent = JBUI.Panels.simplePanel().addToTop(myComboBox);
    setExpression(myExpression);
  }

  protected ComboBox<XExpression> createComboBox(CollectionComboBoxModel<XExpression> model, int width) {
    return new ComboBox<>(model, width);
  }

  public ComboBox getComboBox() {
    return myComboBox;
  }

  @Override
  public JComponent getComponent() {
    return myComponent;
  }

  @Override
  public @Nullable Editor getEditor() {
    return myEditor.getEditorTextField().getEditor(true);
  }

  @Override
  public JComponent getEditorComponent() {
    return myEditor.getEditorTextField();
  }

  @Override
  public void setEnabled(boolean enable) {
    if (enable == myComboBox.isEnabled()) return;

    UIUtil.setEnabled(myComponent, enable, true);
    //myComboBox.setEditable(enable);

    if (enable) {
      //initEditor();
    }
    else {
      myExpression = getExpression();
    }
    super.setEnabled(enable);
  }

  private void initEditor(boolean showMultiline, boolean languageInside) {
    myEditor = new XDebuggerComboBoxEditor(showMultiline, languageInside);
    myComboBox.setEditor(myEditor);
    //myEditor.setItem(myExpression);
    myComboBox.setRenderer(new EditorComboBoxRenderer(myEditor));
    myComboBox.setMaximumRowCount(10);
  }

  @Override
  protected void onHistoryChanged() {
    fillComboBox();
  }

  private void fillComboBox() {
    myModel.setSelectedItem(null); // must do this to preserve current editor
    myModel.replaceAll(getRecentExpressions());
  }

  private static final Key<Boolean> DUMMY_DOCUMENT = Key.create("DummyDocument");

  @Override
  protected void doSetText(XExpression text) {
    myExpression = text;
    // set a dummy document immediately
    DocumentImpl dummyDocument = new DocumentImpl(text.getExpression());
    dummyDocument.putUserData(DUMMY_DOCUMENT, true);
    myEditor.getEditorTextField().setNewDocumentAndFileType(getFileType(text), dummyDocument);
    // schedule the real document creation
    ReadAction.nonBlocking(() -> createDocument(text))
      .inSmartMode(getProject())
      .finishOnUiThread(ModalityState.any(), document -> {
        myEditor.getEditorTextField().setNewDocumentAndFileType(getFileType(text), document);
        getEditorsProvider().afterEditorCreated(getEditor());
      })
      .coalesceBy(this)
      .submit(AppExecutorUtil.getAppExecutorService());
  }

  @Override
  public XExpression getExpression() {
    XExpression item = myEditor.getItem();
    return item != null ? item : myExpression;
  }

  @Override
  protected Document createDocument(XExpression text) {
    return myDocumentProcessor.apply(super.createDocument(text));
  }

  public void setDocumentProcessor(Function<? super Document, ? extends Document> documentProcessor) {
    myDocumentProcessor = documentProcessor;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myEditor.getEditorTextField();
  }

  @Override
  public void selectAll() {
    myComboBox.getEditor().selectAll();
  }

  @Override
  protected void prepareEditor(EditorEx editor) {
    super.prepareEditor(editor);
    editor.getColorsScheme().setEditorFontSize(Math.min(myComboBox.getFont().getSize(), EditorUtil.getEditorFont().getSize()));
  }

  // Workaround for IDEA-273987, IDEA-278153.
  // Should be removed after IDEA-285001 is fixed
  public void fixEditorNotReleasedFalsePositiveException(@NotNull Project project, @NotNull Disposable parentDisposable) {
    EditorTextField field = ObjectUtils.tryCast(getEditorComponent(), EditorTextField.class);
    if (field == null) return;
    Disposable disposable = Disposer.newDisposable("XDebuggerExpressionComboBox Disposable");
    Disposer.register(parentDisposable, () -> {
      // In case the project is closing this block is called
      // from the BaseContentCloseListener#disposeContent
      // and then removes editor with EditorComboBox#releaseLater.
      // The latter causes a false-positive exception (IDEA-273987) that editor is not released
      // when validation is running (see  IDEA-285001).
      // Until IDEA-285001 is fixed this one is scheduled for next EDT call
      // to let Disposer.register(session.getProject(), disposable) dispose an editor
      // with correct way when project is closed.
      // If this one is triggered because the tool window is closed
      // then it's ok to release it later.
      ApplicationManager.getApplication().invokeLater(() -> Disposer.dispose(disposable));
    });
    Disposer.register(project, disposable);
    field.setDisposedWith(disposable);
  }

  private class XDebuggerComboBoxEditor implements ComboBoxEditor {
    private final JComponent myPanel;
    private final EditorComboBoxEditor myDelegate;

    XDebuggerComboBoxEditor(boolean showMultiline, boolean languageInside) {
      myDelegate = new EditorComboBoxEditor(getProject(), getEditorsProvider().getFileType()) {
        @Override
        protected void onEditorCreate(EditorEx editor) {
          editor.putUserData(DebuggerCopyPastePreprocessor.REMOVE_NEWLINES_ON_PASTE, true);
          prepareEditor(editor);
          if (showMultiline) {
            setExpandable(editor);
          }
          foldNewLines(editor);
          editor.getFilteredDocumentMarkupModel().addMarkupModelListener(((EditorImpl)editor).getDisposable(), new MarkupModelListener() {
            private final AtomicInteger errors = new AtomicInteger();
            @Override
            public void afterAdded(@NotNull RangeHighlighterEx highlighter) {
              processHighlighter(highlighter, true);
            }

            @Override
            public void afterRemoved(@NotNull RangeHighlighterEx highlighter) {
              processHighlighter(highlighter, false);
            }

            void processHighlighter(@NotNull RangeHighlighterEx highlighter, boolean add) {
              HighlightInfo info = HighlightInfo.fromRangeHighlighter(highlighter);
              if (info != null && HighlightSeverity.ERROR.equals(info.getSeverity())) {
                int value = errors.addAndGet(add ? 1 : -1);
                if (value == 0 || value == 1) {
                  EdtInvocationManager.invokeLaterIfNeeded(() -> {
                    if (UIUtil.isShowing(myComboBox)) {
                      myComboBox.putClientProperty("JComponent.outline", value > 0 ? "error" : null);
                      myComboBox.repaint();
                    }
                  });
                }
              }
            }
          });
        }
      };
      myDelegate.getEditorComponent().setFontInheritedFromLAF(false);
      JComponent comp = myDelegate.getEditorComponent();
      if (languageInside) {
        comp = addChooser(comp);
      }
      if (showMultiline) {
        comp = addExpand(comp, true);
      }
      myPanel = comp;
    }

    public EditorTextField getEditorTextField() {
      return myDelegate.getEditorComponent();
    }

    @Override
    public JComponent getEditorComponent() {
      return myPanel;
    }

    @Override
    public void setItem(Object anObject) {
      if (anObject != null) { // do not reset the editor on null
        setExpression((XExpression)anObject);
      }
    }

    @Override
    public XExpression getItem() {
      Object item = myDelegate.getItem();
      if (item instanceof Document document && !Boolean.TRUE.equals(document.getUserData(DUMMY_DOCUMENT))) { // sometimes null on Mac
        return getEditorsProvider().createExpression(getProject(), document, myExpression.getLanguage(), myExpression.getMode());
      }
      return null;
    }

    @Override
    public void selectAll() {
      myDelegate.selectAll();
    }

    @Override
    public void addActionListener(ActionListener l) {
      myDelegate.addActionListener(l);
    }

    @Override
    public void removeActionListener(ActionListener l) {
      myDelegate.removeActionListener(l);
    }
  }
}
