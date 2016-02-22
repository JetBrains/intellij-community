/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.UsageContextPanel;
import com.intellij.usages.UsageView;
import com.intellij.usages.UsageViewPresentation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * @author cdr
 */
public class UsagePreviewPanel extends UsageContextPanelBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.usages.impl.UsagePreviewPanel");
  private Editor myEditor;
  private final boolean myIsEditor;

  public UsagePreviewPanel(@NotNull Project project, @NotNull UsageViewPresentation presentation) {
    this(project, presentation, false);
  }

  public UsagePreviewPanel(@NotNull Project project,
                           @NotNull UsageViewPresentation presentation,
                           boolean isEditor) {
    super(project, presentation);
    myIsEditor = isEditor;
  }

  public static class Provider implements UsageContextPanel.Provider {
    @NotNull
    @Override
    public UsageContextPanel create(@NotNull UsageView usageView) {
      return new UsagePreviewPanel(((UsageViewImpl)usageView).getProject(), usageView.getPresentation(), true) {
        @Override
        protected void customizeEditorSettings(EditorSettings settings) {
          super.customizeEditorSettings(settings);
          settings.setUseSoftWraps(true);
        }
      };
    }

    @Override
    public boolean isAvailableFor(@NotNull UsageView usageView) {
      return true;
    }
    @NotNull
    @Override
    public String getTabTitle() {
      return "Preview";
    }
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

    highlight(infos, myEditor, myProject);
  }

  private static final Key<Boolean> IN_PREVIEW_USAGE_FLAG = Key.create("IN_PREVIEW_USAGE_FLAG");

  public static void highlight(@NotNull final List<UsageInfo> infos, @NotNull final Editor editor, @NotNull final Project project) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        if (project.isDisposed()) return;
        if (!editor.isDisposed()) {
          doHighlight(infos, editor, project);
        }
      }
    }, ModalityState.current());
  }

  private static void doHighlight(@NotNull List<UsageInfo> infos, @NotNull Editor editor, @NotNull Project project) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    MarkupModel markupModel = editor.getMarkupModel();
    for (RangeHighlighter highlighter : markupModel.getAllHighlighters()) {
      if (highlighter.getUserData(IN_PREVIEW_USAGE_FLAG) != null) {
        highlighter.dispose();
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
      TextRange infoRange = info.getRangeInElement();
      TextRange textRange = infoRange == null 
                            || infoRange.getStartOffset() > elementRange.getLength() 
                            || infoRange.getEndOffset() > elementRange.getLength() ? null 
                                                                                   : elementRange.cutOut(infoRange);
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
      textRange = InjectedLanguageManager.getInstance(project).injectedToHost(psiElement, textRange);

      RangeHighlighter highlighter = markupModel.addRangeHighlighter(textRange.getStartOffset(), textRange.getEndOffset(),
                                                                                   HighlighterLayer.ADDITIONAL_SYNTAX, attributes,
                                                                                   HighlighterTargetArea.EXACT_RANGE);
      highlighter.putUserData(IN_PREVIEW_USAGE_FLAG, Boolean.TRUE);
      editor.getCaretModel().moveToOffset(textRange.getEndOffset());
    }
    editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
  }

  private static final Key<UsagePreviewPanel> PREVIEW_EDITOR_FLAG = Key.create("PREVIEW_EDITOR_FLAG");
  private Editor createEditor(final PsiFile psiFile, Document document) {
    if (isDisposed) return null;
    Project project = psiFile.getProject();

    Editor editor = EditorFactory.getInstance().createEditor(document, project, psiFile.getVirtualFile(), !myIsEditor);

    EditorSettings settings = editor.getSettings();
    customizeEditorSettings(settings);

    editor.putUserData(PREVIEW_EDITOR_FLAG, this);
    return editor;
  }

  protected void customizeEditorSettings(EditorSettings settings) {
    settings.setLineMarkerAreaShown(false);
    settings.setIndentGuidesShown(false);
    settings.setFoldingOutlineShown(false);
    settings.setAdditionalColumnsCount(0);
    settings.setAdditionalLinesCount(0);
    settings.setVirtualSpace(true);
  }

  @Override
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

  @Override
  public void updateLayoutLater(@Nullable final List<UsageInfo> infos) {
    if (infos == null) {
      releaseEditor();
      removeAll();
      JComponent titleComp = new JLabel(UsageViewBundle.message("select.the.usage.to.preview", myPresentation.getUsagesWord()), SwingConstants.CENTER);
      add(titleComp, BorderLayout.CENTER);
      revalidate();
    }
    else {
      resetEditor(infos);
    }
  }
}
