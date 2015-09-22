/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.execution.filters.LineNumbersMapping;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.editor.impl.EditorFactoryImpl;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.DocumentUtil;

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
    final Document doc = ((EditorFactoryImpl)factory).createDocument("", true, false);
    doc.setReadOnly(true);
    myEditor = factory.createEditor(doc, project);
    EditorHighlighterFactory editorHighlighterFactory = EditorHighlighterFactory.getInstance();
    final SyntaxHighlighter syntaxHighlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(StdFileTypes.JAVA, project, null);
    ((EditorEx)myEditor).setHighlighter(editorHighlighterFactory.createEditorHighlighter(syntaxHighlighter, EditorColorsManager.getInstance().getGlobalScheme()));
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
    PsiFile psiFile = element.getContainingFile();
    final Document document = PsiDocumentManager.getInstance(element.getProject()).getDocument(psiFile);
    if (document != null) {
      int lineNumber = document.getLineNumber(element.getTextOffset());
      VirtualFile file = psiFile.getVirtualFile();
      if (file != null) {
        LineNumbersMapping mapping = file.getUserData(LineNumbersMapping.LINE_NUMBERS_MAPPING_KEY);
        if (mapping != null) {
          int mappedLine = mapping.sourceToBytecode(lineNumber);
          while (mappedLine == -1 && lineNumber < document.getLineCount()) {
            mappedLine = mapping.sourceToBytecode(++lineNumber);
          }
          if (mappedLine > 0) {
            lineNumber = mappedLine;
          }
        }
      }
      offset = bytecode.indexOf("LINENUMBER " + lineNumber);
      while (offset == -1 && lineNumber < document.getLineCount()) {
        offset = bytecode.indexOf("LINENUMBER " + (lineNumber++));
      }
    }
    setText(bytecode, Math.max(0, offset));
  }

  public void setText(final String bytecode, final int offset) {
    DocumentUtil.writeInRunUndoTransparentAction(new Runnable() {
      @Override
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

  public String getText() {
    return myEditor.getDocument().getText();
  }

  @Override
  public void dispose() {
    EditorFactory.getInstance().releaseEditor(myEditor);
  }
}
