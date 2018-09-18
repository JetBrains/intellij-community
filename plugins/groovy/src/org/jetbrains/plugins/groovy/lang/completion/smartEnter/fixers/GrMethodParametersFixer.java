// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.completion.smartEnter.fixers;

import com.intellij.lang.SmartEnterProcessorWithFixers;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.completion.smartEnter.GroovySmartEnterProcessor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

public class GrMethodParametersFixer extends SmartEnterProcessorWithFixers.Fixer<GroovySmartEnterProcessor> {
  @Override
  public void apply(@NotNull Editor editor, @NotNull GroovySmartEnterProcessor processor, @NotNull PsiElement psiElement) {
    if (psiElement instanceof GrParameterList && psiElement.getParent() instanceof GrMethod) {
      PsiElement rParenth = ((GrParameterList)psiElement).getRParen();
      // TODO ends with comma
      if (rParenth == null || !")".equals(rParenth.getText())) {
        GrParameterList list = (GrParameterList)psiElement;
        final GrParameter[] params = list.getParameters();
        int offset;
        if (params.length == 0) {
          offset = list.getTextRange().getStartOffset() + 1;
        }
        else {
          offset = params[params.length - 1].getTextRange().getEndOffset();
        }
        editor.getDocument().insertString(offset, ")");
      }
    }
  }
}
