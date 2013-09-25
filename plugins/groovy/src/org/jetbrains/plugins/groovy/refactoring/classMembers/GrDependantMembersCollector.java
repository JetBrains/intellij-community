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
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.classMembers.DependentMembersCollectorBase;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;

/**
 * @author Max Medvedev
 */
public class GrDependantMembersCollector extends DependentMembersCollectorBase<GrMember, PsiClass> {
  public GrDependantMembersCollector(PsiClass clazz, PsiClass superClass) {
    super(clazz, superClass);
  }

  @Override
  public void collect(GrMember member) {
    member.accept(new GrClassMemberReferenceVisitor(getClazz()) {
      @Override
      protected void visitClassMemberReferenceElement(GrMember classMember, GrReferenceElement ref) {
        if (!existsInSuperClass(classMember)) {
          myCollection.add(classMember);
        }
      }
    });
  }

  private boolean existsInSuperClass(PsiMember classMember) {
    if (getSuperClass() == null) return false;
    if (!(classMember instanceof PsiMethod)) return false;
    final PsiMethod method = ((PsiMethod)classMember);
    final PsiMethod methodBySignature = (getSuperClass()).findMethodBySignature(method, true);
    return methodBySignature != null;
  }
}
