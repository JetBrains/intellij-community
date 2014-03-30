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
import com.intellij.psi.PsiClassType;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.refactoring.classMembers.AbstractMemberInfoStorage;
import com.intellij.refactoring.classMembers.MemberInfoBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrExtendsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrImplementsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

import java.util.ArrayList;

/**
 * @author Max Medvedev
 */
public class GrMemberInfoStorage extends AbstractMemberInfoStorage<GrMember, PsiClass, GrMemberInfo> {
  public GrMemberInfoStorage(GrTypeDefinition aClass, MemberInfoBase.Filter<GrMember> memberInfoFilter) {
    super(aClass, memberInfoFilter);
  }

  @Override
  protected boolean isInheritor(PsiClass baseClass, PsiClass aClass) {
    return aClass.isInheritor(baseClass, true);
  }

  @Override
  protected void extractClassMembers(PsiClass aClass, ArrayList<GrMemberInfo> temp) {
    GrMemberInfo.extractClassMembers(aClass, temp, myFilter, false);
  }

  @Override
  protected boolean memberConflict(GrMember member1, GrMember member) {
    if (member instanceof GrMethod && member1 instanceof GrMethod) {
      return MethodSignatureUtil.areSignaturesEqual((GrMethod)member, (GrMethod)member1);
    }
    else if (member instanceof GrField && member1 instanceof GrField ||
             member instanceof GrTypeDefinition && member1 instanceof GrTypeDefinition) {
      return member.getName().equals(member1.getName());
    }

    return false;
  }

  @Override
  protected void buildSubClassesMap(PsiClass aClass) {
    if (aClass instanceof GrTypeDefinition) {
      final GrExtendsClause extendsList = ((GrTypeDefinition)aClass).getExtendsClause();
      if (extendsList != null) {
        buildSubClassesMapForList(extendsList.getReferencedTypes(), (GrTypeDefinition)aClass);
      }

      final GrImplementsClause implementsList = ((GrTypeDefinition)aClass).getImplementsClause();
      if (implementsList != null) {
        buildSubClassesMapForList(implementsList.getReferencedTypes(), (GrTypeDefinition)aClass);
      }
    }
  }

  private void buildSubClassesMapForList(final PsiClassType[] classesList, GrTypeDefinition aClass) {
    for (int i = 0; i < classesList.length; i++) {
      PsiClassType element = classesList[i];
      PsiClass resolved = element.resolve();
      if (resolved instanceof GrTypeDefinition) {
        GrTypeDefinition superClass = (GrTypeDefinition)resolved;
        getSubclasses(superClass).add(aClass);
        buildSubClassesMap(superClass);
      }
    }
  }
}
