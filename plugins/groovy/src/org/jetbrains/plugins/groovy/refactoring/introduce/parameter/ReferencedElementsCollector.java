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
package org.jetbrains.plugins.groovy.refactoring.introduce.parameter;

import com.intellij.psi.PsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;

import java.util.ArrayList;
import java.util.List;

/**
* @author Medvedev Max
*/
class ReferencedElementsCollector extends GroovyRecursiveElementVisitor {

  private final List<PsiElement> myResult = new ArrayList<>();

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
