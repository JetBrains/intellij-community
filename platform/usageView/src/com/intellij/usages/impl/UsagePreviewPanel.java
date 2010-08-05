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

package com.intellij.usages.impl;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * @author cdr
 */
public class UsagePreviewPanel extends JPanel implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.usages.impl.UsagePreviewPanel");
  private Editor myEditor;
  private final Project myProject;
  private volatile boolean isDisposed = false;

  public UsagePreviewPanel(final Project project) {
    myProject = project;
    setLayout(new BorderLayout());
    setBorder(IdeBorderFactory.createBorder());
  }

  private void resetEditor(@NotNull final List<UsageInfo> infos) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    PsiElement psiElement = infos.get(0).getElement();
    if (psiElement == null) return;
    PsiFile psiFile = psiElement.getContainingFile();
    if (psiFile == null) return;

    PsiLanguageInjectionHost host = InjectedLanguageManager.getInstance(myProject).getInjectionHost(psiFile);
    if (host != null) {
      psiFile = host.getContainingFile();
      if (psiFile == null) return;
    }

    final Document document = PsiDocumentManager.getInstance(psiFile.getProject()).getDocument(psiFile);
    if (document == null) return;
    if (myEditor == null || document != myEditor.getDocument()) {
      releaseEditor();
      removeAll();
      myEditor = createEditor(psiFile, document);
      if (myEditor == null) return;
      myEditor.setBorder(null);
      add(myEditor.getComponent(), BorderLayout.CENTER);

      revalidate();
    }

    final Editor editor = myEditor;
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        if (myProject.isDisposed()) return;
        highlight(infos, editor);
      }
    });
  }

  private static final Key<Boolean> IN_PREVIEW_USAGE_FLAG = Key.create("IN_PREVIEW_USAGE_FLAG");
  private void highlight(final List<UsageInfo> infos, final Editor editor) {
    if (editor != myEditor) return; //already disposed
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    MarkupModel markupModel = myEditor.getMarkupModel();
    for (RangeHighlighter highlighter : markupModel.getAllHighlighters()) {
      if (highlighter.getUserData(IN_PREVIEW_USAGE_FLAG) != null) {
        markupModel.removeHighlighter(highlighter);
      }
    }
    for (int i = infos.size()-1; i>=0; i--) { // finish with the first usage so that caret end up there
      UsageInfo info = infos.get(i);
      PsiElement psiElement = info.getElement();
      if (psiElement == null || !psiElement.isValid()) continue;
      int offsetInFile = psiElement.getTextOffset();

      EditorColorsManager colorManager = EditorColorsManager.getInstance();
      TextAttributes attributes = colorManager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);

      TextRange elementRange = psiElement.getTextRange();
      TextRange infoRange = info.getRange();
      TextRange textRange = elementRange.intersection(infoRange);
      if (textRange == null) textRange = elementRange;
      // hack to determine element range to highlight
      if (psiElement instanceof PsiNamedElement && !(psiElement instanceof PsiFile)) {
        PsiFile psiFile = psiElement.getContainingFile();
        PsiElement nameElement = psiFile.findElementAt(offsetInFile);
        if (nameElement != null) {
          textRange = nameElement.getTextRange();
        }
      }
      // highlight injected element in host document textrange
      textRange = InjectedLanguageManager.getInstance(myProject).injectedToHost(psiElement, textRange);

      RangeHighlighter highlighter = markupModel.addRangeHighlighter(textRange.getStartOffset(), textRange.getEndOffset(),
                                                                                   HighlighterLayer.ADDITIONAL_SYNTAX, attributes,
                                                                                   HighlighterTargetArea.EXACT_RANGE);
      highlighter.putUserData(IN_PREVIEW_USAGE_FLAG, Boolean.TRUE);
      myEditor.getCaretModel().moveToOffset(textRange.getEndOffset());
    }
    myEditor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
  }

  private static final Key<UsagePreviewPanel> PREVIEW_EDITOR_FLAG = Key.create("PREVIEW_EDITOR_FLAG");
  private Editor createEditor(final PsiFile psiFile, Document document) {
    if (isDisposed) return null;
    Project project = psiFile.getProject();

    Editor editor = EditorFactory.getInstance().createEditor(document, project, psiFile.getVirtualFile(), true);

    EditorSettings settings = editor.getSettings();
    settings.setLineMarkerAreaShown(false);
    settings.setIndentGuidesShown(false);
    settings.setFoldingOutlineShown(false);
    settings.setAdditionalColumnsCount(0);
    settings.setAdditionalLinesCount(0);
    settings.setVirtualSpace(true);

    editor.putUserData(PREVIEW_EDITOR_FLAG, this);
    return editor;
  }

  public void dispose() {
    isDisposed = true;
    releaseEditor();
    for (Editor editor : EditorFactory.getInstance().getAllEditors()) {
      if (editor.getProject() == myProject && editor.getUserData(PREVIEW_EDITOR_FLAG) == this) {
        LOG.error("Editor was not released:"+editor);
      }
    }
  }

  private void releaseEditor() {
    if (myEditor != null) {
      EditorFactory.getInstance().releaseEditor(myEditor);
      myEditor = null;
    }
  }

  public void updateLayout(final List<UsageInfo> infos) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      public void run() {
        if (myProject.isDisposed()) return;
        if (infos == null) {
          releaseEditor();
          removeAll();
          JComponent titleComp = new JLabel(UsageViewBundle.message("select.the.usage.to.preview"));
          add(titleComp, BorderLayout.CENTER);
          revalidate();
        }
        else {
          resetEditor(infos);
        }
      }
    });
  }
}
