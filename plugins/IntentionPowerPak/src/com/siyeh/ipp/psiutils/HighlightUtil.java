/*
 * Copyright 2007-2012 Bas Leijdekkers
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
package com.siyeh.ipp.psiutils;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.MacroCallNode;
import com.intellij.codeInsight.template.macro.SuggestVariableNameMacro;
import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class HighlightUtil {

  private HighlightUtil() {
  }

  public static void highlightElements(
    @NotNull final Collection<? extends PsiElement> elementCollection,
    @NotNull final String statusBarText) {
    if (elementCollection.isEmpty()) {
      return;
    }
    final Application application = ApplicationManager.getApplication();
    application.invokeLater(new Runnable() {
      public void run() {
        final PsiElement[] elements = PsiUtilCore.toPsiElementArray(elementCollection);
        final PsiElement firstElement = elements[0];
        if (!firstElement.isValid()) {
          return;
        }
        final Project project = firstElement.getProject();
        final FileEditorManager editorManager = FileEditorManager.getInstance(project);
        final EditorColorsManager editorColorsManager = EditorColorsManager.getInstance();
        final Editor editor = editorManager.getSelectedTextEditor();
        if (editor == null) {
          return;
        }
        final EditorColorsScheme globalScheme = editorColorsManager.getGlobalScheme();
        final TextAttributes textattributes = globalScheme.getAttributes(
            EditorColors.SEARCH_RESULT_ATTRIBUTES);
        final HighlightManager highlightManager = HighlightManager.getInstance(project);
        highlightManager.addOccurrenceHighlights(editor, elements, textattributes, true, null);
        final FindManager findmanager = FindManager.getInstance(project);
        FindModel findmodel = findmanager.getFindNextModel();
        if (findmodel == null) {
          findmodel = findmanager.getFindInFileModel();
        }
        findmodel.setSearchHighlighters(true);
        findmanager.setFindWasPerformed();
        findmanager.setFindNextModel(findmodel);
        application.invokeLater(new Runnable() {
          public void run() {
            final WindowManager windowManager = WindowManager.getInstance();
            final StatusBar statusBar = windowManager.getStatusBar(project);
            if (statusBar != null) {
              statusBar.setInfo(statusBarText);
            }
          }
        });
      }
    });
  }

  public static void highlightElement(
    @NotNull PsiElement element, @NotNull final String statusBarText) {
    final List<PsiElement> elements = Collections.singletonList(element);
    highlightElements(elements, statusBarText);
  }

  public static String getPresentableText(PsiElement element) {
    return getPresentableText(element, new StringBuilder()).toString();
  }

  private static StringBuilder getPresentableText(PsiElement element, StringBuilder builder) {
    if (element == null) {
      return builder;
    }
    if (element instanceof PsiWhiteSpace) {
      return builder.append(' ');
    }
    final PsiElement[] children = element.getChildren();
    if (children.length != 0) {
      for (PsiElement child : children) {
        getPresentableText(child, builder);
      }
    }
    else {
      builder.append(element.getText());
    }
    return builder;
  }

  public static void showRenameTemplate(PsiElement context, PsiNameIdentifierOwner element) {
    context = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(context);
    final Query<PsiReference> query = ReferencesSearch.search(element, element.getUseScope());
    final Collection<PsiReference> references = query.findAll();
    final Project project = context.getProject();
    final FileEditorManager fileEditorManager =
      FileEditorManager.getInstance(project);
    final Editor editor = fileEditorManager.getSelectedTextEditor();
    if (editor == null) {
      return;
    }
    final TemplateBuilderImpl builder = new TemplateBuilderImpl(context);
    final Expression macroCallNode = new MacroCallNode(
      new SuggestVariableNameMacro());
    final PsiElement identifier = element.getNameIdentifier();
    builder.replaceElement(identifier, "PATTERN", macroCallNode, true);
    for (PsiReference reference : references) {
      builder.replaceElement(reference, "PATTERN", "PATTERN", false);
    }
    final Template template = builder.buildInlineTemplate();
    final TextRange textRange = context.getTextRange();
    final int startOffset = textRange.getStartOffset();
    editor.getCaretModel().moveToOffset(startOffset);
    final TemplateManager templateManager =
      TemplateManager.getInstance(project);
    templateManager.startTemplate(editor, template);
  }
}