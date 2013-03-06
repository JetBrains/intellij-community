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
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
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
public class CreateMethodFromUsageFix implements IntentionAction {
  protected final PsiClass myTargetClass;
  protected final GrReferenceExpression myRefExpression;

  public CreateMethodFromUsageFix(@NotNull GrReferenceExpression refExpression, @NotNull PsiClass targetClass) {
    myRefExpression = refExpression;
    myTargetClass = targetClass;
  }

  @NotNull
  public String getText() {
    return GroovyBundle.message("create.method.from.usage", getMethodName());
  }

  @NotNull
  public String getFamilyName() {
    return GroovyBundle.message("create.from.usage.family.name");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myTargetClass.isValid() && myRefExpression.isValid() && myTargetClass.isWritable();
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final JVMElementFactory factory = JVMElementFactories.getFactory(myTargetClass.getLanguage(), project);
    assert factory != null;
    PsiMethod method = factory.createMethod(getMethodName(), PsiType.VOID);
    if (StaticChecker.isInStaticContext(myRefExpression, myTargetClass)) {
      method.getModifierList().setModifierProperty(PsiModifier.STATIC, true);
    }

    PsiType[] argTypes = getArgumentTypes();
    assert argTypes != null;

    ChooseTypeExpression[] paramTypesExpressions = setupParams(method, argTypes, factory);

    TypeConstraint[] constraints = getReturnTypeConstraints();

    final PsiGenerationInfo<PsiMethod> info = OverrideImplementUtil.createGenerationInfo(method);
    info.insert(myTargetClass, findInsertionAnchor(info), false);
    method = info.getPsiMember();

    if (shouldBeAbstract(myTargetClass)) {
      method.getBody().delete();
      if (!myTargetClass.isInterface()) {
        method.getModifierList().setModifierProperty(PsiModifier.ABSTRACT, true);
      }
    }

    final PsiElement context = PsiTreeUtil.getParentOfType(myRefExpression, PsiClass.class, PsiMethod.class, PsiFile.class);
    IntentionUtils.createTemplateForMethod(argTypes, paramTypesExpressions, method, myTargetClass, constraints, false, context);
  }

  @NotNull
  protected TypeConstraint[] getReturnTypeConstraints() {
    return GroovyExpectedTypesProvider.calculateTypeConstraints((GrExpression)myRefExpression.getParent());
  }

  protected PsiType[] getArgumentTypes() {
    return PsiUtil.getArgumentTypes(myRefExpression, false);
  }

  @NotNull
  protected String getMethodName() {
    return myRefExpression.getReferenceName();
  }

  protected boolean shouldBeAbstract(PsiClass aClass) {
    return aClass.isInterface();
  }

  @Nullable
  private PsiElement findInsertionAnchor(PsiGenerationInfo<PsiMethod> info) {
    PsiElement parent = myTargetClass instanceof GroovyScriptClass ? ((GroovyScriptClass)myTargetClass).getContainingFile() : myTargetClass;
    if (PsiTreeUtil.isAncestor(parent, myRefExpression, false)) {
      return info.findInsertionAnchor(myTargetClass, myRefExpression);
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
      if (argType == null || argType == PsiType.NULL) argType = TypesUtil.getJavaLangObject(myRefExpression);
      final PsiParameter p = factory.createParameter("o", argType);
      parameterList.add(p);
      TypeConstraint[] constraints = {SupertypeConstraint.create(argType)};
      boolean isGroovy = method.getLanguage() == GroovyFileType.GROOVY_LANGUAGE;
      paramTypesExpressions[i] = new ChooseTypeExpression(constraints, myRefExpression.getManager(), isGroovy, method.getResolveScope());
    }
    return paramTypesExpressions;
  }

  public boolean startInWriteAction() {
    return true;
  }
}
