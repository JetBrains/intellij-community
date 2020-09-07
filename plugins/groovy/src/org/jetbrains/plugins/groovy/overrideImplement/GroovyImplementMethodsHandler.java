// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

public class GroovyImplementMethodsHandler implements LanguageCodeInsightActionHandler {
  @Override
  public boolean isValidFor(Editor editor, PsiFile psiFile) {
    return psiFile != null && GroovyFileType.GROOVY_FILE_TYPE.equals(psiFile.getFileType());
  }

  @Override
  public void invoke(@NotNull final Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    if (!EditorModificationUtil.checkModificationAllowed(editor)) return;
    PsiClass aClass = OverrideImplementUtil.getContextClass(project, editor, file, true);
    if (aClass instanceof GrTypeDefinition) {
      GrTypeDefinition typeDefinition = (GrTypeDefinition)aClass;
      if (GroovyOverrideImplementExploreUtil.getMethodSignaturesToImplement(typeDefinition).isEmpty()) {
        HintManager.getInstance().showErrorHint(editor, JavaBundle.message("implement.method.no.methods.to.implement"));
        return;
      }

      GroovyOverrideImplementUtil.chooseAndImplementMethods(project, editor, typeDefinition);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
