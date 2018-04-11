// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.intentions.base.IntentionUtils;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.GroovyExpectedTypesProvider;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.SupertypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.TypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStaticChecker;
import org.jetbrains.plugins.groovy.lang.psi.util.GrTraitUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.template.expressions.ChooseTypeExpression;

/**
 * @author ven
 */
public class CreateMethodFromUsageFix extends GrCreateFromUsageBaseFix implements IntentionAction {

  public CreateMethodFromUsageFix(@NotNull GrReferenceExpression refExpression) {
    super(refExpression);
  }

  @Override
  @NotNull
  public String getText() {
    return GroovyBundle.message("create.method.from.usage", getMethodName());
  }

  @Override
  protected void invokeImpl(Project project, @NotNull PsiClass targetClass) {
    final JVMElementFactory factory = JVMElementFactories.getFactory(targetClass.getLanguage(), targetClass.getProject());
    assert factory != null;
    PsiMethod method = factory.createMethod(getMethodName(), PsiType.VOID);

    final GrReferenceExpression ref = getRefExpr();
    if (GrStaticChecker.isInStaticContext(ref, targetClass)) {
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
    IntentionUtils.createTemplateForMethod(paramTypesExpressions, method, targetClass, constraints, false, context);
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
    return aClass.isInterface() && !GrTraitUtil.isTrait(aClass);
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
      boolean isGroovy = method.getLanguage() == GroovyLanguage.INSTANCE;
      paramTypesExpressions[i] = new ChooseTypeExpression(constraints, method.getManager(), method.getResolveScope(), isGroovy);
    }
    return paramTypesExpressions;
  }
}
