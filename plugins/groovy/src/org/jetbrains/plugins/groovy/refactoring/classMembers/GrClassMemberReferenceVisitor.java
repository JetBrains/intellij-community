/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.classMembers;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author Max Medvedev
 */
public abstract class GrClassMemberReferenceVisitor extends GroovyRecursiveElementVisitor {
  private final PsiClass myClass;

  public GrClassMemberReferenceVisitor(@NotNull PsiClass aClass) {
    myClass = aClass;
  }

  @Override
  public void visitReferenceExpression(GrReferenceExpression ref) {
    GrExpression qualifier = ref.getQualifier();

    if (qualifier != null && !(PsiUtil.isThisOrSuperRef(qualifier))) {
      qualifier.accept(this);

      if (!(qualifier instanceof GrReferenceExpression) || !(((GrReferenceExpression) qualifier).resolve() instanceof PsiClass)) {
        return;
      }
    }

    PsiElement resolved = ref.resolve();
    if (resolved instanceof GrMember) {
      PsiClass containingClass = ((GrMember)resolved).getContainingClass();
      if (isPartOf(myClass, containingClass)) {
        visitClassMemberReferenceElement((GrMember)resolved, ref);
      }
    }
  }

  @Override
  public void visitCodeReferenceElement(GrCodeReferenceElement reference) {
    PsiElement referencedElement = reference.resolve();
    if (referencedElement instanceof GrTypeDefinition) {
      final GrTypeDefinition referencedClass = (GrTypeDefinition)referencedElement;
      if (PsiTreeUtil.isAncestor(myClass, referencedElement, true) ||
          isPartOf(myClass, referencedClass.getContainingClass())) {
        visitClassMemberReferenceElement((GrMember)referencedElement, reference);
      }
    }
  }

  private static boolean isPartOf(@NotNull PsiClass aClass, @Nullable PsiClass containingClass) {
    if (containingClass == null) return false;
    return aClass.equals(containingClass) || aClass.isInheritor(containingClass, true);
  }

  protected abstract void visitClassMemberReferenceElement(GrMember resolved, GrReferenceElement ref);
}
