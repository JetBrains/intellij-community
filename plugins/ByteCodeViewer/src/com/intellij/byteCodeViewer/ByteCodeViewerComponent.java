/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.byteCodeViewer;

import com.intellij.codeInsight.hint.EditorFragmentComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.ui.LightColors;

import javax.swing.*;
import java.awt.*;

/**
 * @author anna
 * @since 5/7/12
 */
public class ByteCodeViewerComponent extends JPanel implements Disposable {

  private final Editor myEditor;

  public ByteCodeViewerComponent(Project project, AnAction[] additionalActions) {
    super(new BorderLayout());
    final EditorFactory factory = EditorFactory.getInstance();
    final Document doc = factory.createDocument("");
    doc.setReadOnly(true);
    myEditor = factory.createEditor(doc, project);
    EditorHighlighterFactory editorHighlighterFactory = EditorHighlighterFactory.getInstance();
    final SyntaxHighlighter syntaxHighlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(StdFileTypes.JAVA, project, null);
    ((EditorEx)myEditor).setHighlighter(editorHighlighterFactory.createEditorHighlighter(syntaxHighlighter, EditorColorsManager.getInstance().getGlobalScheme()));
    ((EditorEx)myEditor).setBackgroundColor(EditorFragmentComponent.getBackgroundColor(myEditor));
    myEditor.getColorsScheme().setColor(EditorColors.CARET_ROW_COLOR, LightColors.SLIGHTLY_GRAY);
    ((EditorEx)myEditor).setCaretVisible(true);

    final EditorSettings settings = myEditor.getSettings();
    settings.setLineMarkerAreaShown(false);
    settings.setIndentGuidesShown(false);
    settings.setLineNumbersShown(false);
    settings.setFoldingOutlineShown(false);

    myEditor.setBorder(null);
    add(myEditor.getComponent(), BorderLayout.CENTER);
    final ActionManager actionManager = ActionManager.getInstance();
    final DefaultActionGroup actions = new DefaultActionGroup();
    if (additionalActions != null) {
      for (final AnAction action : additionalActions) {
        actions.add(action);
      }
    }
    add(actionManager.createActionToolbar(ActionPlaces.JAVADOC_TOOLBAR, actions, true).getComponent(), BorderLayout.NORTH);
  }

  public void setText(final String bytecode) {
    setText(bytecode, 0);
  }

  public void setText(final String bytecode, PsiElement element) {
    int offset = 0;
    final Document document = PsiDocumentManager.getInstance(element.getProject()).getDocument(element.getContainingFile());
    if (document != null) {
      int lineNumber = document.getLineNumber(element.getTextOffset());
      offset = bytecode.indexOf("LINENUMBER " + lineNumber);
      while (offset == -1 && lineNumber < document.getLineCount()) {
        offset = bytecode.indexOf("LINENUMBER " + (lineNumber++));
      }
    }
    setText(bytecode, Math.max(0, offset));
  }

  public void setText(final String bytecode, final int offset) {
    CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            Document fragmentDoc = myEditor.getDocument();
            fragmentDoc.setReadOnly(false);
            fragmentDoc.replaceString(0, fragmentDoc.getTextLength(), bytecode);
            fragmentDoc.setReadOnly(true);
            myEditor.getCaretModel().moveToOffset(offset);
            myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
          }
        });
      }
    });
  }

  public String getText() {
    return myEditor.getDocument().getText();
  }

  @Override
  public void dispose() {
    EditorFactory.getInstance().releaseEditor(myEditor);
  }
}
