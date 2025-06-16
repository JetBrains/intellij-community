// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.overrideImplement;

import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.java.JavaBundle;
import com.intellij.lang.LanguageCodeInsightActionHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.util.GroovyOverrideImplementExploreUtil;
import org.jetbrains.plugins.groovy.util.GroovyOverrideImplementUtil;

public final class GroovyOverrideMethodsHandler implements LanguageCodeInsightActionHandler {
  @Override
  public boolean isValidFor(Editor editor, PsiFile psiFile) {
    return psiFile != null && GroovyFileType.GROOVY_FILE_TYPE.equals(psiFile.getFileType());
  }

  @Override
  public void invoke(final @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    if (!EditorModificationUtil.checkModificationAllowed(editor)) return;
    PsiClass aClass = OverrideImplementUtil.getContextClass(project, editor, psiFile, true);
    if (aClass instanceof GrTypeDefinition typeDefinition) {

      if (GroovyOverrideImplementExploreUtil.getMethodSignaturesToOverride(typeDefinition).isEmpty()) {
        HintManager.getInstance().showErrorHint(editor, JavaBundle.message("override.methods.error.no.methods"));
        return;
      }

      GroovyOverrideImplementUtil.chooseAndOverrideMethods(project, editor, typeDefinition);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
