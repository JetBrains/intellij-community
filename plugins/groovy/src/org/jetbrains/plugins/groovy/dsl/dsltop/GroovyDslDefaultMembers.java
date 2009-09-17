/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jetbrains.plugins.groovy.dsl.dsltop;

import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.dsl.GdslMembersHolderConsumer;
import org.jetbrains.plugins.groovy.dsl.holders.DelegatedMembersHolder;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;

/**
 * @author ilyas
 */
public class GroovyDslDefaultMembers implements GdslTopLevelMembersProvider {
  
  /*************************************************************************************
   Methods and properties of the GroovyDSL language
   ************************************************************************************/

  public void delegatesTo(@Nullable PsiClass clazz, GdslMembersHolderConsumer consumer) {
    if (clazz != null) {
      final DelegatedMembersHolder holder = new DelegatedMembersHolder();
      for (PsiMethod method : clazz.getAllMethods()) {
        if (!method.isConstructor()) holder.addMember(method);
      }
      for (PsiField field : clazz.getAllFields()) {
        holder.addMember(field);
      }
      consumer.addMemberHolder(holder);
    }
  }

  /**
   * Find a class by its full-qulified name
   * @param fqn
   * @return
   */
  @Nullable
  public PsiClass findClass(String fqn, GdslMembersHolderConsumer consumer) {
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(consumer.getProject());
    final PsiClass clazz = facade.findClass(fqn, GlobalSearchScope.allScope(consumer.getProject()));
    return clazz;
  }


  /**
   * Add a member to a context's ctype
   * @param member
   */
  public PsiMember add(PsiMember member, GdslMembersHolderConsumer consumer) {
    final DelegatedMembersHolder holder = new DelegatedMembersHolder();
    holder.addMember(member);
    consumer.addMemberHolder(holder);
    return member;
  }

  /**
   * Returns enclosing method call of a given context's place
   */
  @Nullable
  public GrCallExpression getEnclosingCall(GdslMembersHolderConsumer consumer) {
    final PsiElement place = consumer.getPlace();
    return place == null ? null : PsiTreeUtil.getParentOfType(place, GrCallExpression.class);
  }
  
}
