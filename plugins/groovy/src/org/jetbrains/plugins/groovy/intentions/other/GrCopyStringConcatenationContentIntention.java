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
package org.jetbrains.plugins.groovy.intentions.other;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;

/**
 * @author Max Medvedev
 */
public class GrCopyStringConcatenationContentIntention extends Intention {
  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull Project project, Editor editor) throws IncorrectOperationException {
    final StringBuilder buffer = new StringBuilder();
    getValue(element, buffer);

    final Transferable contents = new StringSelection(buffer.toString());
    CopyPasteManager.getInstance().setContents(contents);
  }

  private static void getValue(PsiElement element, StringBuilder buffer) {
    if (element instanceof GrLiteral) {
      buffer.append(((GrLiteral)element).getValue());
    }
    else if (element instanceof GrBinaryExpression) {
      getValue(((GrBinaryExpression)element).getLeftOperand(), buffer);
      getValue(((GrBinaryExpression)element).getRightOperand(), buffer);
    }
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return GrCopyStringConcatenationPredicate.INSTANCE;
  }
}
