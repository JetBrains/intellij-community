/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.intentions.style;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.IntentionUtils;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils.*;

/**
 * @author ilyas
 */
public class JavaStylePropertiesInvocationIntention extends Intention {
  @Override
  protected boolean isStopElement(PsiElement element) {
    return super.isStopElement(element) || element instanceof GrClosableBlock;
  }

  protected void processIntention(@NotNull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
    assert element instanceof GrMethodCall;
    GrMethodCall call = ((GrMethodCall)element);
    GrExpression invoked = call.getInvokedExpression();
    if (isGetterInvocation(call) && invoked instanceof GrReferenceExpression) {
      String name = ((GrReferenceExpression)invoked).getName();
      assert name != null;
      name = StringUtil.trimStart(name, GET_PREFIX);
      name = StringUtil.decapitalize(name);
      replaceWithGetter(call, name);
    }
    else if (isSetterInvocation(call) && invoked instanceof GrReferenceExpression) {
      String name = ((GrReferenceExpression)invoked).getName();
      assert name != null;
      name = StringUtil.trimStart(name, SET_PREFIX);
      name = StringUtil.decapitalize(name);
      GrExpression value = call.getExpressionArguments()[0];
      replaceWithSetter(call, name, value);
    }
  }

  private static void replaceWithSetter(GrMethodCall call, String name, GrExpression value) throws IncorrectOperationException {
    GrReferenceExpression refExpr = (GrReferenceExpression) call.getInvokedExpression();
    String oldNameStr = refExpr.getReferenceNameElement().getText();
    String newRefExpr = StringUtil.trimEnd(refExpr.getText(), oldNameStr) + name;
    IntentionUtils.replaceStatement(newRefExpr + " = " + value.getText(), call);
  }

  private static void replaceWithGetter(GrMethodCall call, String name) throws IncorrectOperationException {
    GrReferenceExpression refExpr = (GrReferenceExpression) call.getInvokedExpression();
    String oldNameStr = refExpr.getReferenceNameElement().getText();
    String newRefExpr = StringUtil.trimEnd(refExpr.getText(), oldNameStr) + name;
    IntentionUtils.replaceExpression(newRefExpr, call);
  }

  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new JavaPropertyInvocationPredicate();
  }

  public static boolean isPropertyAccessor(GrMethodCall call) {
    return !isInvokedOnMap(call) && (isGetterInvocation(call) || isSetterInvocation(call));
  }

  private static boolean isInvokedOnMap(GrMethodCall call) {
    GrExpression expr = call.getInvokedExpression();
    return expr instanceof GrReferenceExpression && ResolveUtil.isKeyOfMap((GrReferenceExpression)expr);
  }

  private static boolean isSetterInvocation(GrMethodCall call) {
    GrExpression expr = call.getInvokedExpression();

    if (!(expr instanceof GrReferenceExpression)) return false;

    GrReferenceExpression refExpr = (GrReferenceExpression) expr;
    String name = refExpr.getName();
    if (name == null || !name.startsWith(SET_PREFIX)) return false;

    name = name.substring(SET_PREFIX.length());
    String propName = StringUtil.decapitalize(name);
    if (propName.length() == 0 || name.equals(propName)) return false;

    if (call instanceof GrApplicationStatement) {
      PsiElement element = refExpr.resolve();
      if (!(element instanceof PsiMethod) || !GroovyPropertyUtils.isSimplePropertySetter(((PsiMethod)element))) return false;
    } else {
      PsiMethod method = call.resolveMethod();
      if (!GroovyPropertyUtils.isSimplePropertySetter(method)) return false;
    }

    if (call instanceof GrMethodCallExpression) {
      GrArgumentList args = call.getArgumentList();
      return args != null &&
          args.getExpressionArguments().length == 1 &&
          args.getNamedArguments().length == 0;
    }

    GrArgumentList args = call.getArgumentList();
    return args != null &&
        args.getExpressionArguments().length == 1 &&
        args.getNamedArguments().length == 0;

  }

  private static boolean isGetterInvocation(GrMethodCall call) {
    GrExpression expr = call.getInvokedExpression();
    if (!(expr instanceof GrReferenceExpression)) return false;

    GrReferenceExpression refExpr = (GrReferenceExpression) expr;
    String name = refExpr.getName();
    if (name == null || !name.startsWith(GET_PREFIX)) return false;

    name = name.substring(GET_PREFIX.length());
    String propName = StringUtil.decapitalize(name);
    if (propName.length() == 0 || name.equals(propName)) return false;

    PsiMethod method = call.resolveMethod();
    if (!GroovyPropertyUtils.isSimplePropertyGetter(method)) return false;

    GrArgumentList args = call.getArgumentList();
    return args != null && args.getExpressionArguments().length == 0;
  }

  private static class JavaPropertyInvocationPredicate implements PsiElementPredicate {
    public boolean satisfiedBy(PsiElement element) {
      if (!(element instanceof GrMethodCall)) return false;
      return isPropertyAccessor((GrMethodCall) element);
    }
  }
}
