/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.introduce.parameter;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.introduceParameter.ExternalUsageInfo;
import com.intellij.refactoring.introduceParameter.IntroduceParameterData;
import com.intellij.refactoring.introduceParameter.IntroduceParameterUtil;
import com.intellij.refactoring.util.ConflictsUtil;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntProcedure;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrParametersOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.refactoring.GrRefactoringError;
import org.jetbrains.plugins.groovy.refactoring.GroovyNameSuggestionUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;
import org.jetbrains.plugins.groovy.refactoring.extract.ExtractUtil;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContext;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContextImpl;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceHandlerBase;
import org.jetbrains.plugins.groovy.refactoring.introduce.StringPartInfo;
import org.jetbrains.plugins.groovy.refactoring.introduce.field.GroovyFieldValidator;

import java.util.*;

/**
 * @author Maxim.Medvedev
 */
public class GroovyIntroduceParameterUtil {
  private static final Logger LOG = Logger.getInstance(GroovyIntroduceParameterUtil.class);

  private GroovyIntroduceParameterUtil() {
  }

  public static PsiField[] findUsedFieldsWithGetters(GrStatement[] statements, PsiClass containingClass) {
    if (containingClass == null) return PsiField.EMPTY_ARRAY;
    final FieldSearcher searcher = new FieldSearcher(containingClass);
    for (GrStatement statement : statements) {
      statement.accept(searcher);
    }
    return searcher.getResult();
  }

  @Nullable
  public static PsiParameter getAnchorParameter(PsiParameterList parameterList, boolean isVarArgs) {
    final PsiParameter[] parameters = parameterList.getParameters();
    final int length = parameters.length;
    if (isVarArgs) {
      return length > 1 ? parameters[length - 2] : null;
    }
    else {
      return length > 0 ? parameters[length - 1] : null;
    }
  }

