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
package org.jetbrains.plugins.groovy.unwrap;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;

import java.util.List;

public class GroovyElseRemover extends GroovyElseUnwrapperBase {
  public GroovyElseRemover() {
    super(CodeInsightBundle.message("remove.else"));
  }

  @Override
  public PsiElement collectAffectedElements(@NotNull PsiElement e, @NotNull List<PsiElement> toExtract) {
    super.collectAffectedElements(e, toExtract);
    return ((GrIfStatement)e.getParent()).getElseBranch();
  }

  @Override
  protected void unwrapElseBranch(GrStatement branch, PsiElement parent, Context context) throws IncorrectOperationException {
    if (branch instanceof GrIfStatement) {
      deleteSelectedElseIf((GrIfStatement)branch, context);
    }
    else {
      context.delete(branch);
    }
  }

  private static void deleteSelectedElseIf(GrIfStatement selectedBranch, Context context) throws IncorrectOperationException {
    GrIfStatement parentIf = (GrIfStatement)selectedBranch.getParent();
    GrStatement childElse = selectedBranch.getElseBranch();

    if (childElse == null) {
      context.delete(selectedBranch);
      return;
    }

    context.setElseBranch(parentIf, childElse);
  }
}
