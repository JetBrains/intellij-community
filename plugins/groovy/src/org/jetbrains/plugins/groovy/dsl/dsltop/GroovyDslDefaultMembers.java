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

package org.jetbrains.plugins.groovy.dsl.dsltop;

import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.dsl.GdslMembersHolderConsumer;
import org.jetbrains.plugins.groovy.dsl.holders.DelegatedMembersHolder;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

/**
 * @author ilyas
 */
@SuppressWarnings({"MethodMayBeStatic", "UnusedDeclaration"})
public class GroovyDslDefaultMembers implements GdslMembersProvider {

  /**
   * Find a class by its full-qulified name
   *
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
   * **********************************************************************************
   * Methods and properties of the GroovyDSL language
   * **********************************************************************************
   */

  public void delegatesTo(@Nullable PsiElement elem, GdslMembersHolderConsumer consumer) {
    if (elem instanceof PsiClass) {
      final PsiClass clazz = (PsiClass)elem;
      final DelegatedMembersHolder holder = new DelegatedMembersHolder();
      for (PsiMethod method : clazz.getAllMethods()) {
        if (!method.isConstructor()) holder.addMember(method);
      }
      for (PsiField field : clazz.getAllFields()) {
        holder.addMember(field);
      }
      consumer.addMemberHolder(holder);
    }
    else if (elem instanceof GrExpression) {
      GrExpression expr = (GrExpression)elem;
      final PsiType type = expr.getType();
      if (type instanceof PsiClassType) {
        PsiClassType ctype = (PsiClassType)type;
        delegatesTo(ctype.resolve(), consumer);
      }
    }
  }

  /**
   * Add a member to a context's ctype
   *
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
  public GrCall enclosingCall(String name, GdslMembersHolderConsumer consumer) {
    final PsiElement place = consumer.getPlace();
    if (place == null) return null;
    GrCall call = PsiTreeUtil.getParentOfType(place, GrCall.class, true);
    if (call == null) return null;
    while (call != null && !name.equals(getInvokedMethodName(call))) {
      call = PsiTreeUtil.getParentOfType(call, GrCall.class, true);
    }
    if (call == null) return null;

    final GrArgumentList argumentList = call.getArgumentList();
    if (argumentList != null) {
      for (GrExpression arg : argumentList.getExpressionArguments()) {
        if (arg instanceof GrClosableBlock && PsiTreeUtil.findCommonParent(place, arg) == arg) {
          return call;
        }
      }
    }

    if (call instanceof GrMethodCallExpression) {
      for (GrExpression arg : ((GrMethodCallExpression)call).getClosureArguments()) {
        if (arg instanceof GrClosableBlock && PsiTreeUtil.findCommonParent(place, arg) == arg) {
          return call;
        }
      }
    }
    return null;
  }

  @Nullable
  public PsiMethod enclosingMethod(GdslMembersHolderConsumer consumer) {
    final PsiElement place = consumer.getPlace();
    if (place == null) return null;
    return PsiTreeUtil.getParentOfType(place, PsiMethod.class, true);
  }

  @Nullable
  public PsiMember enclosingMember(GdslMembersHolderConsumer consumer) {
    final PsiElement place = consumer.getPlace();
    if (place == null) return null;
    final PsiMember member = PsiTreeUtil.getParentOfType(place, PsiMember.class, true);
    if (member instanceof PsiClass) return null;
    return member;
  }

  @Nullable
  public PsiClass enclosingClass(GdslMembersHolderConsumer consumer) {
    final PsiElement place = consumer.getPlace();
    if (place == null) return null;
    return PsiTreeUtil.getParentOfType(place, PsiClass.class, true);
  }

  @Nullable
  private static String getInvokedMethodName(GrCall call) {
    if (call instanceof GrMethodCall) {
      final GrExpression expr = ((GrMethodCall)call).getInvokedExpression();
      if (expr instanceof GrReferenceExpression) {
        return ((GrReferenceExpression)expr).getName();
      }
    }
    return null;
  }

}