  public static void removeParametersFromCall(final GrClosureSignatureUtil.ArgInfo<PsiElement>[] actualArgs, final TIntArrayList parametersToRemove) {
    parametersToRemove.forEach(new TIntProcedure() {
      @Override
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

  public static void removeParamsFromUnresolvedCall(GrCall callExpression, PsiParameter[] parameters, TIntArrayList parametersToRemove) {
    final GrExpression[] arguments = callExpression.getExpressionArguments();
    final GrClosableBlock[] closureArguments = callExpression.getClosureArguments();
    final GrNamedArgument[] namedArguments = callExpression.getNamedArguments();

    final boolean hasNamedArgs;
    if (namedArguments.length > 0) {
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

    parametersToRemove.forEachDescending(new TIntProcedure() {
      @Override
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

  public static void detectAccessibilityConflicts(@Nullable GroovyPsiElement elementToProcess,
                                                  final UsageInfo[] usages,
                                                  MultiMap<PsiElement, String> conflicts,
                                                  boolean replaceFieldsWithGetters,
                                                  Project project) {
    if (elementToProcess == null) return;

    final ReferencedElementsCollector collector = new ReferencedElementsCollector();
    elementToProcess.accept(collector);
    final List<PsiElement> result = collector.getResult();
    if (result.isEmpty()) return;

    for (final UsageInfo usageInfo : usages) {
      if (!(usageInfo instanceof ExternalUsageInfo) || !IntroduceParameterUtil.isMethodUsage(usageInfo)) continue;

      final PsiElement place = usageInfo.getElement();
      for (PsiElement element : result) {
        if (element instanceof PsiField && replaceFieldsWithGetters) {
          //check getter access instead
          final PsiClass psiClass = ((PsiField)element).getContainingClass();
          LOG.assertTrue(psiClass != null);
          final PsiMethod method = GroovyPropertyUtils.findGetterForField((PsiField)element);
          if (method != null) {
            element = method;
          }
        }
        if (element instanceof PsiMember &&
            !JavaPsiFacade.getInstance(project).getResolveHelper().isAccessible((PsiMember)element, place, null)) {
          String message = RefactoringBundle.message(
            "0.is.not.accessible.from.1.value.for.introduced.parameter.in.that.method.call.will.be.incorrect",
            RefactoringUIUtil.getDescription(element, true),
            RefactoringUIUtil.getDescription(ConflictsUtil.getContainer(place), true));
          conflicts.putValue(element, message);
        }
      }
    }
  }

  public static void processChangedMethodCall(PsiElement element, GrIntroduceParameterSettings settings, Project project) {
    if (!(element.getParent() instanceof GrMethodCallExpression)) {
      LOG.error(element.getParent());
      return;
    }

    GrMethodCallExpression methodCall = (GrMethodCallExpression)element.getParent();

    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);
    final String name = settings.getName();
    LOG.assertTrue(name != null);
    GrExpression expression = factory.createExpressionFromText(name, null);
    final GrArgumentList argList = methodCall.getArgumentList();
    final PsiElement[] exprs = argList.getAllArguments();

    if (exprs.length > 0) {
      argList.addAfter(expression, exprs[exprs.length - 1]);
    }
    else {
      argList.add(expression);
    }

    removeParametersFromCall(methodCall, settings);
  }

  private static void removeParametersFromCall(GrMethodCallExpression methodCall, GrIntroduceParameterSettings settings) {
    final GroovyResolveResult resolveResult = methodCall.advancedResolve();
    final PsiElement resolved = resolveResult.getElement();
    LOG.assertTrue(resolved instanceof PsiMethod);
    final GrClosureSignature signature = GrClosureSignatureUtil.createSignature((PsiMethod)resolved, resolveResult.getSubstitutor());
    final GrClosureSignatureUtil.ArgInfo<PsiElement>[] argInfos = GrClosureSignatureUtil.mapParametersToArguments(signature, methodCall);
    LOG.assertTrue(argInfos != null);
    settings.parametersToRemove().forEach(new TIntProcedure() {
      @Override
      public boolean execute(int value) {
        final List<PsiElement> args = argInfos[value].args;
        for (PsiElement arg : args) {
          arg.delete();
        }
        return true;
      }
    });
  }

  public static GrMethod generateDelegate(PsiMethod prototype, IntroduceParameterData.ExpressionWrapper initializer, Project project) {
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);

    GrMethod result;
    if (prototype instanceof GrMethod) {
      result = (GrMethod)prototype.copy();
    }
    else {
      StringBuilder builder = new StringBuilder();
      builder.append(prototype.getModifierList().getText()).append(' ');

      if (prototype.getReturnTypeElement() != null  ) {
        builder.append(prototype.getReturnTypeElement().getText());
      }
      builder.append(' ').append(prototype.getName());
      builder.append(prototype.getParameterList().getText());
      builder.append("{}");
      result = factory.createMethodFromText(builder.toString());
    }

    StringBuilder call = new StringBuilder();
    call.append("def foo(){\n");
    final GrParameter[] parameters = result.getParameters();
    call.append(prototype.getName());
    if (initializer.getExpression() instanceof GrClosableBlock) {
      if (parameters.length > 0) {
        call.append('(');
        for (GrParameter parameter : parameters) {
          call.append(parameter.getName()).append(", ");
        }
        call.replace(call.length()-2, call.length(), ")");
      }
      call.append(initializer.getText());
    }
    else {
      call.append('(');
      for (GrParameter parameter : parameters) {
        call.append(parameter.getName()).append(", ");
      }
      call.append(initializer.getText());
      call.append(")");
    }
    call.append("\n}");
    final GrOpenBlock block = factory.createMethodFromText(call.toString()).getBlock();

    result.getBlock().replace(block);
    final PsiElement parent = prototype.getParent();
    final GrMethod method = (GrMethod)parent.addBefore(result, prototype);
    JavaCodeStyleManager.getInstance(method.getProject()).shortenClassReferences(method);
    return method;
  }

  public static TObjectIntHashMap<GrParameter> findParametersToRemove(IntroduceParameterInfo helper) {
    final TObjectIntHashMap<GrParameter> result = new TObjectIntHashMap<>();

    final TextRange range = ExtractUtil.getRangeOfRefactoring(helper);

    GrParameter[] parameters = helper.getToReplaceIn().getParameters();
    for (int i = 0; i < parameters.length; i++) {
      GrParameter parameter = parameters[i];
      if (shouldRemove(parameter, range.getStartOffset(), range.getEndOffset())) {
        result.put(parameter, i);
      }
    }
    return result;
  }

  private static boolean shouldRemove(GrParameter parameter, int start, int end) {
    for (PsiReference reference : ReferencesSearch.search(parameter)) {
      final PsiElement element = reference.getElement();
      if (element == null) continue;

      final int offset = element.getTextRange().getStartOffset();
      if (offset < start || end <= offset) {
        return false;
      }
    }
    return true;
  }

  static PsiElement[] getOccurrences(GrIntroduceParameterSettings settings) {
    final GrParametersOwner scope = settings.getToReplaceIn();

    final GrExpression expression = settings.getExpression();
    if (expression != null) {
      final PsiElement expr = PsiUtil.skipParentheses(expression, false);
      if (expr == null) return PsiElement.EMPTY_ARRAY;

      final PsiElement[] occurrences = GroovyRefactoringUtil.getExpressionOccurrences(expr, scope);
      if (occurrences == null || occurrences.length == 0) {
        throw new GrRefactoringError(GroovyRefactoringBundle.message("no.occurrences.found"));
      }
      return occurrences;
    }
    else {
      final GrVariable var = settings.getVar();
      LOG.assertTrue(var != null);
      final List<PsiElement> list = Collections.synchronizedList(new ArrayList<PsiElement>());
      ReferencesSearch.search(var, new LocalSearchScope(scope)).forEach(psiReference -> {
        final PsiElement element = psiReference.getElement();
        if (element != null) {
          list.add(element);
        }
        return true;
      });
      return list.toArray(new PsiElement[list.size()]);
    }
  }

  @Nullable
  public static GrExpression addClosureToCall(PsiElement initializer, GrArgumentList list) {
    if (!(initializer instanceof GrClosableBlock)) return null;

    final PsiElement parent = list.getParent();
    if (!(parent instanceof GrMethodCallExpression)) return null;

    PsiElement anchor;
    final GrClosableBlock[] cls = ((GrMethodCallExpression)parent).getClosureArguments();
    if (cls.length > 0) {
      anchor = cls[cls.length - 1];
    }
    else {
      anchor = list;
    }

    return (GrExpression)parent.addAfter(initializer, anchor);
  }

  @Nullable
  static GrVariable findVar(IntroduceParameterInfo info) {
    GrVariable variable = info.getVar();
    if (variable != null) return variable;

    final GrStatement[] statements = info.getStatements();
    if (statements.length != 1) return null;
    return GrIntroduceHandlerBase.findVariable(statements[0]);
  }

  @Nullable
  static GrExpression findExpr(IntroduceParameterInfo info) {
    final GrStatement[] statements = info.getStatements();
    if (statements.length != 1) return null;
    return GrIntroduceHandlerBase.findExpression(statements[0]);
  }

  static LinkedHashSet<String> suggestNames(GrVariable var,
                                            GrExpression expr,
                                            StringPartInfo stringPart,
                                            GrParametersOwner scope,
                                            Project project) {
    if (expr != null) {
      final GrIntroduceContext
        introduceContext = new GrIntroduceContextImpl(project, null, expr, var, stringPart, PsiElement.EMPTY_ARRAY, scope);
      final GroovyFieldValidator validator = new GroovyFieldValidator(introduceContext);
      return new LinkedHashSet<>(Arrays.asList(GroovyNameSuggestionUtil.suggestVariableNames(expr, validator, true)));
    }
    else if (var != null) {
      final GrIntroduceContext introduceContext = new GrIntroduceContextImpl(project, null, expr, var, stringPart, PsiElement.EMPTY_ARRAY, scope);
      final GroovyFieldValidator validator = new GroovyFieldValidator(introduceContext);
      LinkedHashSet<String> names = new LinkedHashSet<>();
      names.add(var.getName());
      ContainerUtil.addAll(names, GroovyNameSuggestionUtil.suggestVariableNameByType(var.getType(), validator));
      return names;
    }
    else {
      LinkedHashSet<String> names = new LinkedHashSet<>();
      names.add("closure");
      return names;
    }
  }

  private static class FieldSearcher extends GroovyRecursiveElementVisitor {
    PsiClass myClass;
    private final List<PsiField> result = new ArrayList<>();

    private FieldSearcher(PsiClass aClass) {
      myClass = aClass;
    }

    public PsiField[] getResult() {
      return ContainerUtil.toArray(result, new PsiField[result.size()]);
    }

    @Override
    public void visitReferenceExpression(GrReferenceExpression ref) {
      super.visitReferenceExpression(ref);
      final GrExpression qualifier = ref.getQualifier();
      if (!PsiUtil.isThisReference(qualifier)) return;

      final PsiElement resolved = ref.resolve();
      if (!(resolved instanceof PsiField)) return;
      final PsiMethod getter = GroovyPropertyUtils.findGetterForField((PsiField)resolved);
      if (getter != null) {
        result.add((PsiField)resolved);
      }
    }
  }
}
