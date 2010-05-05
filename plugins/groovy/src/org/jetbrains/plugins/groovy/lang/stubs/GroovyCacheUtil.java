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
package org.jetbrains.plugins.groovy.lang.stubs;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GrClassSubstitutor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrReferenceList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrAnnotatedMemberIndex;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrAnonymousClassIndex;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrDirectInheritorsIndex;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author ilyas
 */
public abstract class GroovyCacheUtil {

  @NotNull
  public static PsiMember[] getAnnotatedMemberCandidates(PsiClass clazz, GlobalSearchScope scope) {
    final String name = clazz.getName();
    if (name == null) return GrMember.EMPTY_ARRAY;
    final Collection<PsiMember> members = StubIndex.getInstance().get(GrAnnotatedMemberIndex.KEY, name, clazz.getProject(), scope);
    return members.toArray(new PsiMember[members.size()]);
  }

  @NotNull
  public static PsiClass[] getDeriverCandidates(PsiClass clazz, GlobalSearchScope scope) {
    final String name = clazz.getName();
    if (name == null) return GrTypeDefinition.EMPTY_ARRAY;
    final ArrayList<PsiClass> inheritors = new ArrayList<PsiClass>();
    final Collection<GrReferenceList> refLists = StubIndex.getInstance().get(GrDirectInheritorsIndex.KEY, name, clazz.getProject(), scope);
    for (GrReferenceList list : refLists) {
      final PsiElement parent = list.getParent();
      if (parent instanceof GrTypeDefinition) {
        inheritors.add(GrClassSubstitutor.getSubstitutedClass(((GrTypeDefinition)parent)));
      }
    }
    final Collection<GrAnonymousClassDefinition> classes =
      StubIndex.getInstance().get(GrAnonymousClassIndex.KEY, name, clazz.getProject(), scope);
    for (GrAnonymousClassDefinition aClass : classes) {
      inheritors.add(aClass);
    }
    return inheritors.toArray(new PsiClass[inheritors.size()]);
  }


}
