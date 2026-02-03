// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions.conversions;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.intentions.base.GrPsiUpdateIntention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrAssertStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ConvertJunitAssertionToAssertStatementIntention extends GrPsiUpdateIntention implements PsiElementPredicate {
  private static class Holder {
    private static final Pattern PATTERN = Pattern.compile("arg(\\d+)");

    private static final Map<String, String[]> ourStatementMap = Map.of(
      "assertNotNull", new String[]{null, "assert arg0 != null", "assert arg1 != null : arg0"},
      "assertNull", new String[]{null, "assert arg0 == null", "assert arg1 == null : arg0"},

      "assertTrue", new String[]{null, "assert arg0", "assert arg1 : arg0"},
      "assertFalse", new String[]{null, "assert !arg0", "assert !arg1 : arg0"},

      "assertEquals", new String[]{null, null, "assert arg0 == arg1", "assert arg1 == arg2 : arg0"},

      "assertSame", new String[]{null, null, "assert arg0.is(arg1)", "assert arg1.is(arg2) : arg0"},
      "assertNotSame", new String[]{null, null, "assert !arg0.is(arg1)", "assert !arg1.is(arg2) : arg0"});
  }

  private static @Nullable String getReplacementStatement(@NotNull PsiMethod method, @NotNull GrMethodCall methodCall) {
    PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) return null;

    String qualifiedName = containingClass.getQualifiedName();
    if (!"junit.framework.Assert".equals(qualifiedName) && !"groovy.util.GroovyTestCase".equals(qualifiedName)) return null;

    String[] replacementStatements = Holder.ourStatementMap.get(method.getName());
    if (replacementStatements == null) return null;
    
    GrArgumentList argumentList = methodCall.getArgumentList();

    if (argumentList.getNamedArguments().length > 0) return null;

    GrExpression[] arguments = argumentList.getExpressionArguments();

    if (arguments.length >= replacementStatements.length) return null;
    
    return replacementStatements[arguments.length];
  }
  
  private static @Nullable GrStatement getReplacementElement(@NotNull PsiMethod method, @NotNull GrMethodCall methodCall) {
    String replacementStatement = getReplacementStatement(method, methodCall);
    if (replacementStatement == null) return null;
    
    GrExpression[] arguments = methodCall.getArgumentList().getExpressionArguments();
    
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(method.getProject());
    
    GrAssertStatement statement = (GrAssertStatement)factory.createStatementFromText(replacementStatement);
    
    final Map<GrExpression, GrExpression> replaceMap = new HashMap<>();
    
    statement.acceptChildren(new GroovyRecursiveElementVisitor() {
      @Override
      public void visitExpression(@NotNull GrExpression expression) {
        Matcher matcher = Holder.PATTERN.matcher(expression.getText());
        if (matcher.matches()) {
          int index = Integer.parseInt(matcher.group(1));
          replaceMap.put(expression, arguments[index]);
        }
        else {
          super.visitExpression(expression);
        }
      }
    });

    for (Map.Entry<GrExpression, GrExpression> entry : replaceMap.entrySet()) {
      entry.getKey().replaceWithExpression(entry.getValue(), true);
    }

    return statement;
  }

  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull ActionContext context, @NotNull ModPsiUpdater updater) {
    GrMethodCall methodCall = (GrMethodCall)element;

    PsiMethod method = methodCall.resolveMethod();
    if (method == null) return;

    GrStatement replacementElement = getReplacementElement(method, methodCall);
    if (replacementElement == null) return;

    ((GrMethodCall)element).replaceWithStatement(replacementElement);
  }

  @Override
  protected @NotNull PsiElementPredicate getElementPredicate() {
    return this;
  }

  @Override
  public boolean satisfiedBy(@NotNull PsiElement element) {
    if (!(element instanceof GrMethodCall methodCall)) return false;

    PsiMethod method = methodCall.resolveMethod();
    if (method == null) return false;

    return getReplacementStatement(method, methodCall) != null;
  }
}
