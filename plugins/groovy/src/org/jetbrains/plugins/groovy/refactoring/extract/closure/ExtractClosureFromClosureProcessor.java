// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.extract.closure;

import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.IntroduceParameterRefactoring;
import com.intellij.refactoring.introduceParameter.ExternalUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;
import org.jetbrains.plugins.groovy.refactoring.extract.ExtractUtil;
import org.jetbrains.plugins.groovy.refactoring.introduce.parameter.GrIntroduceClosureParameterProcessor;
import org.jetbrains.plugins.groovy.refactoring.introduce.parameter.GrIntroduceParameterSettings;
import org.jetbrains.plugins.groovy.refactoring.introduce.parameter.GroovyIntroduceParameterUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class ExtractClosureFromClosureProcessor extends ExtractClosureProcessorBase {
  public ExtractClosureFromClosureProcessor(@NotNull GrIntroduceParameterSettings helper) {
    super(helper);
  }

  @Override
  protected boolean preprocessUsages(@NotNull Ref<UsageInfo[]> refUsages) {
    UsageInfo[] usagesIn = refUsages.get();
    MultiMap<PsiElement, String> conflicts = new MultiMap<>();

    if (!myHelper.generateDelegate()) {
      for (GrStatement statement : myHelper.getStatements()) {
        GroovyIntroduceParameterUtil.detectAccessibilityConflicts(statement, usagesIn, conflicts,
                                                                  myHelper.replaceFieldsWithGetters() !=
                                                                  IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE,
                                                                  myProject);
      }
    }
    return showConflicts(conflicts, usagesIn);
  }


  @Override
  protected void performRefactoring(UsageInfo @NotNull [] usages) {
    GrIntroduceClosureParameterProcessor.processExternalUsages(usages, myHelper, generateClosure(myHelper));
    GrIntroduceClosureParameterProcessor.processClosure(usages, myHelper);

    GrStatementOwner declarationOwner = GroovyRefactoringUtil.getDeclarationOwner(myHelper.getStatements()[0]);
    ExtractUtil.replaceStatement(declarationOwner, myHelper);
  }

  @Override
  protected UsageInfo @NotNull [] findUsages() {
    final GrVariable var = (GrVariable)myHelper.getToSearchFor();
    if (var != null) {
      final List<UsageInfo> result = new ArrayList<>();
      for (PsiReference ref : ReferencesSearch.search(var).asIterable()) {
        final PsiElement element = ref.getElement();
        if (element.getLanguage() != GroovyLanguage.INSTANCE) {
          result.add(new OtherLanguageUsageInfo(ref));
          continue;
        }

        final GrCall call = GroovyRefactoringUtil.getCallExpressionByMethodReference(element);
        if (call == null) continue;

        result.add(new ExternalUsageInfo(element));
      }
      return result.toArray(UsageInfo.EMPTY_ARRAY);
    }
    return UsageInfo.EMPTY_ARRAY;
  }
}

