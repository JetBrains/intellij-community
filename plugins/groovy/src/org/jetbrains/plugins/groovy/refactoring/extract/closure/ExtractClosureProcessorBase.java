// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.extract.closure;

import com.intellij.psi.PsiElement;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.refactoring.extract.ExtractUtil;
import org.jetbrains.plugins.groovy.refactoring.introduce.parameter.GrIntroduceParameterSettings;

/**
 * @author Max Medvedev
 */
public abstract class ExtractClosureProcessorBase extends BaseRefactoringProcessor {
  protected final GrIntroduceParameterSettings myHelper;

  public ExtractClosureProcessorBase(@NotNull GrIntroduceParameterSettings helper) {
    super(helper.getProject());
    myHelper = helper;
  }

  @Override
  protected @NotNull UsageViewDescriptor createUsageViewDescriptor(UsageInfo @NotNull [] usages) {
    return new UsageViewDescriptorAdapter() {
      @Override
      public PsiElement @NotNull [] getElements() {
        return new PsiElement[]{myHelper.getToSearchFor()};
      }

      @Override
      public String getProcessedElementsHeader() {
        return GroovyBundle.message("header.extract.closure");
      }
    };
  }

  @Override
  protected @NotNull String getCommandName() {
    return GroovyBundle.message("extract.closure.command.name");
  }

  public static @NotNull GrClosableBlock generateClosure(@NotNull GrIntroduceParameterSettings helper) {
    StringBuilder buffer = new StringBuilder();

    buffer.append("{ ");

    final String[] params = ExtractUtil.getParameterString(helper, true);
    if (params.length > 0) {
      for (String p : params) {
        buffer.append(p);
      }
      buffer.append("->");
    }
    if (helper.getStatements().length > 1) {
      buffer.append('\n');
    }

    ExtractUtil.generateBody(helper, false, buffer, helper.isForceReturn());
    buffer.append(" }");

    return GroovyPsiElementFactory.getInstance(helper.getProject()).createClosureFromText(buffer.toString(), helper.getToReplaceIn());
  }
}
