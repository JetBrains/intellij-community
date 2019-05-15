// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.psiutils;

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
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

public class HighlightUtils {

  private HighlightUtils() {
  }

  public static void highlightElement(@NotNull PsiElement element) {
    highlightElements(Collections.singleton(element));
  }

  public static void highlightElement(@NotNull PsiElement element, String statusBarText) {
    highlightElements(Collections.singleton(element), statusBarText);
  }

  public static void highlightElements(@NotNull final Collection<? extends PsiElement> elementCollection) {
    highlightElements(elementCollection, InspectionGadgetsBundle.message("press.escape.to.remove.highlighting.message"));
  }

  public static void highlightElements(@NotNull final Collection<? extends PsiElement> elementCollection, String statusBarText) {
    if (elementCollection.isEmpty()) {
      return;
    }
    if (elementCollection.contains(null)) {
      throw new IllegalArgumentException("Nulls passed in collection: " + elementCollection);
    }
    final Application application = ApplicationManager.getApplication();
    application.invokeLater(() -> {
      final PsiElement[] elements = PsiUtilCore.toPsiElementArray(elementCollection);
      if (ContainerUtil.exists(elements, element -> !element.isValid())) {
        return;
      }
      final PsiElement firstElement = elements[0];
      final Project project = firstElement.getProject();
      if (project.isDisposed()) return;
      final Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
      if (editor == null) {
        return;
      }
      final EditorColorsScheme globalScheme = EditorColorsManager.getInstance().getGlobalScheme();
      final TextAttributes textattributes = globalScheme.getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
      HighlightManager.getInstance(project).addOccurrenceHighlights(editor, elements, textattributes, true, null);
      WindowManager.getInstance().getStatusBar(project).setInfo(statusBarText);
      final FindManager findmanager = FindManager.getInstance(project);
      FindModel findmodel = findmanager.getFindNextModel();
      if (findmodel == null) {
        findmodel = findmanager.getFindInFileModel();
      }
      findmodel.setSearchHighlighters(true);
      findmanager.setFindWasPerformed();
      findmanager.setFindNextModel(findmodel);
    });
  }

  public static void showRenameTemplate(PsiElement context,
                                        PsiNameIdentifierOwner element,
                                        PsiReference... references) {
    context = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(
      context);
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
      builder.replaceElement(reference, "PATTERN", "PATTERN",
                             false);
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