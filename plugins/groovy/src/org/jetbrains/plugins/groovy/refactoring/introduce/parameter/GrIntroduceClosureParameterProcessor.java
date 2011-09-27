/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.IntroduceParameterRefactoring;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.introduceParameter.*;
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.ConflictsUtil;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.MultiMap;
import gnu.trove.TIntProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.GrReferenceAdjuster;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;
import org.jetbrains.plugins.groovy.lang.psi.impl.types.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;
import org.jetbrains.plugins.groovy.refactoring.inline.InlineMethodConflictSolver;
import org.jetbrains.plugins.groovy.refactoring.introduce.parameter.java2groovy.FieldConflictsResolver;
import org.jetbrains.plugins.groovy.refactoring.introduce.parameter.java2groovy.OldReferencesResolver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Medvedev Max
 */
public class GrIntroduceClosureParameterProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance(GrIntroduceClosureParameterProcessor.class);

  private GrIntroduceParameterSettings mySettings;
  private GrIntroduceParameterContext myContext;
  private GrClosableBlock toReplaceIn;
  private PsiElement toSearchFor;
  private GrExpressionWrapper myParameterInitializer;
  private GroovyPsiElementFactory myFactory = GroovyPsiElementFactory.getInstance(myProject);

  public GrIntroduceClosureParameterProcessor(GrIntroduceParameterSettings settings, GrIntroduceParameterContext context) {
    super(context.project, null);
    mySettings = settings;
    myContext = context;

    toReplaceIn = (GrClosableBlock)myContext.toReplaceIn;
    toSearchFor = myContext.toSearchFor;
    myParameterInitializer = new GrExpressionWrapper(this.myContext.expression);
  }

  @NotNull
  protected UsageViewDescriptor createUsageViewDescriptor(final UsageInfo[] usages) {
    return new UsageViewDescriptorAdapter() {
      @NotNull
      @Override
      public PsiElement[] getElements() {
        return new PsiElement[]{toSearchFor != null ? toSearchFor : toReplaceIn};
      }

      @Override
      public String getProcessedElementsHeader() {
        return RefactoringBundle.message("introduce.parameter.elements.header");
      }
    };
  }

  @Override
  protected boolean preprocessUsages(Ref<UsageInfo[]> refUsages) {
    UsageInfo[] usagesIn = refUsages.get();
    MultiMap<PsiElement, String> conflicts = new MultiMap<PsiElement, String>();

    if (!mySettings.generateDelegate()) {
      detectAccessibilityConflicts(usagesIn, conflicts);
    }

    if (myContext.expression != null && toSearchFor instanceof PsiMember) {
      final AnySupers anySupers = new AnySupers();
      myContext.expression.accept(anySupers);
      if (anySupers.isResult()) {
        final PsiElement containingClass = PsiUtil.getFileOrClassContext(toReplaceIn);
        for (UsageInfo usageInfo : usagesIn) {
          if (!(usageInfo.getElement() instanceof PsiMethod) && !(usageInfo instanceof InternalUsageInfo)) {
            if (!PsiTreeUtil.isAncestor(containingClass, usageInfo.getElement(), false)) {
              conflicts.putValue(myContext.expression, RefactoringBundle
                .message("parameter.initializer.contains.0.but.not.all.calls.to.method.are.in.its.class", CommonRefactoringUtil.htmlEmphasize(PsiKeyword.SUPER)));
              break;
            }
          }
        }
      }
    }

    //todo
    //for (IntroduceParameterMethodUsagesProcessor processor : IntroduceParameterMethodUsagesProcessor.EP_NAME.getExtensions()) {
      //processor.findConflicts(this, refUsages.get(), conflicts);
    //}

    return showConflicts(conflicts, usagesIn);
  }



  private void detectAccessibilityConflicts(final UsageInfo[] usageArray, MultiMap<PsiElement, String> conflicts) {
    //todo whole method
    if (myContext.expression == null) return;

    final ReferencedElementsCollector collector = new ReferencedElementsCollector();
    myContext.expression.accept(collector);
    final List<PsiElement> result = collector.getResult();
    if (result.isEmpty()) return;

    for (final UsageInfo usageInfo : usageArray) {
      if (!(usageInfo instanceof ExternalUsageInfo) || !IntroduceParameterUtil.isMethodUsage(usageInfo)) continue;

      final PsiElement place = usageInfo.getElement();
      for (PsiElement element : result) {
        if (element instanceof PsiField &&
            mySettings.replaceFieldsWithGetters() != IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE) {
          //check getter access instead
          final PsiClass psiClass = ((PsiField)element).getContainingClass();
          LOG.assertTrue(psiClass != null);
          final PsiMethod method = GroovyPropertyUtils.findGetterForField((PsiField)element);
          if (method != null) {
            element = method;
          }
        }
        if (element instanceof PsiMember &&
            !JavaPsiFacade.getInstance(myProject).getResolveHelper().isAccessible((PsiMember)element, place, null)) {
          String message = RefactoringBundle.message(
            "0.is.not.accessible.from.1.value.for.introduced.parameter.in.that.method.call.will.be.incorrect",
            RefactoringUIUtil.getDescription(element, true),
            RefactoringUIUtil.getDescription(ConflictsUtil.getContainer(place), true));
          conflicts.putValue(element, message);
        }
      }
    }
  }

  @NotNull
  @Override
  protected UsageInfo[] findUsages() {
    ArrayList<UsageInfo> result = new ArrayList<UsageInfo>();

    if (!mySettings.generateDelegate() && toSearchFor != null) {
      Collection<PsiReference> refs = ReferencesSearch.search(toSearchFor, toSearchFor.getResolveScope(), true).findAll();
      if (toSearchFor instanceof GrField) {
        final GrAccessorMethod[] getters = ((GrField)toSearchFor).getGetters();
        for (GrAccessorMethod getter : getters) {
          refs.addAll(MethodReferencesSearch.search(getter, getter.getResolveScope(), true).findAll());
        }
      }

      for (PsiReference ref1 : refs) {
        PsiElement ref = ref1.getElement();
        if (!PsiTreeUtil.isAncestor(toReplaceIn, ref, false)) {
          result.add(new ExternalUsageInfo(ref));
        }
        else {
          result.add(new ChangedMethodCallInfo(ref));
        }
      }
    }

    if (mySettings.replaceAllOccurrences()) {
      PsiElement[] exprs = myContext.occurrences;
      for (PsiElement expr : exprs) {
        result.add(new InternalUsageInfo(expr));
      }
    }
    else {
      if (myContext.expression != null) {
        result.add(new InternalUsageInfo(myContext.expression));
      }
    }

    final UsageInfo[] usageInfos = result.toArray(new UsageInfo[result.size()]);
    return UsageViewUtil.removeDuplicatedUsages(usageInfos);
  }

  @Override
  protected void performRefactoring(UsageInfo[] usages) {
    if (mySettings.generateDelegate()) {
      LOG.assertTrue(toSearchFor instanceof GrVariable);

      String newName = InlineMethodConflictSolver.suggestNewName(((GrVariable)toSearchFor).getName() + "Delegate", null, toSearchFor);
      GrClosableBlock result = generateDelegateClosure(this.toReplaceIn, (GrVariable)toSearchFor, newName);

      processClosure(usages);

      final GrVariableDeclaration declaration = myFactory.createVariableDeclaration(null, toReplaceIn, ((GrVariable)toSearchFor)
        .getDeclaredType(), newName);
      declaration.getModifierList().replace(((GrVariable)toSearchFor).getModifierList());
      toReplaceIn.replace(result);
      insertDeclaration((GrVariable)toSearchFor, declaration).getVariables()[0].getInitializerGroovy();
    }
    else {
      processExternalUsages(usages);

      processClosure(usages);
    }

    if (myContext.var != null && mySettings.removeLocalVariable()) {
      myContext.var.delete();
    }
  }

  private void processClosure(UsageInfo[] usages) {
    changeSignature(toReplaceIn);
    processInternalUsages(usages);
  }

  private void processInternalUsages(UsageInfo[] usages) {
    // Replacing expression occurrences
    for (UsageInfo usage : usages) {
      if (usage instanceof ChangedMethodCallInfo) {
        PsiElement element = usage.getElement();

        processChangedMethodCall(element);
      }
      else if (usage instanceof InternalUsageInfo) {
        PsiElement element = usage.getElement();
        if (element == null) continue;
        GrExpression newExpr = myFactory.createExpressionFromText(mySettings.getName());
        if (element instanceof GrExpression) {
          ((GrExpression)element).replaceWithExpression(newExpr, true);
        }
        else {
          element.replace(newExpr);
        }
      }
    }
  }

  private void processExternalUsages(UsageInfo[] usages) {
    for (UsageInfo usage : usages) {
      if (usage instanceof ExternalUsageInfo) {
        processExternalUsage(usage);
      }
    }
  }

  private void changeSignature(GrClosableBlock block) {
    final String name = mySettings.getName();
    final FieldConflictsResolver fieldConflictsResolver = new FieldConflictsResolver(name, block);

    final GrParameter[] parameters = block.getParameters();
    mySettings.parametersToRemove().forEachDescending(new TIntProcedure() {
      public boolean execute(final int paramNum) {
        try {
          PsiParameter param = parameters[paramNum];
          param.delete();
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
        return true;
      }
    });

    final PsiType type = mySettings.getSelectedType();
    final String typeText = type == null ? null : type.getCanonicalText();
    GrParameter parameter = myFactory.createParameter(name, typeText, block);
    parameter.getModifierList().setModifierProperty(PsiModifier.FINAL, mySettings.declareFinal());

    final GrParameterList parameterList = block.getParameterList();
    final PsiParameter anchorParameter = GroovyIntroduceParameterUtil.getAnchorParameter(parameterList, block.isVarArgs());
    parameter = (GrParameter)parameterList.addAfter(parameter, anchorParameter);

    if (block.getArrow() == null) {
      final PsiElement arrow = block.addAfter(myFactory.createClosureFromText("{->}").getArrow().copy(), parameterList);
      final PsiElement child = block.getFirstChild().getNextSibling();
      if (TokenSets.WHITE_SPACES_SET.contains(child.getNode().getElementType())) {
        block.addAfter(child, arrow);
        child.delete();
      }
    }
    GrReferenceAdjuster.shortenReferences(parameter);

    fieldConflictsResolver.fix();
  }

  private void processExternalUsage(UsageInfo usage) {
    final PsiElement element = usage.getElement();
    GrCall callExpression = GroovyRefactoringUtil.getCallExpressionByMethodReference(element);
    if (callExpression == null) {
      final PsiElement parent = element.getParent();
      if (parent instanceof GrReferenceExpression && element == ((GrReferenceExpression)parent).getQualifier() && "call".equals(
        ((GrReferenceExpression)parent).getReferenceName())) {
        callExpression = GroovyRefactoringUtil.getCallExpressionByMethodReference(parent);
      }
    }
    
    if (callExpression == null) return;
      
      
    //LOG.assertTrue(callExpression != null);

    //check for x.getFoo()(args)
    if (callExpression instanceof GrMethodCall) {
      final GrExpression invoked = ((GrMethodCall)callExpression).getInvokedExpression();
      if (invoked instanceof GrReferenceExpression) {
        final GroovyResolveResult result = ((GrReferenceExpression)invoked).advancedResolve();
        final PsiElement resolved = result.getElement();
        if (resolved instanceof GrAccessorMethod && !result.isInvokedOnProperty()) {
          PsiElement actualCallExpression = callExpression.getParent();
          if (actualCallExpression instanceof GrCall) {
            callExpression = (GrCall)actualCallExpression;
          }
        }
      }
    }
    
    GrArgumentList argList = callExpression.getArgumentList();
    LOG.assertTrue(argList != null);
    GrExpression[] oldArgs = argList.getExpressionArguments();

    final GrExpression anchor = getAnchorForArgument(oldArgs, toReplaceIn.isVarArgs(), toReplaceIn.getParameterList());

    GrClosureSignature signature = GrClosureSignatureUtil.createSignature(callExpression);
    if (signature == null) signature = GrClosureSignatureUtil.createSignature(toReplaceIn);

    final GrClosureSignatureUtil.ArgInfo<PsiElement>[] actualArgs =
      GrClosureSignatureUtil.mapParametersToArguments(signature, argList, callExpression, callExpression.getClosureArguments(), true);

    if (PsiTreeUtil.isAncestor(toReplaceIn, callExpression, false)) {
      argList.addAfter(myFactory.createExpressionFromText(mySettings.getName()), anchor);
    }
    else {
      PsiElement initializer = ExpressionConverter.getExpression(myParameterInitializer.getExpression(), GroovyFileType.GROOVY_LANGUAGE, myProject);
      LOG.assertTrue(initializer instanceof GrExpression);

      GrExpression newArg = (GrExpression)argList.addAfter(initializer, anchor);
      new OldReferencesResolver(callExpression, newArg, toReplaceIn, mySettings.replaceFieldsWithGetters(), initializer, signature,
                                actualArgs, toReplaceIn.getParameters()).resolve();
      ChangeContextUtil.clearContextInfo(initializer);
    }

    if (actualArgs == null) {
      GroovyIntroduceParameterUtil.removeParamsFromUnresolvedCall(callExpression, toReplaceIn.getParameters(), mySettings.parametersToRemove());
    }
    else {
      GroovyIntroduceParameterUtil.removeParametersFromCall(actualArgs, mySettings.parametersToRemove());
    }
  }

  @Nullable
  private static GrExpression getAnchorForArgument(GrExpression[] oldArgs, boolean isVarArg, PsiParameterList parameterList) {
    if (!isVarArg) return ArrayUtil.getLastElement(oldArgs);

    final PsiParameter[] parameters = parameterList.getParameters();
    if (parameters.length > oldArgs.length) return ArrayUtil.getLastElement(oldArgs);

    final int lastNonVararg = parameters.length - 2;
    return lastNonVararg >= 0 ? oldArgs[lastNonVararg] : null;
  }


  private GrClosableBlock generateDelegateClosure(GrClosableBlock originalClosure, GrVariable anchor, String newName) {
    GrClosableBlock result;
    if (originalClosure.hasParametersSection()) {
      result = myFactory.createClosureFromText("{->}", anchor);
      final GrParameterList parameterList = (GrParameterList)originalClosure.getParameterList().copy();
      result.getParameterList().replace(parameterList);
    }
    else {
      result = myFactory.createClosureFromText("{}", anchor);
    }

    StringBuilder call = new StringBuilder();
    call.append(newName).append('(');

    final GrParameter[] parameters = result.getParameters();
    for (GrParameter parameter : parameters) {
      call.append(parameter.getName()).append(", ");
    }
    call.append(myParameterInitializer.getText());
    call.append(")");

    final GrStatement statement = myFactory.createStatementFromText(call.toString());
    result.addStatementBefore(statement, null);
    return result;
  }

  private GrVariableDeclaration insertDeclaration(GrVariable original, GrVariableDeclaration declaration) {
    if (original instanceof GrField) {
      final PsiClass containingClass = ((GrField)original).getContainingClass();
      LOG.assertTrue(containingClass != null);
      return (GrVariableDeclaration)containingClass.addBefore(declaration, original.getParent());
    }

    final GrStatementOwner block;
    if (original instanceof PsiParameter) {
      final PsiElement container = original.getParent().getParent();
      if (container instanceof GrMethod) {
        block = ((GrMethod)container).getBlock();
      }
      else if (container instanceof GrClosableBlock) {
        block = (GrCodeBlock)container;
      }
      else if (container instanceof GrForStatement) {
        final GrStatement body = ((GrForStatement)container).getBody();
        if (body instanceof GrBlockStatement) {
          block = ((GrBlockStatement)body).getBlock();
        }
        else {
          GrBlockStatement blockStatement = myFactory.createBlockStatement();
          LOG.assertTrue(blockStatement != null);
          if (body != null) {
            blockStatement.getBlock().addStatementBefore((GrStatement)body.copy(), null);
            blockStatement = (GrBlockStatement)body.replace(blockStatement);
          }
          else {
            blockStatement = (GrBlockStatement)container.add(blockStatement);
          }
          block = blockStatement.getBlock();
        }
      }
      else {
        throw new IncorrectOperationException();
      }

      LOG.assertTrue(block != null);
      return (GrVariableDeclaration)block.addStatementBefore(declaration, null);
    }

    PsiElement parent = original.getParent();
    LOG.assertTrue(parent instanceof GrVariableDeclaration);

    final PsiElement pparent = parent.getParent();

    if (pparent instanceof GrIfStatement) {
      if (((GrIfStatement)pparent).getThenBranch() == parent) {
        block = ((GrIfStatement)pparent).replaceThenBranch(myFactory.createBlockStatement()).getBlock();
      }
      else {
        block = ((GrIfStatement)pparent).replaceElseBranch(myFactory.createBlockStatement()).getBlock();
      }
      parent = block.addStatementBefore(((GrVariableDeclaration)parent), null);
    }
    else if (pparent instanceof GrLoopStatement) {
      block = ((GrLoopStatement)pparent).replaceBody(myFactory.createBlockStatement()).getBlock();
      parent = block.addStatementBefore(((GrVariableDeclaration)parent), null);
    }
    else {
      LOG.assertTrue(pparent instanceof GrStatementOwner);
      block = (GrStatementOwner)pparent;
    }

    return (GrVariableDeclaration)block.addStatementBefore(declaration, (GrStatement)parent);
  }

  private void processChangedMethodCall(PsiElement element) {
    if (element.getParent() instanceof GrMethodCallExpression) {
      GrMethodCallExpression methodCall = (GrMethodCallExpression)element.getParent();

      GrExpression expression = myFactory.createExpressionFromText(mySettings.getName(), null);
      final GrArgumentList argList = methodCall.getArgumentList();
      final PsiElement[] exprs = argList.getAllArguments();

      if (exprs.length > 0) {
        argList.addAfter(expression, exprs[exprs.length - 1]);
      }
      else {
        argList.add(expression);
      }

      removeParametersFromCall(methodCall, argList);

    }
    else {
      LOG.error(element.getParent());
    }
  }

  private void removeParametersFromCall(GrMethodCallExpression methodCall, GrArgumentList argList) {
    final GroovyResolveResult resolveResult = methodCall.advancedResolve();
    final PsiElement resolved = resolveResult.getElement();
    LOG.assertTrue(resolved instanceof PsiMethod);
    final GrClosureSignature signature = GrClosureSignatureUtil.createSignature((PsiMethod)resolved, resolveResult.getSubstitutor());
    final GrClosureSignatureUtil.ArgInfo<PsiElement>[] argInfos =
      GrClosureSignatureUtil.mapParametersToArguments(signature, argList, methodCall, methodCall.getClosureArguments());
    LOG.assertTrue(argInfos != null);
    mySettings.parametersToRemove().forEach(new TIntProcedure() {
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

  @Override
  protected String getCommandName() {
    return RefactoringBundle.message("introduce.parameter.command", UsageViewUtil.getDescriptiveName(myContext.toReplaceIn));
  }


}
