/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.annotator;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.PsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.List;

/**
* Created by Max Medvedev on 21/03/14
*/
class GrDeclarationHighlightingVisitor extends GroovyRecursiveElementVisitor {

  private final List<HighlightInfo> myInfos;

  public GrDeclarationHighlightingVisitor(List<HighlightInfo> collector) {
    myInfos = collector;
  }

  @Override
  public void visitReferenceExpression(GrReferenceExpression referenceExpression) {
    super.visitReferenceExpression(referenceExpression);
    visit(referenceExpression);
  }

  @Override
  public void visitCodeReferenceElement(GrCodeReferenceElement refElement) {
    super.visitCodeReferenceElement(refElement);
    visit(refElement);
  }

  @Override
  public void visitVariable(GrVariable variable) {
    super.visitVariable(variable);

    if (PsiUtil.isLocalVariable(variable) || variable instanceof GrParameter) {
      final TextAttributesKey attribute = GrHighlightUtil.getDeclarationHighlightingAttribute(variable, null);
      if (attribute != null) {
        final PsiElement nameElement = variable.getNameIdentifierGroovy();
        addInfo(attribute, nameElement);
      }
    }
  }

  private void visit(GrReferenceElement element) {
    ProgressManager.checkCanceled();
    final PsiElement resolved = element.resolve();
    final TextAttributesKey attribute = GrHighlightUtil.getDeclarationHighlightingAttribute(resolved, element);
    if (attribute != null) {
      final PsiElement refNameElement = GrHighlightUtil.getElementToHighlight(element);
      addInfo(attribute, refNameElement);
    }
  }

  private void addInfo(TextAttributesKey attribute, PsiElement nameElement) {
    assert myInfos != null;
    HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.INFORMATION).range(nameElement).needsUpdateOnTyping(false).textAttributes(attribute).create();
    if (info != null) {
      myInfos.add(info);
    }
  }
}
