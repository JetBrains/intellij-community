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

package org.jetbrains.plugins.groovy.refactoring.introduce.parameter.java2groovy;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.introduceParameter.ExpressionConverter;
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
import gnu.trove.TIntProcedure;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.GrReferenceAdjuster;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.impl.types.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

/**
 * @author Maxim.Medvedev
 *         Date: Apr 18, 2009 3:16:24 PM
 */
public class GroovyIntroduceParameterMethodUsagesProcessor implements IntroduceParameterMethodUsagesProcessor {
  private static final Logger LOG = Logger
    .getInstance("#org.jetbrains.plugins.groovy.refactoring.introduce.parameter.java2groovy.GroovyIntroduceParameterMethodUsagesProcessor");

  private static boolean isGroovyUsage(UsageInfo usage) {
    final PsiElement el = usage.getElement();
    return el != null && GroovyFileType.GROOVY_LANGUAGE.equals(el.getLanguage());
  }

  public boolean isMethodUsage(UsageInfo usage) {
    return GroovyRefactoringUtil.isMethodUsage(usage.getElement()) && isGroovyUsage(usage);
  }

  public void findConflicts(IntroduceParameterData data, UsageInfo[] usages, MultiMap<PsiElement, String> conflicts) {
  }

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

    final GrClosureSignatureUtil.ArgInfo<PsiElement>[] actualArgs =
      GrClosureSignatureUtil.mapParametersToArguments(signature, argList, callExpression, callExpression.getClosureArguments(), true);

    if (method != null && IntroduceParameterUtil.isMethodInUsages(data, method, usages)) {
      argList.addAfter(GroovyPsiElementFactory.getInstance(data.getProject()).createExpressionFromText(data.getParameterName()), anchor);
    }
    else {
      PsiElement initializer = ExpressionConverter
        .getExpression(data.getParameterInitializer().getExpression(), GroovyFileType.GROOVY_LANGUAGE, data.getProject());
      LOG.assertTrue(initializer instanceof GrExpression);

      GrExpression newArg = (GrExpression)argList.addAfter(initializer, anchor);
      new OldReferencesResolver(callExpression, newArg, data.getMethodToReplaceIn(), data.getReplaceFieldsWithGetters(), initializer,
                                signature, actualArgs).resolve();
      ChangeContextUtil.clearContextInfo(initializer);
    }

    if (actualArgs == null) {
      removeParamsFromUnresolvedCall(callExpression, data);
    }
    else {
      removeParametersFromCall(actualArgs, data.getParametersToRemove());
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
    parametersToRemove.forEach(new TIntProcedure() {
      public boolean execute(final int paramNum) {
        try {
          final GrClosureSignatureUtil.ArgInfo<PsiElement> actualArg = actualArgs[paramNum];
          for (PsiElement arg : actualArg.args) {
            arg.delete();
          }
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
        return true;
      }
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

    data.getParametersToRemove().forEachDescending(new TIntProcedure() {
      public boolean execute(int paramNum) {
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
      }
    });
  }

  public boolean processChangeMethodSignature(IntroduceParameterData data, UsageInfo usage, UsageInfo[] usages) throws IncorrectOperationException {
    if (!(usage.getElement() instanceof GrMethod) || !isGroovyUsage(usage)) return true;
    GrMethod method = (GrMethod)usage.getElement();

    final FieldConflictsResolver fieldConflictsResolver = new FieldConflictsResolver(data.getParameterName(), method.getBlock());
    final MethodJavaDocHelper javaDocHelper = new MethodJavaDocHelper(method);
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(method.getProject());

    final PsiParameter[] parameters = method.getParameterList().getParameters();
    data.getParametersToRemove().forEachDescending(new TIntProcedure() {
      public boolean execute(final int paramNum) {
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
      }
    });

    final PsiType forcedType = data.getForcedType();
    final String typeText = forcedType.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) ? null : forcedType.getCanonicalText();

    GrParameter parameter = factory.createParameter(data.getParameterName(), typeText, method);
    parameter.getModifierList().setModifierProperty(GrModifier.FINAL, data.isDeclareFinal());
    final PsiParameter anchorParameter = getAnchorParameter(method);
    final GrParameterList parameterList = method.getParameterList();
    parameter = (GrParameter)parameterList.addAfter(parameter, anchorParameter);
    GrReferenceAdjuster.shortenReferences(parameter);
    final PsiDocTag tagForAnchorParameter = javaDocHelper.getTagForParameter(anchorParameter);
    javaDocHelper.addParameterAfter(data.getParameterName(), tagForAnchorParameter);

    fieldConflictsResolver.fix();

    return false;

  }

  @Nullable
  private static PsiParameter getAnchorParameter(PsiMethod methodToReplaceIn) {
    PsiParameterList parameterList = methodToReplaceIn.getParameterList();
    final PsiParameter anchorParameter;
    final PsiParameter[] parameters = parameterList.getParameters();
    final int length = parameters.length;
    if (!methodToReplaceIn.isVarArgs()) {
      anchorParameter = length > 0 ? parameters[length - 1] : null;
    }
    else {
      anchorParameter = length > 1 ? parameters[length - 2] : null;
    }
    return anchorParameter;
  }

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
