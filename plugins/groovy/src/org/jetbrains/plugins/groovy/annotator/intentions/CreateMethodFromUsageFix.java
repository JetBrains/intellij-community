/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.generation.PsiGenerationInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.intentions.base.IntentionUtils;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.GroovyExpectedTypesProvider;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.SupertypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.TypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.StaticChecker;
import org.jetbrains.plugins.groovy.template.expressions.ChooseTypeExpression;

/**
 * @author ven
 */
public class CreateMethodFromUsageFix extends GrCreateFromUsageBaseFix implements IntentionAction {

  public CreateMethodFromUsageFix(@NotNull GrReferenceExpression refExpression) {
    super(refExpression);
  }

  @NotNull
  public String getText() {
    return GroovyBundle.message("create.method.from.usage", getMethodName());
  }

  protected void invokeImpl(Project project, @NotNull PsiClass targetClass) {
    final JVMElementFactory factory = JVMElementFactories.getFactory(targetClass.getLanguage(), targetClass.getProject());
    assert factory != null;
    PsiMethod method = factory.createMethod(getMethodName(), PsiType.VOID);

    final GrReferenceExpression ref = getRefExpr();
    if (StaticChecker.isInStaticContext(ref, targetClass)) {
      method.getModifierList().setModifierProperty(PsiModifier.STATIC, true);
    }

    PsiType[] argTypes = getArgumentTypes();
    assert argTypes != null;

    ChooseTypeExpression[] paramTypesExpressions = setupParams(method, argTypes, factory);

    TypeConstraint[] constraints = getReturnTypeConstraints();

    final PsiGenerationInfo<PsiMethod> info = OverrideImplementUtil.createGenerationInfo(method);
    info.insert(targetClass, findInsertionAnchor(info, targetClass), false);
    method = info.getPsiMember();

    if (shouldBeAbstract(targetClass)) {
      method.getBody().delete();
      if (!targetClass.isInterface()) {
        method.getModifierList().setModifierProperty(PsiModifier.ABSTRACT, true);
      }
    }

    final PsiElement context = PsiTreeUtil.getParentOfType(ref, PsiClass.class, PsiMethod.class, PsiFile.class);
    IntentionUtils.createTemplateForMethod(argTypes, paramTypesExpressions, method, targetClass, constraints, false, context);
  }

  @NotNull
  protected TypeConstraint[] getReturnTypeConstraints() {
    return GroovyExpectedTypesProvider.calculateTypeConstraints((GrExpression)getRefExpr().getParent());
  }

  protected PsiType[] getArgumentTypes() {
    return PsiUtil.getArgumentTypes(getRefExpr(), false);
  }

  @NotNull
  protected String getMethodName() {
    return getRefExpr().getReferenceName();
  }

  protected boolean shouldBeAbstract(PsiClass aClass) {
    return aClass.isInterface();
  }

  @Nullable
  private PsiElement findInsertionAnchor(PsiGenerationInfo<PsiMethod> info,
                                         PsiClass targetClass) {
    PsiElement parent = targetClass instanceof GroovyScriptClass ? ((GroovyScriptClass)targetClass).getContainingFile() : targetClass;
    if (PsiTreeUtil.isAncestor(parent, getRefExpr(), false)) {
      return info.findInsertionAnchor(targetClass, getRefExpr());
    }
    else {
      return null;
    }
  }

  @NotNull
  private ChooseTypeExpression[] setupParams(@NotNull PsiMethod method, @NotNull PsiType[] argTypes, @NotNull JVMElementFactory factory) {
    final PsiParameterList parameterList = method.getParameterList();

    ChooseTypeExpression[] paramTypesExpressions = new ChooseTypeExpression[argTypes.length];
    for (int i = 0; i < argTypes.length; i++) {
      PsiType argType = TypesUtil.unboxPrimitiveTypeWrapper(argTypes[i]);
      if (argType == null || argType == PsiType.NULL) argType = TypesUtil.getJavaLangObject(getRefExpr());
      final PsiParameter p = factory.createParameter("o", argType);
      parameterList.add(p);
      TypeConstraint[] constraints = {SupertypeConstraint.create(argType)};
      boolean isGroovy = method.getLanguage() == GroovyFileType.GROOVY_LANGUAGE;
      paramTypesExpressions[i] = new ChooseTypeExpression(constraints, method.getManager(), method.getResolveScope(), isGroovy);
    }
    return paramTypesExpressions;
  }
}
