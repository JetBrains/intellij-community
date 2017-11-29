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
package org.jetbrains.plugins.groovy.codeInspection.confusing;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.psi.PsiDocCommentOwner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.impl.PsiImplUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;

/**
 * @author Max Medvedev
 */
public class GrDeprecatedAPIUsageInspection extends BaseInspection {
  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @Nls
  @NotNull
  public String getDisplayName() {
    return GroovyInspectionBundle.message("gr.deprecated.api.usage");
  }

  @NotNull
  @Override
  protected BaseInspectionVisitor buildVisitor() {
    return new BaseInspectionVisitor() {
      @Override
      public void visitReferenceExpression(@NotNull GrReferenceExpression ref) {
        super.visitReferenceExpression(ref);
        checkRef(ref);
      }

      @Override
      public void visitCodeReferenceElement(@NotNull GrCodeReferenceElement ref) {
        super.visitCodeReferenceElement(ref);
        checkRef(ref);
      }

      private void checkRef(GrReferenceElement ref) {
        PsiElement resolved = ref.resolve();
        if (isDeprecated(resolved)) {
          PsiElement toHighlight = getElementToHighlight(ref);
          registerError(toHighlight, GroovyBundle.message("0.is.deprecated", ref.getReferenceName()), LocalQuickFix.EMPTY_ARRAY,
                        ProblemHighlightType.LIKE_DEPRECATED);
        }
      }

      @NotNull
      public PsiElement getElementToHighlight(@NotNull GrReferenceElement refElement) {
        final PsiElement refNameElement = refElement.getReferenceNameElement();
        return refNameElement != null ? refNameElement : refElement;
      }


      private boolean isDeprecated(PsiElement resolved) {
        if (resolved instanceof PsiDocCommentOwner) {
          return ((PsiDocCommentOwner)resolved).isDeprecated();
        }
        if (resolved instanceof PsiModifierListOwner && PsiImplUtil.isDeprecatedByAnnotation((PsiModifierListOwner)resolved)) {
          return true;
        }
        return false;
      }
    };
  }
}
