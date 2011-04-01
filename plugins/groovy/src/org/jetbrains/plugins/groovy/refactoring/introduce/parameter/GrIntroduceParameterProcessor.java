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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.IntroduceParameterRefactoring;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.introduceParameter.*;
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.ConflictsUtil;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.refactoring.util.usageInfo.DefaultConstructorImplicitUsageInfo;
import com.intellij.refactoring.util.usageInfo.NoConstructorClassUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.containers.MultiMap;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSuperReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.types.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Maxim.Medvedev
 */
public class GrIntroduceParameterProcessor extends BaseRefactoringProcessor implements IntroduceParameterData {
  private static final Logger LOG =
    Logger.getInstance("#org.jetbrains.plugins.groovy.refactoring.introduce.parameter.GrIntroduceParameterProcessor");

  private final GrIntroduceParameterSettings mySettings;
  private final GrIntroduceParameterContext myContext;
  private IntroduceParameterData.ExpressionWrapper myParameterInitializer;

  public GrIntroduceParameterProcessor(GrIntroduceParameterSettings settings, GrIntroduceParameterContext context) {
    super(context.project);
    this.mySettings = settings;
    this.myContext = context;

    myParameterInitializer = new GrExpressionWrapper(this.myContext.expression);
  }

  @Override
  protected UsageViewDescriptor createUsageViewDescriptor(final UsageInfo[] usages) {
    return new UsageViewDescriptorAdapter() {
      @NotNull
      @Override
      public PsiElement[] getElements() {
        return new PsiElement[]{myContext.methodToSearchFor};
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

    /*AnySameNameVariables anySameNameVariables = new AnySameNameVariables();
    myMethodToReplaceIn.accept(anySameNameVariables);
    final Pair<PsiElement, String> conflictPair = anySameNameVariables.getConflict();
    if (conflictPair != null) {
      conflicts.putValue(conflictPair.first, conflictPair.second);
    }*/

    if (!mySettings.generateDelegate()) {
      detectAccessibilityConflicts(usagesIn, conflicts);
    }

    if (myContext.expression != null && !myContext.methodToReplaceIn.hasModifierProperty(PsiModifier.PRIVATE)) {
      final AnySupers anySupers = new AnySupers();
      myContext.expression.accept(anySupers);
      if (anySupers.isResult()) {
        for (UsageInfo usageInfo : usagesIn) {
          if (!(usageInfo.getElement() instanceof PsiMethod) && !(usageInfo instanceof InternalUsageInfo)) {
            if (!PsiTreeUtil.isAncestor(myContext.methodToReplaceIn.getContainingClass(), usageInfo.getElement(), false)) {
              conflicts.putValue(myContext.expression,
                                 RefactoringBundle.message("parameter.initializer.contains.0.but.not.all.calls.to.method.are.in.its.class",
                                                           CommonRefactoringUtil.htmlEmphasize(PsiKeyword.SUPER)));
              break;
            }
          }
        }
      }
    }

    for (IntroduceParameterMethodUsagesProcessor processor : IntroduceParameterMethodUsagesProcessor.EP_NAME.getExtensions()) {
      processor.findConflicts(this, refUsages.get(), conflicts);
    }

    return showConflicts(conflicts, usagesIn);
  }

  private void detectAccessibilityConflicts(final UsageInfo[] usageArray, MultiMap<PsiElement, String> conflicts) {
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

  private static class AnySupers extends GroovyRecursiveElementVisitor {
    boolean myContainsSupers = false;

    @Override
    public void visitSuperExpression(GrSuperReferenceExpression superExpression) {
      super.visitSuperExpression(superExpression);
      myContainsSupers = true;
    }

    boolean isResult() {
      return myContainsSupers;
    }
  }

  private static class ReferencedElementsCollector extends GroovyRecursiveElementVisitor {

    private List<PsiElement> myResult = new ArrayList<PsiElement>();

    @Override
    public void visitReferenceExpression(GrReferenceExpression referenceExpression) {
      add(referenceExpression);
    }

    private void add(GrReferenceElement referenceExpression) {
      final PsiElement resolved = referenceExpression.resolve();
      if (resolved != null) {
        myResult.add(resolved);
      }
    }

    @Override
    public void visitCodeReferenceElement(GrCodeReferenceElement refElement) {
      add(refElement);
    }

    public List<PsiElement> getResult() {
      return myResult;
    }
  }

  @NotNull
  @Override
  protected UsageInfo[] findUsages() {
    ArrayList<UsageInfo> result = new ArrayList<UsageInfo>();

    final PsiMethod methodToSearchFor = myContext.methodToSearchFor;

    if (!mySettings.generateDelegate()) {
      Collection<PsiReference> refs =
        MethodReferencesSearch.search(methodToSearchFor, GlobalSearchScope.projectScope(myProject), true).findAll();

      for (PsiReference ref1 : refs) {
        PsiElement ref = ref1.getElement();
        if (ref instanceof PsiMethod && ((PsiMethod)ref).isConstructor()) {
          DefaultConstructorImplicitUsageInfo implicitUsageInfo =
            new DefaultConstructorImplicitUsageInfo((PsiMethod)ref, ((PsiMethod)ref).getContainingClass(), methodToSearchFor);
          result.add(implicitUsageInfo);
        }
        else if (ref instanceof PsiClass) {
          result.add(new NoConstructorClassUsageInfo((PsiClass)ref));
        }
        else if (!IntroduceParameterUtil.insideMethodToBeReplaced(ref, myContext.methodToReplaceIn)) {
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

    Collection<PsiMethod> overridingMethods =
      OverridingMethodsSearch.search(methodToSearchFor, methodToSearchFor.getUseScope(), true).findAll();

    for (PsiMethod overridingMethod : overridingMethods) {
      result.add(new UsageInfo(overridingMethod));
    }

    final UsageInfo[] usageInfos = result.toArray(new UsageInfo[result.size()]);
    return UsageViewUtil.removeDuplicatedUsages(usageInfos);
  }

  @Override
  protected void performRefactoring(UsageInfo[] usages) {
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(myProject);

    PsiType initializerType = mySettings.getSelectedType();

    // Changing external occurences (the tricky part)

    IntroduceParameterUtil.processUsages(usages, this);

    final boolean methodsToProcessAreDifferent = myContext.methodToReplaceIn != myContext.methodToSearchFor;
    if (mySettings.generateDelegate()) {
      generateDelegate(myContext.methodToReplaceIn);
      if (methodsToProcessAreDifferent) {
        final GrMethod method = generateDelegate(myContext.methodToSearchFor);
        final PsiClass containingClass = method.getContainingClass();
        if (containingClass != null && containingClass.isInterface()) {
          final GrOpenBlock block = method.getBlock();
          if (block != null) {
            block.delete();
          }
        }
      }
    }

    // Changing signature of initial method
    // (signature of myMethodToReplaceIn will be either changed now or have already been changed)
    LOG.assertTrue(initializerType == null || initializerType.isValid());

    final FieldConflictsResolver fieldConflictsResolver =
      new FieldConflictsResolver(mySettings.getName(), myContext.methodToReplaceIn.getBlock());
    IntroduceParameterUtil.changeMethodSignatureAndResolveFieldConflicts(new UsageInfo(myContext.methodToReplaceIn), usages, this);
    if (methodsToProcessAreDifferent) {
      IntroduceParameterUtil.changeMethodSignatureAndResolveFieldConflicts(new UsageInfo(myContext.methodToSearchFor), usages, this);
    }

    if (myContext.var != null) myContext.var.delete();

    // Replacing expression occurences
    for (UsageInfo usage : usages) {
      if (usage instanceof ChangedMethodCallInfo) {
        PsiElement element = usage.getElement();

        processChangedMethodCall(element);
      }
      else if (usage instanceof InternalUsageInfo) {
        PsiElement element = usage.getElement();
        if (element == null) continue;
        GrExpression newExpr = factory.createExpressionFromText(mySettings.getName());
        if (element instanceof GrExpression) {
          ((GrExpression)element).replaceWithExpression(newExpr, true);
        }
        else {
          element.replace(newExpr);
        }
      }
    }

    if (myContext.var != null && mySettings.removeLocalVariable()) {
      myContext.var.delete();
    }
    fieldConflictsResolver.fix();
  }

  private GrMethod generateDelegate(PsiMethod prototype) {
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(myProject);

    GrMethod result;
    if (prototype instanceof GrMethod) {
      result = (GrMethod)prototype.copy();
    }
    else {
      StringBuilder builder = new StringBuilder();
      builder.append(prototype.getModifierList().getText()).append(' ');

      if (prototype.getReturnTypeElement() != null) {
        builder.append(prototype.getReturnTypeElement().getText());
      }
      builder.append(' ').append(prototype.getName());
      builder.append(prototype.getParameterList().getText());
      builder.append("{}");
      result = factory.createMethodFromText(builder.toString());
    }

    StringBuilder call = new StringBuilder();
    call.append("def foo(){\n").append(prototype.getName()).append('(');
    final GrParameter[] parameters = result.getParameters();
    for (GrParameter parameter : parameters) {
      call.append(parameter.getName()).append(", ");
    }
    if (parameters.length > 0) {
      call.delete(call.length() - 2, call.length());
    }
    call.append(myParameterInitializer.getText());
    call.append(");\n}");
    final GrOpenBlock block = factory.createMethodFromText(call.toString()).getBlock();

    result.getBlock().replace(block);
    final PsiElement parent = myContext.methodToReplaceIn.getParent();
    return (GrMethod)parent.addBefore(result, myContext.methodToReplaceIn);
  }

  private void processChangedMethodCall(PsiElement element) {
    if (element.getParent() instanceof GrMethodCallExpression) {
      GrMethodCallExpression methodCall = (GrMethodCallExpression)element.getParent();

      GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(myProject);
      GrExpression expression = factory.createExpressionFromText(mySettings.getName(), null);
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
    return RefactoringBundle.message("introduce.parameter.command", UsageViewUtil.getDescriptiveName(myContext.methodToReplaceIn));
  }

  @NotNull
  @Override
  public Project getProject() {
    return myContext.project;
  }

  @Override
  public PsiMethod getMethodToReplaceIn() {
    return myContext.methodToReplaceIn;
  }

  @NotNull
  @Override
  public PsiMethod getMethodToSearchFor() {
    return myContext.methodToSearchFor;
  }

  @Override
  public IntroduceParameterData.ExpressionWrapper getParameterInitializer() {
    return myParameterInitializer;
  }

  @NotNull
  @Override
  public String getParameterName() {
    return mySettings.getName();
  }

  @Override
  public int getReplaceFieldsWithGetters() {
    return mySettings.replaceFieldsWithGetters();
  }

  @Override
  public boolean isDeclareFinal() {
    return mySettings.declareFinal();
  }

  @Override
  public boolean isGenerateDelegate() {
    return mySettings.generateDelegate();
  }

  @NotNull
  @Override
  public PsiType getForcedType() {
    final PsiType selectedType = mySettings.getSelectedType();
    if (selectedType != null) return selectedType;
    final PsiManager manager = PsiManager.getInstance(myProject);
    final GlobalSearchScope resolveScope = myContext.methodToReplaceIn.getResolveScope();
    return PsiType.getJavaLangObject(manager, resolveScope);
  }

  @NotNull
  @Override
  public TIntArrayList getParametersToRemove() {
    return mySettings.parametersToRemove();
  }
}
