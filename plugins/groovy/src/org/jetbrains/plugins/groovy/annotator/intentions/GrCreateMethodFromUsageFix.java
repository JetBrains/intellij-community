// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.generation.PsiGenerationInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyFileType;
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

import java.util.List;

public class GrCreateMethodFromUsageFix extends GrCreateFromUsageBaseFix implements IntentionAction {

  public GrCreateMethodFromUsageFix(@NotNull GrReferenceExpression refExpression) {
    super(refExpression);
  }

  @Override
  public @NotNull String getFamilyName() {
    return GroovyBundle.message("create.method.from.usage.family.name");
  }

  @Override
  public @NotNull String getText() {
    return GroovyBundle.message("create.method.from.usage", getMethodName());
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    final List<PsiClass> classes = getTargetClasses();
    if (classes.isEmpty()) {
      return IntentionPreviewInfo.EMPTY;
    }
    Data data = generateMethod(classes.get(0), true);
    return new IntentionPreviewInfo.CustomDiff(GroovyFileType.GROOVY_FILE_TYPE, "", data.method.getText());
  }

  @Override
  protected final void invokeImpl(Project project, @NotNull PsiClass targetClass) {
    Data data = generateMethod(targetClass, false);

    final PsiElement context = PsiTreeUtil.getParentOfType(getRefExpr(), PsiClass.class, PsiMethod.class, PsiFile.class);
    IntentionUtils.createTemplateForMethod(data.paramTypesExpressions, data.method, targetClass, data.constraints, false, context);
  }

  private static class Data {
    ChooseTypeExpression[] paramTypesExpressions;
    PsiMethod method;
    TypeConstraint[] constraints;

    private Data(ChooseTypeExpression[] paramTypesExpression, PsiMethod method, TypeConstraint[] constraints) {
      this.paramTypesExpressions = paramTypesExpression;
      this.method = method;
      this.constraints = constraints;
    }
  }

  private Data generateMethod(@NotNull PsiClass targetClass, boolean readOnly) {
    final JVMElementFactory factory = JVMElementFactories.getFactory(targetClass.getLanguage(), targetClass.getProject());
    assert factory != null;
    PsiMethod method = factory.createMethod(getMethodName(), PsiTypes.voidType());

    final GrReferenceExpression ref = getRefExpr();
    if (GrStaticChecker.isInStaticContext(ref, targetClass)) {
      method.getModifierList().setModifierProperty(PsiModifier.STATIC, true);
    }

    PsiType[] argTypes = getArgumentTypes();
    assert argTypes != null;

    ChooseTypeExpression[] paramTypesExpressions = setupParams(method, argTypes, factory);

    TypeConstraint[] constraints = getReturnTypeConstraints();

    final PsiGenerationInfo<PsiMethod> info = OverrideImplementUtil.createGenerationInfo(method);
    if (!readOnly) {
      info.insert(targetClass, findInsertionAnchor(info, targetClass), false);
    }
    method = info.getPsiMember();

    if (shouldBeAbstract(targetClass)) {
      method.getBody().delete();
      if (!targetClass.isInterface()) {
        method.getModifierList().setModifierProperty(PsiModifier.ABSTRACT, true);
      }
    }
    return new Data(paramTypesExpressions, method, constraints);
  }

  protected TypeConstraint @NotNull [] getReturnTypeConstraints() {
    return GroovyExpectedTypesProvider.calculateTypeConstraints((GrExpression)getRefExpr().getParent());
  }

  protected PsiType[] getArgumentTypes() {
    return PsiUtil.getArgumentTypes(getRefExpr(), false);
  }

  protected @NotNull String getMethodName() {
    return getRefExpr().getReferenceName();
  }

  protected boolean shouldBeAbstract(PsiClass aClass) {
    return aClass.isInterface() && !GrTraitUtil.isTrait(aClass);
  }

  private @Nullable PsiElement findInsertionAnchor(PsiGenerationInfo<PsiMethod> info,
                                                   PsiClass targetClass) {
    PsiElement parent = targetClass instanceof GroovyScriptClass ? ((GroovyScriptClass)targetClass).getContainingFile() : targetClass;
    if (PsiTreeUtil.isAncestor(parent, getRefExpr(), false)) {
      return info.findInsertionAnchor(targetClass, getRefExpr());
    }
    else {
      return null;
    }
  }

  private ChooseTypeExpression @NotNull [] setupParams(@NotNull PsiMethod method, PsiType @NotNull [] argTypes, @NotNull JVMElementFactory factory) {
    final PsiParameterList parameterList = method.getParameterList();

    ChooseTypeExpression[] paramTypesExpressions = new ChooseTypeExpression[argTypes.length];
    for (int i = 0; i < argTypes.length; i++) {
      PsiType argType = TypesUtil.unboxPrimitiveTypeWrapper(argTypes[i]);
      if (argType == null || argType == PsiTypes.nullType()) argType = TypesUtil.getJavaLangObject(getRefExpr());
      final PsiParameter p = factory.createParameter("o", argType);
      parameterList.add(p);
      TypeConstraint[] constraints = {SupertypeConstraint.create(argType)};
      boolean isGroovy = method.getLanguage() == GroovyLanguage.INSTANCE;
      paramTypesExpressions[i] = new ChooseTypeExpression(constraints, method.getManager(), method.getResolveScope(), isGroovy);
    }
    return paramTypesExpressions;
  }
}
