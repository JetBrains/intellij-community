package org.jetbrains.plugins.groovy.lang.completion.getters;

import com.intellij.codeInsight.completion.CompletionContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.filters.ContextGetter;
import com.intellij.util.ArrayUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;

/**
 * @author ven
 */
public class SuggestedVariableNamesGetter implements ContextGetter {
  public Object[] get(PsiElement context, CompletionContext completionContext) {
    //complete variable names
    if (context != null) {
      final PsiElement parent = context.getParent();
      if (parent instanceof GrVariable) {
        final GrVariable variable = (GrVariable) parent;
        if (context.equals(variable.getNameIdentifierGroovy())) {
          final PsiType type = variable.getTypeGroovy();
          if (type != null) {
            final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(context.getProject());
            VariableKind kind = variable instanceof GrParameter ? VariableKind.PARAMETER :
                                variable instanceof GrField ? VariableKind.FIELD : VariableKind.LOCAL_VARIABLE;
            SuggestedNameInfo suggestedNameInfo = codeStyleManager.suggestVariableName(kind, null, null, type);
            return suggestedNameInfo.names;
          }
        }
      }
    }

    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }
}
