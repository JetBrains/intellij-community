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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyNamesUtil;

import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils.*;

/**
 * @author ilyas
 */
public class JavaStylePropertiesInvocationIntention extends Intention {
  private static final Logger LOG = Logger.getInstance(JavaStylePropertiesInvocationIntention.class);

  @Override
  protected boolean isStopElement(PsiElement element) {
    return super.isStopElement(element) || element instanceof GrClosableBlock;
  }

  protected void processIntention(@NotNull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
    assert element instanceof GrMethodCall;
    GrMethodCall call = ((GrMethodCall)element);
    GrExpression invoked = call.getInvokedExpression();
    String accessorName = ((GrReferenceExpression)invoked).getName();
    if (isGetterInvocation(call) && invoked instanceof GrReferenceExpression) {
      final GrExpression newCall = genRefForGetter(call, accessorName);
      call.replaceWithExpression(newCall, true);
    }
    else if (isSetterInvocation(call) && invoked instanceof GrReferenceExpression) {
      final GrStatement newCall = genRefForSetter(call, accessorName);
      call.replaceWithStatement(newCall);
    }
  }

  private static GrAssignmentExpression genRefForSetter(GrMethodCall call, String accessorName) {
    String name = getPropertyNameBySetterName(accessorName);
    GrExpression value = call.getExpressionArguments()[0];
    GrReferenceExpression refExpr = (GrReferenceExpression)call.getInvokedExpression();
    String oldNameStr = refExpr.getReferenceNameElement().getText();
    String newRefExpr = StringUtil.trimEnd(refExpr.getText(), oldNameStr) + name;
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(call.getProject());
    return (GrAssignmentExpression)factory.createStatementFromText(newRefExpr + " = " + value.getText(), call);
  }

  private static GrExpression genRefForGetter(GrMethodCall call, String accessorName) {
    String name = getPropertyNameByGetterName(accessorName, true);
    GrReferenceExpression refExpr = (GrReferenceExpression)call.getInvokedExpression();
    String oldNameStr = refExpr.getReferenceNameElement().getText();
    String newRefExpr = StringUtil.trimEnd(refExpr.getText(), oldNameStr) + name;

    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(call.getProject());
    return factory.createExpressionFromText(newRefExpr, call);
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
    GrReferenceExpression refExpr = (GrReferenceExpression)expr;

    PsiMethod method;
    if (call instanceof GrApplicationStatement) {
      PsiElement element = refExpr.resolve();
      if (!(element instanceof PsiMethod) || !isSimplePropertySetter(((PsiMethod)element))) return false;
      method = (PsiMethod)element;
    }
    else {
      method = call.resolveMethod();
      if (!isSimplePropertySetter(method)) return false;
    }

    if (!GroovyNamesUtil.isValidReference(getPropertyNameByGetterName(method.getName(), true),
                                          ((GrReferenceExpression)expr).getQualifier() != null,
                                          call.getProject())) {
      return false;
    }

    GrArgumentList args = call.getArgumentList();
    if (args == null || args.getExpressionArguments().length != 1 || args.getNamedArguments().length > 0) {
      return false;
    }

    GrAssignmentExpression assignment = genRefForSetter(call, refExpr.getName());
    GrExpression value = assignment.getLValue();
    if (value instanceof GrReferenceExpression &&
        call.getManager().areElementsEquivalent(((GrReferenceExpression)value).resolve(), method)) {
      return true;
    }

    return false;
  }

  private static boolean isGetterInvocation(GrMethodCall call) {
    GrExpression expr = call.getInvokedExpression();
    if (!(expr instanceof GrReferenceExpression)) return false;

    PsiMethod method = call.resolveMethod();
    if (!isSimplePropertyGetter(method)) return false;

    if (!GroovyNamesUtil.isValidReference(getPropertyNameByGetterName(method.getName(), true),
                                          ((GrReferenceExpression)expr).getQualifier() != null,
                                          call.getProject())) {
      return false;
    }

    GrArgumentList args = call.getArgumentList();
    if (args == null || args.getAllArguments().length != 0) {
      return false;
    }

    GrExpression ref = genRefForGetter(call, ((GrReferenceExpression)expr).getName());
    if (ref instanceof GrReferenceExpression) {
      PsiElement resolved = ((GrReferenceExpression)ref).resolve();
      PsiManager manager = call.getManager();
      if (manager.areElementsEquivalent(resolved, method) || areEquivalentAccessors(method, resolved, manager)) {
        return true;
      }
    }

    return false;
  }

  private static boolean areEquivalentAccessors(PsiMethod method, PsiElement resolved, PsiManager manager) {
    if (!(resolved instanceof GrAccessorMethod) || !(method instanceof GrAccessorMethod)) {
      return false;
    }

    if (((GrAccessorMethod)resolved).isSetter() != ((GrAccessorMethod)method).isSetter()) return false;

    GrField p1 = ((GrAccessorMethod)resolved).getProperty();
    GrField p2 = ((GrAccessorMethod)method).getProperty();
    return manager.areElementsEquivalent(p1, p2);
  }

  private static class JavaPropertyInvocationPredicate implements PsiElementPredicate {
    public boolean satisfiedBy(PsiElement element) {
      if (!(element instanceof GrMethodCall)) return false;
      return isPropertyAccessor((GrMethodCall)element);
    }
  }
}
