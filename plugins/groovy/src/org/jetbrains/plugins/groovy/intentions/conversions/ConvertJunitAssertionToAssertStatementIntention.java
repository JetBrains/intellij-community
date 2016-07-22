/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.intentions.conversions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
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

/**
 * @author Sergey Evdokimov
 */
public class ConvertJunitAssertionToAssertStatementIntention extends Intention implements PsiElementPredicate {

  private static final Pattern PATTERN = Pattern.compile("arg(\\d+)");
  
  private static final Map<String, String[]> ourStatementMap = new HashMap<>();
  static {
    ourStatementMap.put("assertNotNull", new String[]{null, "assert arg0 != null", "assert arg1 != null : arg0"});
    ourStatementMap.put("assertNull", new String[]{null, "assert arg0 == null", "assert arg1 == null : arg0"});

    ourStatementMap.put("assertTrue", new String[]{null, "assert arg0", "assert arg1 : arg0"});
    ourStatementMap.put("assertFalse", new String[]{null, "assert !arg0", "assert !arg1 : arg0"});

    ourStatementMap.put("assertEquals", new String[]{null, null, "assert arg0 == arg1", "assert arg1 == arg2 : arg0"});

    ourStatementMap.put("assertSame", new String[]{null, null, "assert arg0.is(arg1)", "assert arg1.is(arg2) : arg0"});
    ourStatementMap.put("assertNotSame", new String[]{null, null, "assert !arg0.is(arg1)", "assert !arg1.is(arg2) : arg0"});
  }
  
  @Nullable
  private static String getReplacementStatement(@NotNull PsiMethod method, @NotNull GrMethodCall methodCall) {
    PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) return null;

    String qualifiedName = containingClass.getQualifiedName();
    if (!"junit.framework.Assert".equals(qualifiedName) && !"groovy.util.GroovyTestCase".equals(qualifiedName)) return null;

    String[] replacementStatements = ourStatementMap.get(method.getName());
    if (replacementStatements == null) return null;
    
    GrArgumentList argumentList = methodCall.getArgumentList();

    if (argumentList.getNamedArguments().length > 0) return null;

    GrExpression[] arguments = argumentList.getExpressionArguments();

    if (arguments.length >= replacementStatements.length) return null;
    
    return replacementStatements[arguments.length];
  }
  
  @Nullable
  private static GrStatement getReplacementElement(@NotNull PsiMethod method, @NotNull GrMethodCall methodCall) {
    String replacementStatement = getReplacementStatement(method, methodCall);
    if (replacementStatement == null) return null;
    
    GrExpression[] arguments = methodCall.getArgumentList().getExpressionArguments();
    
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(method.getProject());
    
    GrAssertStatement statement = (GrAssertStatement)factory.createStatementFromText(replacementStatement);
    
    final Map<GrExpression, GrExpression> replaceMap = new HashMap<>();
    
    statement.acceptChildren(new GroovyRecursiveElementVisitor() {
      @Override
      public void visitExpression(GrExpression expression) {
        Matcher matcher = PATTERN.matcher(expression.getText());
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
  protected void processIntention(@NotNull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
    GrMethodCall methodCall = (GrMethodCall)element;

    PsiMethod method = methodCall.resolveMethod();
    if (method == null) return;

    GrStatement replacementElement = getReplacementElement(method, methodCall);
    if (replacementElement == null) return;

    ((GrMethodCall)element).replaceWithStatement(replacementElement);
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return this;
  }

  @Override
  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof GrMethodCall)) return false;
    
    GrMethodCall methodCall = (GrMethodCall)element;

    PsiMethod method = methodCall.resolveMethod();
    if (method == null) return false;

    return getReplacementStatement(method, methodCall) != null;
  }
}
