/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package org.jetbrains.plugins.groovy.lang.completion.filters.modifiers;

import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.completion.GroovyCompletionUtil;

/**
 * @author ilyas
 */
public class ModifiersFilter implements ElementFilter {
  public boolean isAcceptable(Object element, PsiElement context) {
    if (asSimpleVariable(context) || asTypedMethod(context) ||
        asVariableInBlock(context)) {
      return true;
    }
    return context.getParent() instanceof GrExpression &&
        context.getParent().getParent() instanceof GroovyFile &&
        GroovyCompletionUtil.isNewStatement(context, false);
  }

  public boolean isClassAcceptable(Class hintClass) {
    return true;
  }

  private static boolean asSimpleVariable(PsiElement context) {
    return context.getParent() instanceof GrTypeDefinitionBody &&
        GroovyCompletionUtil.isNewStatement(context, true);
  }

  private static boolean asVariableInBlock(PsiElement context) {
    if (context.getParent() instanceof  GrReferenceExpression &&
        (context.getParent().getParent() instanceof GrOpenBlock ||
        context.getParent().getParent() instanceof GrClosableBlock) &&
        GroovyCompletionUtil.isNewStatement(context, true)) {
      return true;
    }

    if (context.getParent() instanceof  GrReferenceExpression &&
        context.getParent().getParent() instanceof GrApplicationExpression &&
        (context.getParent().getParent().getParent() instanceof GrOpenBlock ||
        context.getParent().getParent().getParent() instanceof GrClosableBlock) &&
        GroovyCompletionUtil.isNewStatement(context, true)) {
      return true;
    }

    return context.getParent() instanceof GrTypeDefinitionBody;
  }

  private static boolean asTypedMethod(PsiElement context) {
    return context.getParent() instanceof GrReferenceElement &&
        context.getParent().getParent() instanceof GrTypeElement &&
        context.getParent().getParent().getParent() instanceof GrMethod &&
        context.getParent().getParent().getParent().getParent() instanceof GrTypeDefinitionBody &&
        context.getTextOffset() == context.getParent().getParent().getParent().getParent().getTextOffset();

  }

  @NonNls
  public String toString() {
    return "First filter for modifier keywords";
  }

}