// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.ui;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.event.MarkupModelListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.EditorComboBoxEditor;
import com.intellij.ui.EditorComboBoxRenderer;
import com.intellij.ui.EditorTextField;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.impl.XDebuggerHistoryManager;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
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
    myComboBox = new ComboBox<>(myModel, 100);
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

  public ComboBox getComboBox() {
    return myComboBox;
  }

  @Override
  public JComponent getComponent() {
    return myComponent;
  }

  @Override
  @Nullable
  public Editor getEditor() {
    return myEditor.getEditorTextField().getEditor();
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
    myComboBox.setMaximumRowCount(XDebuggerHistoryManager.MAX_RECENT_EXPRESSIONS);
  }

  @Override
  protected void onHistoryChanged() {
    fillComboBox();
  }

  private void fillComboBox() {
    myModel.setSelectedItem(null); // must do this to preserve current editor
    myModel.replaceAll(getRecentExpressions());
  }

  @Override
  protected void doSetText(XExpression text) {
    myExpression = text;
    myEditor.getEditorTextField().setNewDocumentAndFileType(getFileType(text), createDocument(text));
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
  protected void prepareEditor(Editor editor) {
    super.prepareEditor(editor);
    editor.getColorsScheme().setEditorFontSize(Math.min(myComboBox.getFont().getSize(), EditorUtil.getEditorFont().getSize()));
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
            int errors = 0;
            @Override
            public void afterAdded(@NotNull RangeHighlighterEx highlighter) {
              processHighlighter(highlighter, true);
            }

            @Override
            public void beforeRemoved(@NotNull RangeHighlighterEx highlighter) {
              processHighlighter(highlighter, false);
            }

            void processHighlighter(@NotNull RangeHighlighterEx highlighter, boolean add) {
              HighlightInfo info = HighlightInfo.fromRangeHighlighter(highlighter);
              if (info != null && HighlightSeverity.ERROR.equals(info.getSeverity())) {
                errors += add ? 1 : -1;
                if (errors == 0 || errors == 1) {
                  myComboBox.putClientProperty("JComponent.outline", errors > 0 ? "error" : null);
                  myComboBox.repaint();
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
      Object document = myDelegate.getItem();
      if (document instanceof Document) { // sometimes null on Mac
        return getEditorsProvider().createExpression(getProject(), (Document)document, myExpression.getLanguage(), myExpression.getMode());
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
