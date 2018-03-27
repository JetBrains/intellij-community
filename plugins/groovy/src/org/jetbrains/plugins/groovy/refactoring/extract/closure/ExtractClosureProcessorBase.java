/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.extract.closure;

import com.intellij.psi.PsiElement;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.refactoring.extract.ExtractUtil;
import org.jetbrains.plugins.groovy.refactoring.introduce.parameter.GrIntroduceParameterSettings;

/**
 * @author Max Medvedev
 */
public abstract class ExtractClosureProcessorBase extends BaseRefactoringProcessor {
  protected final GrIntroduceParameterSettings myHelper;
  private static final String EXTRACT_CLOSURE = "Extract closure";

  public ExtractClosureProcessorBase(@NotNull GrIntroduceParameterSettings helper) {
    super(helper.getProject());
    myHelper = helper;
  }

  @NotNull
  @Override
  protected UsageViewDescriptor createUsageViewDescriptor(@NotNull UsageInfo[] usages) {
    return new UsageViewDescriptorAdapter() {
      @NotNull
      @Override
      public PsiElement[] getElements() {
        return new PsiElement[]{myHelper.getToSearchFor()};
      }

      @Override
      public String getProcessedElementsHeader() {
        return EXTRACT_CLOSURE;
      }
    };
  }

  @NotNull
  @Override
  protected String getCommandName() {
    return EXTRACT_CLOSURE;
  }

  @NotNull
  public static GrClosableBlock generateClosure(@NotNull GrIntroduceParameterSettings helper) {
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
