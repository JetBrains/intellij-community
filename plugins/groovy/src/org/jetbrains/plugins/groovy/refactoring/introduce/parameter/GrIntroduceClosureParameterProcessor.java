/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.lang.findUsages.DescriptiveNameUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.ExpressionConverter;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.IntroduceParameterRefactoring;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.introduceParameter.ChangedMethodCallInfo;
import com.intellij.refactoring.introduceParameter.ExternalUsageInfo;
import com.intellij.refactoring.introduceParameter.InternalUsageInfo;
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;
import org.jetbrains.plugins.groovy.refactoring.introduce.StringPartInfo;
import org.jetbrains.plugins.groovy.refactoring.introduce.parameter.java2groovy.FieldConflictsResolver;
import org.jetbrains.plugins.groovy.refactoring.introduce.parameter.java2groovy.OldReferencesResolver;
import org.jetbrains.plugins.groovy.refactoring.util.AnySupers;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.List;

/**
 * @author Medvedev Max
 */
public class GrIntroduceClosureParameterProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance(GrIntroduceClosureParameterProcessor.class);

  private final GrIntroduceParameterSettings mySettings;
  private final GrClosableBlock toReplaceIn;
  private final PsiElement toSearchFor;
  private final GrExpressionWrapper myParameterInitializer;
  private final GroovyPsiElementFactory myFactory = GroovyPsiElementFactory.getInstance(myProject);

  public GrIntroduceClosureParameterProcessor(@NotNull GrIntroduceParameterSettings settings) {
    super(settings.getProject(), null);
    mySettings = settings;

    toReplaceIn = (GrClosableBlock)mySettings.getToReplaceIn();
    toSearchFor = mySettings.getToSearchFor();

    final StringPartInfo info = settings.getStringPartInfo();
    final GrExpression expression = info != null ?
                                    info.createLiteralFromSelected() :
                                    mySettings.getExpression();
    myParameterInitializer = new GrExpressionWrapper(expression);
  }

  @Override
  @NotNull
  protected UsageViewDescriptor createUsageViewDescriptor(@NotNull final UsageInfo[] usages) {
    return new UsageViewDescriptorAdapter() {
      @NotNull
      @Override
      public PsiElement[] getElements() {
        return new PsiElement[]{toSearchFor != null ? toSearchFor : toReplaceIn};
      }

      @Override
      public String getProcessedElementsHeader() {
        return GroovyRefactoringBundle.message("introduce.closure.parameter.elements.header");
      }
    };
  }

  @Override
  protected boolean preprocessUsages(@NotNull Ref<UsageInfo[]> refUsages) {
    UsageInfo[] usagesIn = refUsages.get();
    MultiMap<PsiElement, String> conflicts = new MultiMap<>();

    if (!mySettings.generateDelegate()) {
      detectAccessibilityConflicts(usagesIn, conflicts);
    }

    final GrExpression expression = mySettings.getExpression();
    if (expression != null && toSearchFor instanceof PsiMember) {
      final AnySupers anySupers = new AnySupers();
      expression.accept(anySupers);
      if (anySupers.containsSupers()) {
        final PsiElement containingClass = PsiUtil.getFileOrClassContext(toReplaceIn);
        for (UsageInfo usageInfo : usagesIn) {
          if (!(usageInfo.getElement() instanceof PsiMethod) && !(usageInfo instanceof InternalUsageInfo)) {
            if (!PsiTreeUtil.isAncestor(containingClass, usageInfo.getElement(), false)) {
              conflicts.putValue(expression, RefactoringBundle
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
    final GrExpression expression = mySettings.getExpression();
    if (expression == null) return;

    GroovyIntroduceParameterUtil.detectAccessibilityConflicts(expression, usageArray, conflicts,
      mySettings.replaceFieldsWithGetters() != IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, myProject);
  }

  @NotNull
  @Override
  protected UsageInfo[] findUsages() {
    ArrayList<UsageInfo> result = new ArrayList<>();

    if (!mySettings.generateDelegate() && toSearchFor != null) {
      Collection<PsiReference> refs;
      if (toSearchFor instanceof GrField) {
        refs = ReferencesSearch.search(toSearchFor).findAll();
        final GrAccessorMethod[] getters = ((GrField)toSearchFor).getGetters();
        for (GrAccessorMethod getter : getters) {
          refs.addAll(MethodReferencesSearch.search(getter, getter.getResolveScope(), true).findAll());
        }
      }
      else if (toSearchFor instanceof GrVariable) {
        refs = findUsagesForLocal(toReplaceIn, ((GrVariable)toSearchFor));
      }
      else {
        refs = ReferencesSearch.search(toSearchFor).findAll();
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

      if (toSearchFor instanceof GrVariable && !((GrVariable)toSearchFor).hasModifierProperty(PsiModifier.FINAL)) {
        setPreviewUsages(true);
      }
    }

    if (mySettings.replaceAllOccurrences()) {
      PsiElement[] exprs = GroovyIntroduceParameterUtil.getOccurrences(mySettings);
      for (PsiElement expr : exprs) {
        result.add(new InternalUsageInfo(expr));
      }
    }
    else {
      if (mySettings.getExpression() != null) {
        result.add(new InternalUsageInfo(mySettings.getExpression()));
      }
    }

    final UsageInfo[] usageInfos = result.toArray(new UsageInfo[result.size()]);
    return UsageViewUtil.removeDuplicatedUsages(usageInfos);
  }

  private static Collection<PsiReference> findUsagesForLocal(GrClosableBlock initializer, final GrVariable var) {
    final Instruction[] flow = ControlFlowUtils.findControlFlowOwner(initializer).getControlFlow();
    final List<BitSet> writes = ControlFlowUtils.inferWriteAccessMap(flow, var);

    Instruction writeInstr = null;

    final PsiElement parent = initializer.getParent();
    if (parent instanceof GrVariable) {
      writeInstr = ContainerUtil.find(flow, instruction -> instruction.getElement() == var);
    }
    else if (parent instanceof GrAssignmentExpression) {
      final GrReferenceExpression refExpr = (GrReferenceExpression)((GrAssignmentExpression)parent).getLValue();
      final Instruction instruction = ContainerUtil.find(flow, instruction1 -> instruction1.getElement() == refExpr);

      LOG.assertTrue(instruction != null);
      final BitSet prev = writes.get(instruction.num());
      if (prev.cardinality() == 1) {
        writeInstr = flow[prev.nextSetBit(0)];
      }
    }

    LOG.assertTrue(writeInstr != null);

    Collection<PsiReference> result = new ArrayList<>();
    for (Instruction instruction : flow) {
      if (!(instruction instanceof ReadWriteVariableInstruction)) continue;
      if (((ReadWriteVariableInstruction)instruction).isWrite()) continue;

      final PsiElement element = instruction.getElement();
      if (element instanceof GrVariable && element != var) continue;
      if (!(element instanceof GrReferenceExpression)) continue;

      final GrReferenceExpression ref = (GrReferenceExpression)element;
      if (ref.isQualified() || ref.resolve() != var) continue;

      final BitSet prev = writes.get(instruction.num());
      if (prev.cardinality() == 1 && prev.get(writeInstr.num())) {
        result.add(ref);
      }
    }

    return result;
  }

  @Override
  protected void performRefactoring(@NotNull UsageInfo[] usages) {
    processExternalUsages(usages, mySettings, myParameterInitializer.getExpression());
    processClosure(usages, mySettings);

    final GrVariable var = mySettings.getVar();
    if (var != null && mySettings.removeLocalVariable()) {
      var.delete();
    }
  }

  public static void processClosure(UsageInfo[] usages, GrIntroduceParameterSettings settings) {
    changeSignature((GrClosableBlock)settings.getToReplaceIn(), settings);
    processInternalUsages(usages, settings);
  }

  private static void processInternalUsages(UsageInfo[] usages, GrIntroduceParameterSettings settings) {
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(settings.getProject());
    // Replacing expression occurrences
    for (UsageInfo usage : usages) {
      if (usage instanceof ChangedMethodCallInfo) {
        PsiElement element = usage.getElement();

        processChangedMethodCall(element, settings);
      }
      else if (usage instanceof InternalUsageInfo) {
        PsiElement element = usage.getElement();
        if (element == null) continue;
        GrExpression newExpr = factory.createExpressionFromText(settings.getName());
        if (element instanceof GrExpression) {
          ((GrExpression)element).replaceWithExpression(newExpr, true);
        }
        else {
          element.replace(newExpr);
        }
      }
    }

    final StringPartInfo info = settings.getStringPartInfo();
    if (info != null) {
      final GrExpression expr = info.replaceLiteralWithConcatenation(settings.getName());
      final Editor editor = PsiUtilBase.findEditor(expr);
      if (editor != null) {
        editor.getSelectionModel().removeSelection();
        editor.getCaretModel().moveToOffset(expr.getTextRange().getEndOffset());
      }
    }
  }

  public static void processExternalUsages(UsageInfo[] usages, GrIntroduceParameterSettings settings, PsiElement expression) {
    for (UsageInfo usage : usages) {
      if (usage instanceof ExternalUsageInfo) {
        processExternalUsage(usage, settings, expression);
      }
    }
  }

  private static void changeSignature(GrClosableBlock block, GrIntroduceParameterSettings settings) {
    final String name = settings.getName();
    final FieldConflictsResolver fieldConflictsResolver = new FieldConflictsResolver(name, block);

    final GrParameter[] parameters = block.getParameters();
    settings.parametersToRemove().forEachDescending(paramNum -> {
      try {
        PsiParameter param = parameters[paramNum];
        param.delete();
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
      return true;
    });

    final PsiType type = settings.getSelectedType();
    final String typeText = type == null ? null : type.getCanonicalText();
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(settings.getProject());
    GrParameter parameter = factory.createParameter(name, typeText, block);
    parameter.getModifierList().setModifierProperty(PsiModifier.FINAL, settings.declareFinal());

    final GrParameterList parameterList = block.getParameterList();
    final PsiParameter anchorParameter = GroovyIntroduceParameterUtil.getAnchorParameter(parameterList, block.isVarArgs());
    parameter = (GrParameter)parameterList.addAfter(parameter, anchorParameter);

    if (block.getArrow() == null) {
      final PsiElement arrow = block.addAfter(factory.createClosureFromText("{->}").getArrow().copy(), parameterList);
      final PsiElement child = block.getFirstChild().getNextSibling();
      if (PsiImplUtil.isWhiteSpaceOrNls(child)) {
        final String text = child.getText();
        child.delete();
        block.addAfter(factory.createLineTerminator(text), arrow);
      }
    }
    JavaCodeStyleManager.getInstance(parameter.getProject()).shortenClassReferences(parameter);

    fieldConflictsResolver.fix();
  }

  private static void processExternalUsage(UsageInfo usage, GrIntroduceParameterSettings settings, PsiElement expression) {
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

    GrClosableBlock toReplaceIn = (GrClosableBlock)settings.getToReplaceIn();
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(settings.getProject());

    final GrExpression anchor = getAnchorForArgument(oldArgs, toReplaceIn.isVarArgs(), toReplaceIn.getParameterList());

    GrClosureSignature signature = GrClosureSignatureUtil.createSignature(callExpression);
    if (signature == null) signature = GrClosureSignatureUtil.createSignature(toReplaceIn);

    final GrClosureSignatureUtil.ArgInfo<PsiElement>[] actualArgs = GrClosureSignatureUtil
      .mapParametersToArguments(signature, callExpression.getNamedArguments(), callExpression.getExpressionArguments(),
                                callExpression.getClosureArguments(), callExpression, true, true);

    if (PsiTreeUtil.isAncestor(toReplaceIn, callExpression, false)) {
      argList.addAfter(factory.createExpressionFromText(settings.getName()), anchor);
    }
    else {
      PsiElement initializer = ExpressionConverter.getExpression(expression, GroovyLanguage.INSTANCE, settings.getProject());
      LOG.assertTrue(initializer instanceof GrExpression);

      GrExpression newArg = GroovyIntroduceParameterUtil.addClosureToCall(initializer, argList);
      if (newArg == null) {
        final PsiElement dummy = argList.addAfter(factory.createExpressionFromText("1"), anchor);
        newArg = ((GrExpression)dummy).replaceWithExpression((GrExpression)initializer, true);
      }
      new OldReferencesResolver(callExpression, newArg, toReplaceIn, settings.replaceFieldsWithGetters(), initializer, signature,
                                actualArgs, toReplaceIn.getParameters()).resolve();
      ChangeContextUtil.clearContextInfo(initializer);

      //newarg can be replaced by OldReferenceResolve
      if (newArg.isValid()) {
        JavaCodeStyleManager.getInstance(newArg.getProject()).shortenClassReferences(newArg);
        CodeStyleManager.getInstance(settings.getProject()).reformat(newArg);
      }
    }

    if (actualArgs == null) {
      GroovyIntroduceParameterUtil
        .removeParamsFromUnresolvedCall(callExpression, toReplaceIn.getParameters(), settings.parametersToRemove());
    }
    else {
      GroovyIntroduceParameterUtil.removeParametersFromCall(actualArgs, settings.parametersToRemove());
    }

    if (argList.getAllArguments().length == 0 && PsiImplUtil.hasClosureArguments(callExpression)) {
      final GrArgumentList emptyArgList = ((GrMethodCallExpression)factory.createExpressionFromText("foo{}")).getArgumentList();
      argList.replace(emptyArgList);
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
    for (int i = 0; i < parameters.length; i++) {
      if (!mySettings.parametersToRemove().contains(i)) {
        call.append(parameters[i].getName()).append(", ");
      }
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

  private static void processChangedMethodCall(PsiElement element, GrIntroduceParameterSettings settings) {
    if (element.getParent() instanceof GrMethodCallExpression) {
      GrMethodCallExpression methodCall = (GrMethodCallExpression)element.getParent();

      final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(settings.getProject());
      GrExpression expression = factory.createExpressionFromText(settings.getName(), null);
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
    else {
      LOG.error(element.getParent());
    }
  }

  private static void removeParametersFromCall(GrMethodCallExpression methodCall, GrIntroduceParameterSettings settings) {
    final GroovyResolveResult resolveResult = methodCall.advancedResolve();
    final PsiElement resolved = resolveResult.getElement();
    LOG.assertTrue(resolved instanceof PsiMethod);
    final GrClosureSignature signature = GrClosureSignatureUtil.createSignature((PsiMethod)resolved, resolveResult.getSubstitutor());
    final GrClosureSignatureUtil.ArgInfo<PsiElement>[] argInfos = GrClosureSignatureUtil.mapParametersToArguments(signature, methodCall);
    LOG.assertTrue(argInfos != null);
    settings.parametersToRemove().forEach(value -> {
      final List<PsiElement> args = argInfos[value].args;
      for (PsiElement arg : args) {
        arg.delete();
      }
      return true;
    });
  }

  @NotNull
  @Override
  protected String getCommandName() {
    return RefactoringBundle.message("introduce.parameter.command", DescriptiveNameUtil.getDescriptiveName(mySettings.getToReplaceIn()));
  }


}
