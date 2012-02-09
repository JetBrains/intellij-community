/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.refactoring.util.usageInfo.DefaultConstructorImplicitUsageInfo;
import com.intellij.refactoring.util.usageInfo.NoConstructorClassUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.containers.MultiMap;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Maxim.Medvedev
 */
public class GrIntroduceParameterProcessor extends BaseRefactoringProcessor implements IntroduceParameterData {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.refactoring.introduce.parameter.GrIntroduceParameterProcessor");

  private final GrIntroduceExpressionSettings mySettings;
  private IntroduceParameterData.ExpressionWrapper myParameterInitializer;

  public GrIntroduceParameterProcessor(GrIntroduceExpressionSettings settings) {
    super(settings.getProject());
    this.mySettings = settings;

    LOG.assertTrue(mySettings.getToReplaceIn() instanceof GrMethod);
    LOG.assertTrue(mySettings.getToSearchFor() instanceof PsiMethod);
    myParameterInitializer = new GrExpressionWrapper(mySettings.getExpression());
  }

  @NotNull
  @Override
  protected UsageViewDescriptor createUsageViewDescriptor(final UsageInfo[] usages) {
    return new UsageViewDescriptorAdapter() {
      @NotNull
      @Override
      public PsiElement[] getElements() {
        return new PsiElement[]{mySettings.getToSearchFor()};
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
      GroovyIntroduceParameterUtil.detectAccessibilityConflicts(mySettings.getExpression(), usagesIn, conflicts,
                                                                mySettings.replaceFieldsWithGetters() != IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE,
                                                                myProject
      );
    }

    final GrMethod toReplaceIn = (GrMethod)mySettings.getToReplaceIn();
    if (mySettings.getExpression() != null && !toReplaceIn.hasModifierProperty(PsiModifier.PRIVATE)) {
      final AnySupers anySupers = new AnySupers();
      mySettings.getExpression().accept(anySupers);
      if (anySupers.isResult()) {
        for (UsageInfo usageInfo : usagesIn) {
          if (!(usageInfo.getElement() instanceof PsiMethod) && !(usageInfo instanceof InternalUsageInfo)) {
            if (!PsiTreeUtil.isAncestor(toReplaceIn.getContainingClass(), usageInfo.getElement(), false)) {
              conflicts.putValue(mySettings.getExpression(),
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

  @NotNull
  @Override
  protected UsageInfo[] findUsages() {
    ArrayList<UsageInfo> result = new ArrayList<UsageInfo>();

    final PsiMethod toSearchFor = ((PsiMethod)mySettings.getToSearchFor());

    if (!mySettings.generateDelegate()) {
      Collection<PsiReference> refs =
        MethodReferencesSearch.search(toSearchFor, GlobalSearchScope.projectScope(myProject), true).findAll();

      for (PsiReference ref1 : refs) {
        PsiElement ref = ref1.getElement();
        if (ref instanceof PsiMethod && ((PsiMethod)ref).isConstructor()) {
          DefaultConstructorImplicitUsageInfo implicitUsageInfo =
            new DefaultConstructorImplicitUsageInfo((PsiMethod)ref, ((PsiMethod)ref).getContainingClass(), toSearchFor);
          result.add(implicitUsageInfo);
        }
        else if (ref instanceof PsiClass) {
          result.add(new NoConstructorClassUsageInfo((PsiClass)ref));
        }
        else if (!PsiTreeUtil.isAncestor(mySettings.getToReplaceIn(), ref, false)) {
          result.add(new ExternalUsageInfo(ref));
        }
        else {
          result.add(new ChangedMethodCallInfo(ref));
        }
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

    Collection<PsiMethod> overridingMethods = OverridingMethodsSearch.search(toSearchFor, true).findAll();

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

    // Changing external occurrences (the tricky part)

    IntroduceParameterUtil.processUsages(usages, this);

    final GrMethod toReplaceIn = (GrMethod)mySettings.getToReplaceIn();
    final PsiMethod toSearchFor = (PsiMethod)mySettings.getToSearchFor();

    final boolean methodsToProcessAreDifferent = toReplaceIn != toSearchFor;
    if (mySettings.generateDelegate()) {
      GroovyIntroduceParameterUtil.generateDelegate(toReplaceIn, myParameterInitializer, myProject);
      if (methodsToProcessAreDifferent) {
        final GrMethod method = GroovyIntroduceParameterUtil.generateDelegate(toSearchFor, myParameterInitializer, myProject);
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
      new FieldConflictsResolver(mySettings.getName(), toReplaceIn.getBlock());
    IntroduceParameterUtil.changeMethodSignatureAndResolveFieldConflicts(new UsageInfo(toReplaceIn), usages, this);
    if (methodsToProcessAreDifferent) {
      IntroduceParameterUtil.changeMethodSignatureAndResolveFieldConflicts(new UsageInfo(toSearchFor), usages, this);
    }

    // Replacing expression occurrences
    for (UsageInfo usage : usages) {
      if (usage instanceof ChangedMethodCallInfo) {
        PsiElement element = usage.getElement();

        GroovyIntroduceParameterUtil.processChangedMethodCall(element, mySettings, myProject);
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

    final GrVariable var = mySettings.getVar();
    if (var != null && mySettings.removeLocalVariable()) {
      var.delete();
    }
    fieldConflictsResolver.fix();
  }

  @Override
  protected String getCommandName() {
    return RefactoringBundle.message("introduce.parameter.command", UsageViewUtil.getDescriptiveName(mySettings.getToReplaceIn()));
  }

  @NotNull
  @Override
  public Project getProject() {
    return mySettings.getProject();
  }

  @Override
  public PsiMethod getMethodToReplaceIn() {
    return (PsiMethod)mySettings.getToReplaceIn();
  }

  @NotNull
  @Override
  public PsiMethod getMethodToSearchFor() {
    return (PsiMethod)mySettings.getToSearchFor();
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
    final GlobalSearchScope resolveScope = mySettings.getToReplaceIn().getResolveScope();
    return PsiType.getJavaLangObject(manager, resolveScope);
  }

  @NotNull
  @Override
  public TIntArrayList getParametersToRemove() {
    return mySettings.parametersToRemove();
  }
}
