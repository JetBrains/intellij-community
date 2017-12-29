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

package org.jetbrains.plugins.groovy.refactoring.introduce.parameter.java2groovy;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.ExpressionConverter;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.introduceParameter.IntroduceParameterData;
import com.intellij.refactoring.introduceParameter.IntroduceParameterMethodUsagesProcessor;
import com.intellij.refactoring.introduceParameter.IntroduceParameterUtil;
import com.intellij.refactoring.util.javadoc.MethodJavaDocHelper;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.MultiMap;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrParametersOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;
import org.jetbrains.plugins.groovy.refactoring.introduce.parameter.GroovyIntroduceParameterUtil;

/**
 * @author Maxim.Medvedev
 */
public class GroovyIntroduceParameterMethodUsagesProcessor implements IntroduceParameterMethodUsagesProcessor {
  private static final Logger LOG = Logger.getInstance(GroovyIntroduceParameterMethodUsagesProcessor.class);

  private static boolean isGroovyUsage(UsageInfo usage) {
    final PsiElement el = usage.getElement();
    return el != null && GroovyLanguage.INSTANCE.equals(el.getLanguage());
  }

  @Override
  public boolean isMethodUsage(UsageInfo usage) {
    return GroovyRefactoringUtil.isMethodUsage(usage.getElement()) && isGroovyUsage(usage);
  }

  @Override
  public void findConflicts(IntroduceParameterData data, UsageInfo[] usages, MultiMap<PsiElement, String> conflicts) {
  }

  @Override
  public boolean processChangeMethodUsage(IntroduceParameterData data, UsageInfo usage, UsageInfo[] usages) throws IncorrectOperationException {
    GrCall callExpression = GroovyRefactoringUtil.getCallExpressionByMethodReference(usage.getElement());
    if (callExpression == null) return true;
    GrArgumentList argList = callExpression.getArgumentList();
    GrExpression[] oldArgs = argList.getExpressionArguments();

    final GrExpression anchor;
    if (!data.getMethodToSearchFor().isVarArgs()) {
      anchor = getLast(oldArgs);
    }
    else {
      final PsiParameter[] parameters = data.getMethodToSearchFor().getParameterList().getParameters();
      if (parameters.length > oldArgs.length) {
        anchor = getLast(oldArgs);
      }
      else {
        final int lastNonVararg = parameters.length - 2;
        anchor = lastNonVararg >= 0 ? oldArgs[lastNonVararg] : null;
      }
    }

    PsiMethod method = PsiTreeUtil.getParentOfType(argList, PsiMethod.class);

    GrClosureSignature signature = GrClosureSignatureUtil.createSignature(callExpression);
    if (signature == null) signature = GrClosureSignatureUtil.createSignature(data.getMethodToSearchFor(), PsiSubstitutor.EMPTY);

    final GrClosureSignatureUtil.ArgInfo<PsiElement>[] actualArgs = GrClosureSignatureUtil
      .mapParametersToArguments(signature, callExpression.getNamedArguments(), callExpression.getExpressionArguments(),
                                callExpression.getClosureArguments(), callExpression, false, true);

    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(data.getProject());

    if (method != null && IntroduceParameterUtil.isMethodInUsages(data, method, usages)) {
      argList.addAfter(factory.createExpressionFromText(data.getParameterName()), anchor);
    }
    else {
      final PsiElement _expr = data.getParameterInitializer().getExpression();
      PsiElement initializer = ExpressionConverter.getExpression(_expr, GroovyLanguage.INSTANCE, data.getProject());
      LOG.assertTrue(initializer instanceof GrExpression);

      GrExpression newArg = GroovyIntroduceParameterUtil.addClosureToCall(initializer, argList);
      if (newArg == null) {
        final PsiElement dummy = argList.addAfter(factory.createExpressionFromText("1"), anchor);
        newArg = ((GrExpression)dummy).replaceWithExpression((GrExpression)initializer, true);
      }
      final PsiMethod methodToReplaceIn = data.getMethodToReplaceIn();
      new OldReferencesResolver(callExpression, newArg, methodToReplaceIn, data.getReplaceFieldsWithGetters(), initializer,
                                signature, actualArgs, methodToReplaceIn.getParameterList().getParameters()).resolve();
      ChangeContextUtil.clearContextInfo(initializer);

      //newArg can be replaced by OldReferenceResolver
      if (newArg.isValid()) {
        JavaCodeStyleManager.getInstance(newArg.getProject()).shortenClassReferences(newArg);
        CodeStyleManager.getInstance(data.getProject()).reformat(newArg);
      }
    }

    if (actualArgs == null) {
      removeParamsFromUnresolvedCall(callExpression, data);
    }
    else {
      removeParametersFromCall(actualArgs, data.getParametersToRemove());
    }

    if (argList.getAllArguments().length == 0 && PsiImplUtil.hasClosureArguments(callExpression)) {
      final GrArgumentList emptyArgList = ((GrMethodCallExpression)factory.createExpressionFromText("foo{}")).getArgumentList();
      argList.replace(emptyArgList);
    }
    return false;
  }

