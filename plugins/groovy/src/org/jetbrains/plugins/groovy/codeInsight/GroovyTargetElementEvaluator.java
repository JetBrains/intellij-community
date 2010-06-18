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

package org.jetbrains.plugins.groovy.codeInsight;

import com.intellij.codeInsight.TargetElementEvaluator;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrReferenceElementImpl;

/**
 * @author Maxim.Medvedev
 */
public class GroovyTargetElementEvaluator implements TargetElementEvaluator {
  public boolean includeSelfInGotoImplementation(@NotNull PsiElement element) {
    return false;
  }

  public PsiElement getElementByReference(PsiReference ref, int flags) {
    PsiElement sourceElement = ref.getElement();
    if (sourceElement == null) return null;


    if (sourceElement instanceof GrCodeReferenceElement) {
      GrNewExpression newExpr;

      if (sourceElement.getParent() instanceof GrNewExpression) {
        newExpr = (GrNewExpression)sourceElement.getParent();
      }
      else if (sourceElement.getParent().getParent() instanceof GrNewExpression) {//anonymous class declaration
        newExpr = (GrNewExpression)sourceElement.getParent().getParent();
      }
      else {
        return null;
      }

      final PsiMethod constructor = newExpr.resolveConstructor();
      final GrArgumentList argumentList = newExpr.getArgumentList();
      if (constructor != null &&
          argumentList != null &&
          argumentList.getNamedArguments().length != 0 &&
          argumentList.getExpressionArguments().length == 0) {
        if (constructor.getParameterList().getParametersCount() == 0) return constructor.getContainingClass();
      }

      return constructor;
    }

    if (sourceElement instanceof GrReferenceElementImpl) { // For Grails tags in GSP pager (e.g. <%  g.link()  %>)
      PsiElement resolve = ((GrReferenceElementImpl)sourceElement).resolve();
      if (resolve != null && !resolve.isPhysical()) {
        PsiElement navigationElement = resolve.getNavigationElement();
        if (navigationElement != resolve) {
          return navigationElement;
        }
      }
    }
    
    return null;
  }
}
