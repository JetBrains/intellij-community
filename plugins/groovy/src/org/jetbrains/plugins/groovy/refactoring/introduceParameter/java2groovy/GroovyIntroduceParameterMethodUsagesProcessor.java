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

package org.jetbrains.plugins.groovy.refactoring.introduceParameter.java2groovy;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.lang.Language;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.introduceParameter.IntroduceParameterData;
import com.intellij.refactoring.introduceParameter.IntroduceParameterMethodUsagesProcessor;
import com.intellij.util.VisibilityUtil;
import com.intellij.refactoring.util.usageInfo.DefaultConstructorImplicitUsageInfo;
import com.intellij.refactoring.util.javadoc.MethodJavaDocHelper;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.MultiMap;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntProcedure;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

import java.util.*;

/**
 * @author Maxim.Medvedev
 *         Date: Apr 18, 2009 3:16:24 PM
 */
public class GroovyIntroduceParameterMethodUsagesProcessor implements IntroduceParameterMethodUsagesProcessor {
  private static final Language myLanguage = GroovyFileType.GROOVY_LANGUAGE;

  private static boolean isGroovyUsage(UsageInfo usage) {
    final PsiElement el = usage.getElement();
    return el != null && el.getLanguage().is(myLanguage);
  }

  public boolean isMethodUsage(UsageInfo usage) {
    return GroovyRefactoringUtil.isMethodUsage(usage.getElement()) && isGroovyUsage(usage);
  }

  public void findConflicts(IntroduceParameterData data, UsageInfo[] usages, MultiMap<PsiElement, String> conflicts) {
    Set<UsageInfo> groovyUsages = new HashSet<UsageInfo>();
    for (UsageInfo usage : usages) {
      if (isMethodUsage(usage)) groovyUsages.add(usage);
    }
    if (groovyUsages.size() == 0) return;
    data.getParameterInitializer().accept(new InitializerVisitor(conflicts));
  }

  private static class InitializerVisitor extends JavaRecursiveElementWalkingVisitor {
    private final MultiMap<PsiElement, String> conflicts;

    private InitializerVisitor(MultiMap<PsiElement, String> conflicts) {
      this.conflicts = conflicts;
    }

    @Override
    public void visitNewExpression(PsiNewExpression expression) {
      super.visitNewExpression(expression);
      final PsiExpression qualifier = expression.getQualifier();
      if (qualifier != null) {
        conflicts.putValue(qualifier, GroovyRefactoringBundle.message("groovy.does.not.support.inner.classes.but.it.is.used.in.parameter.initializer"));
      }
      final PsiAnonymousClass anonymousClass = expression.getAnonymousClass();
      if (anonymousClass != null) {
        conflicts.putValue(anonymousClass, GroovyRefactoringBundle.message("groovy.does.not.support.anonymous.classes.but.it.is.used.in.parameter.initializer"));
      }
    }
  }

  public boolean processChangeMethodUsage(IntroduceParameterData data, UsageInfo usage, UsageInfo[] usages) throws IncorrectOperationException {
    if (!isMethodUsage(usage)) return true;
    GrCall callExpression = GroovyRefactoringUtil.getCallExpressionByMethodReference(usage.getElement());
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
    if (method!=null && isMethodInUsages(method, usages, data)) {
      argList.addAfter(GroovyPsiElementFactory.getInstance(data.getProject()).createExpressionFromText(data.getParameterName()), anchor);
    }
    else {
      ChangeContextUtil.encodeContextInfo(data.getParameterInitializer(), true);
      final GrExpression grInitializer = GroovyRefactoringUtil.convertJavaExpr2GroovyExpr(data.getParameterInitializer());
      GrExpression newArg = (GrExpression)argList.addAfter(grInitializer, anchor);
      new OldReferencesResolver(callExpression, newArg, data.getMethodToReplaceIn(), data.getReplaceFieldsWithGetters(),
                                data.getParameterInitializer()).resolve();
      ChangeContextUtil.clearContextInfo(data.getParameterInitializer());
    }


    removeParametersFromCall(callExpression.getArgumentList(), data.getParametersToRemove());
    return false;
  }

  private static boolean isMethodInUsages(PsiMethod method, UsageInfo[] usages, IntroduceParameterData data) {
    PsiManager manager=PsiManager.getInstance(data.getProject());
    for (UsageInfo info : usages) {
      if (!(info instanceof DefaultConstructorImplicitUsageInfo) && manager.areElementsEquivalent(info.getElement(), method)) return true;
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

  private static void removeParametersFromCall(final GrArgumentList argList, TIntArrayList parametersToRemove) {
    final GrExpression[] exprs = argList.getExpressionArguments();
    parametersToRemove.forEachDescending(new TIntProcedure() {
      public boolean execute(final int paramNum) {
        try {

          exprs[paramNum].delete();
        }
        catch (IncorrectOperationException e) {

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
    PsiManager manager = method.getManager();
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
//          LOG.error(e);
        }
        return true;
      }
    });

    PsiParameter parameter = factory.createParameter(data.getParameterName(), data.getForcedType().getCanonicalText(), method);
    PsiUtil.setModifierProperty(parameter, PsiModifier.FINAL, data.isDeclareFinal());
    final PsiParameter anchorParameter = getAnchorParameter(method);
    final GrParameterList parameterList = method.getParameterList();
    parameter = (PsiParameter)parameterList.addAfter(parameter, anchorParameter);
    JavaCodeStyleManager.getInstance(manager.getProject()).shortenClassReferences(parameter);
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
//      LOG.assertTrue(length > 0);
//      LOG.assertTrue(parameters[length - 1].isVarArgs());
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
    PsiUtil.setModifierProperty(constructor, VisibilityUtil.getVisibilityModifier(aClass.getModifierList()), true);
    processAddSuperCall(data, new UsageInfo(constructor), usages);
//    constructor = (GrMethod)CodeStyleManager.getInstance(data.getProject()).reformat(constructor);
    return false;
  }

  public boolean processAddSuperCall(IntroduceParameterData data, UsageInfo usage, UsageInfo[] usages) throws IncorrectOperationException {
    if (!(usage.getElement() instanceof GrMethod) || !isGroovyUsage(usage)) return true;
    GrMethod constructor = (GrMethod)usage.getElement();

    if (!constructor.isConstructor()) return true;

    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(data.getProject());

    GrExpression superCall = (GrExpression)factory.createStatementFromText("super();");
    superCall = (GrExpression)CodeStyleManager.getInstance(data.getProject()).reformat(superCall);
    GrOpenBlock body = constructor.getBlock();
    final GrStatement[] statements = body.getStatements();
    if (statements.length > 0) {
      superCall = (GrExpression)body.addStatementBefore(superCall, statements[0]);
    }
    else {
      superCall = (GrExpression)body.addStatementBefore(superCall, null);
    }
    processChangeMethodUsage(data, new UsageInfo(((GrMethodCallExpression)superCall).getInvokedExpression()), usages);
    return false;

  }
}
