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

/**
 * @author Max Medvedev
 */
public abstract class ExtractClosureProcessorBase extends BaseRefactoringProcessor {
  protected final ExtractClosureHelper myHelper;

  public ExtractClosureProcessorBase(@NotNull ExtractClosureHelper helper) {
    super(helper.getProject());
    myHelper = helper;
  }

  @NotNull
  @Override
  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
    return new UsageViewDescriptorAdapter() {
      @NotNull
      @Override
      public PsiElement[] getElements() {
        return new PsiElement[]{myHelper.getToSearchFor()};
      }

      @Override
      public String getProcessedElementsHeader() {
        return ExtractClosureHandler.EXTRACT_CLOSURE;
      }
    };
  }

  @Override
  protected String getCommandName() {
    return ExtractClosureHandler.EXTRACT_CLOSURE;
  }

  protected GrClosableBlock generateClosure() {
    StringBuilder buffer = new StringBuilder();

    buffer.append('{');
    for (String p : ExtractUtil.getParameterString(myHelper, true)) {
      buffer.append(p);
    }
    buffer.append("->\n");

    ExtractUtil.generateBody(myHelper, false, buffer);
    buffer.append('}');

    return GroovyPsiElementFactory.getInstance(myHelper.getProject()).createClosureFromText(buffer.toString(), myHelper.getOwner());
  }
}