  @Nullable
  private static GrExpression getLast(GrExpression[] oldArgs) {
    GrExpression anchor;
    if (oldArgs.length > 0) {
      anchor = oldArgs[oldArgs.length - 1];
    }
    else {
      anchor = null;
    }
    return anchor;
  }

  private static void removeParametersFromCall(final GrClosureSignatureUtil.ArgInfo<PsiElement>[] actualArgs,final TIntArrayList parametersToRemove) {
    parametersToRemove.forEach(paramNum -> {
      try {
        final GrClosureSignatureUtil.ArgInfo<PsiElement> actualArg = actualArgs[paramNum];
        if (actualArg == null) return true;
        for (PsiElement arg : actualArg.args) {
          arg.delete();
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
      return true;
    });
  }

  private static void removeParamsFromUnresolvedCall(GrCall callExpression, IntroduceParameterData data) {
    final GrExpression[] arguments = callExpression.getExpressionArguments();
    final GrClosableBlock[] closureArguments = callExpression.getClosureArguments();
    final GrNamedArgument[] namedArguments = callExpression.getNamedArguments();

    final boolean hasNamedArgs;
    if (namedArguments.length > 0) {
      final PsiMethod method = data.getMethodToSearchFor();
      final PsiParameter[] parameters = method.getParameterList().getParameters();
      if (parameters.length > 0) {
        final PsiType type = parameters[0].getType();
        hasNamedArgs = InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_MAP);
      }
      else {
        hasNamedArgs = false;
      }
    }
    else {
      hasNamedArgs = false;
    }

    data.getParametersToRemove().forEachDescending(paramNum -> {
      try {
        if (paramNum == 0 && hasNamedArgs) {
          for (GrNamedArgument namedArgument : namedArguments) {
            namedArgument.delete();
          }
        }
        else {
          if (hasNamedArgs) paramNum--;
          if (paramNum < arguments.length) {
            arguments[paramNum].delete();
          }
          else if (paramNum < arguments.length + closureArguments.length) {
            closureArguments[paramNum - arguments.length].delete();
          }
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
      return true;
    });
  }

  @Override
  public boolean processChangeMethodSignature(IntroduceParameterData data, UsageInfo usage, UsageInfo[] usages) throws IncorrectOperationException {
    if (!(usage.getElement() instanceof GrMethod) || !isGroovyUsage(usage)) return true;
    GrMethod method = (GrMethod)usage.getElement();

    final FieldConflictsResolver fieldConflictsResolver = new FieldConflictsResolver(data.getParameterName(), method.getBlock());
    final MethodJavaDocHelper javaDocHelper = new MethodJavaDocHelper(method);

    final PsiParameter[] parameters = method.getParameterList().getParameters();
    data.getParametersToRemove().forEachDescending(paramNum -> {
      try {
        PsiParameter param = parameters[paramNum];
        PsiDocTag tag = javaDocHelper.getTagForParameter(param);
        if (tag != null) {
          tag.delete();
        }
        param.delete();
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
      return true;
    });

    addParameter(method, javaDocHelper, data.getForcedType(), data.getParameterName(), data.isDeclareFinal(), data.getProject());

    fieldConflictsResolver.fix();

    return false;

  }

  @NotNull
  public static GrParameter addParameter(@NotNull GrParametersOwner parametersOwner,
                                         @Nullable MethodJavaDocHelper javaDocHelper,
                                         @NotNull PsiType forcedType,
                                         @NotNull String parameterName,
                                         boolean isFinal,
                                         @NotNull Project project) {
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);

    final String typeText =
      forcedType.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) || forcedType == PsiType.NULL || PsiType.VOID.equals(forcedType)
      ? null
      : forcedType.getCanonicalText();

    GrParameter parameter = factory.createParameter(parameterName, typeText, parametersOwner);
    parameter.getModifierList().setModifierProperty(PsiModifier.FINAL, isFinal);
    final PsiParameter anchorParameter = getAnchorParameter(parametersOwner);
    final GrParameterList parameterList = parametersOwner.getParameterList();
    parameter = (GrParameter)parameterList.addAfter(parameter, anchorParameter);

    JavaCodeStyleManager.getInstance(project).shortenClassReferences(parameter);
    if (javaDocHelper != null) {
      final PsiDocTag tagForAnchorParameter = javaDocHelper.getTagForParameter(anchorParameter);
      javaDocHelper.addParameterAfter(parameterName, tagForAnchorParameter);
    }

    return parameter;
  }

  @Nullable
  private static PsiParameter getAnchorParameter(GrParametersOwner parametersOwner) {
    PsiParameterList parameterList = parametersOwner.getParameterList();
    final PsiParameter anchorParameter;
    final PsiParameter[] parameters = parameterList.getParameters();
    final int length = parameters.length;
    if (!parametersOwner.isVarArgs()) {
      anchorParameter = length > 0 ? parameters[length - 1] : null;
    }
    else {
      anchorParameter = length > 1 ? parameters[length - 2] : null;
    }
    return anchorParameter;
  }

  @Override
  public boolean processAddDefaultConstructor(IntroduceParameterData data, UsageInfo usage, UsageInfo[] usages) {
    if (!(usage.getElement() instanceof PsiClass) || !isGroovyUsage(usage)) return true;
    PsiClass aClass = (PsiClass)usage.getElement();
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(data.getProject());
    GrMethod constructor =
      factory.createConstructorFromText(aClass.getName(), ArrayUtil.EMPTY_STRING_ARRAY, ArrayUtil.EMPTY_STRING_ARRAY, "{}");
    constructor = (GrMethod)aClass.add(constructor);
    constructor.getModifierList().setModifierProperty(VisibilityUtil.getVisibilityModifier(aClass.getModifierList()), true);
    processAddSuperCall(data, new UsageInfo(constructor), usages);
    return false;
  }

  @Override
  public boolean processAddSuperCall(IntroduceParameterData data, UsageInfo usage, UsageInfo[] usages) throws IncorrectOperationException {
    if (!(usage.getElement() instanceof GrMethod) || !isGroovyUsage(usage)) return true;
    GrMethod constructor = (GrMethod)usage.getElement();

    if (!constructor.isConstructor()) return true;

    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(data.getProject());

    GrConstructorInvocation superCall = factory.createConstructorInvocation("super();");
    GrOpenBlock body = constructor.getBlock();
    final GrStatement[] statements = body.getStatements();
    if (statements.length > 0) {
      superCall = (GrConstructorInvocation)body.addStatementBefore(superCall, statements[0]);
    }
    else {
      superCall = (GrConstructorInvocation)body.addStatementBefore(superCall, null);
    }
    processChangeMethodUsage(data, new UsageInfo(superCall), usages);
    return false;

  }
}
