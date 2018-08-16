// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.template;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.impl.TemplateContext;
import com.intellij.codeInsight.template.impl.TemplateOptionalProcessor;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiUtilBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

/**
 * @author Maxim.Medvedev
 */
public class GroovyShortenFQNamesProcessor implements TemplateOptionalProcessor, DumbAware {

  @Override
  public void processText(final Project project,
                          final Template template,
                          final Document document,
                          final RangeMarker templateRange,
                          final Editor editor) {
    if (!template.isToShortenLongNames()) return;

    PsiDocumentManager.getInstance(project).commitDocument(document);
    PsiFile file = editor instanceof EditorWindow ? ((EditorWindow)editor).getInjectedFile()
                                                  : PsiUtilBase.getPsiFileInEditor(editor, project);
    if (file instanceof GroovyFile) {
      DumbService.getInstance(project).withAlternativeResolveEnabled(() -> JavaCodeStyleManager.getInstance(project)
        .shortenClassReferences(file, templateRange.getStartOffset(), templateRange.getEndOffset()));
    }
    PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document);
  }

  @Override
  public String getOptionName() {
    return CodeInsightBundle.message("dialog.edit.template.checkbox.shorten.fq.names");
  }

  @Override
  public boolean isEnabled(final Template template) {
    return template.isToShortenLongNames();
  }

  @Override
  public boolean isVisible(@NotNull Template template, @NotNull TemplateContext context) {
    return false;
  }
}
