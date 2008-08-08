package org.jetbrains.plugins.groovy.lang.completion.smartEnter.fixers;

import com.intellij.util.IncorrectOperationException;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import org.jetbrains.plugins.groovy.lang.completion.smartEnter.GroovySmartEnterProcessor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

/**
 * User: Dmitry.Krasilschikov
 * Date: 08.08.2008
 */
public class GrMethodParametersFixer implements GrFixer{
  public void apply(Editor editor, GroovySmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (psiElement instanceof GrParameterList && psiElement.getParent() instanceof GrMethod) {
      PsiElement rParenth = psiElement.getNextSibling();
      if (rParenth == null) return;

//      [todo] ends with comma
      if (! ")".equals(rParenth.getText())) {
        int offset;
        GrParameterList list = (GrParameterList) psiElement;
        final GrParameter[] params = list.getParameters();
        if (params == null || params.length == 0) {
          offset = list.getTextRange().getStartOffset() + 1;
        } else {
          offset = params[params.length - 1].getTextRange().getEndOffset();
        }
        editor.getDocument().insertString(offset, ")");
      }
    }
  }
}
