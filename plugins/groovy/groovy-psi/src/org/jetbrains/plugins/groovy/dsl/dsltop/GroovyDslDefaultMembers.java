// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.dsl.dsltop;

import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.dsl.GdslMembersHolderConsumer;
import org.jetbrains.plugins.groovy.dsl.holders.NonCodeMembersHolder;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.CompletionProcessor;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ResolverProcessor;

@SuppressWarnings({"UnusedDeclaration"})
public final class GroovyDslDefaultMembers implements GdslMembersProvider {

  /**
   * Find a class by its full-qualified name
   */
  public @Nullable PsiClass findClass(String fqn, GdslMembersHolderConsumer consumer) {
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(consumer.getProject());
    return facade.findClass(fqn, GlobalSearchScope.allScope(consumer.getProject()));
  }

  /**
   * **********************************************************************************
   * Methods and properties of the GroovyDSL language
   * **********************************************************************************
   */

  public void delegatesTo(@Nullable PsiElement elem, GdslMembersHolderConsumer consumer) {
    if (elem instanceof PsiClass clazz) {
      final NonCodeMembersHolder holder = new NonCodeMembersHolder();

      if (clazz instanceof GrTypeDefinition context) {
        final PsiClassType type = JavaPsiFacade.getElementFactory(consumer.getProject()).createType(clazz);
        final ResolverProcessor processor = CompletionProcessor.createPropertyCompletionProcessor(clazz);
        ResolveUtil.processAllDeclarations(type, processor, ResolveState.initial(), context);
        for (GroovyResolveResult result : processor.getCandidates()) {
          final PsiElement element = result.getElement();
          if (element instanceof PsiMethod && !((PsiMethod)element).isConstructor() || element instanceof PsiField) {
            holder.addDeclaration(element);
          }
        }
      }
      else {
        for (PsiMethod method : clazz.getAllMethods()) {
          if (!method.isConstructor()) holder.addDeclaration(method);
        }
        for (PsiField field : clazz.getAllFields()) {
          holder.addDeclaration(field);
        }
      }
      consumer.addMemberHolder(holder);
    }
    else if (elem instanceof GrExpression expr) {
      final PsiType type = expr.getType();
      if (type instanceof PsiClassType ctype) {
        delegatesTo(ctype.resolve(), consumer);
      }
    }
  }

  /**
   * Add a member to a context's ctype
   */
  public PsiMember add(PsiMember member, GdslMembersHolderConsumer consumer) {
    final NonCodeMembersHolder holder = new NonCodeMembersHolder();
    holder.addDeclaration(member);
    consumer.addMemberHolder(holder);
    return member;
  }

  /**
   * Returns enclosing method call of a given context's place
   */
  public @Nullable GrCall enclosingCall(String name, GdslMembersHolderConsumer consumer) {
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
      for (GrExpression arg : call.getClosureArguments()) {
        if (arg instanceof GrClosableBlock && PsiTreeUtil.findCommonParent(place, arg) == arg) {
          return call;
        }
      }
    }
    return null;
  }

  public @Nullable PsiMethod enclosingMethod(GdslMembersHolderConsumer consumer) {
    final PsiElement place = consumer.getPlace();
    if (place == null) return null;
    return PsiTreeUtil.getParentOfType(place, PsiMethod.class, true);
  }

  public @Nullable PsiMember enclosingMember(GdslMembersHolderConsumer consumer) {
    final PsiElement place = consumer.getPlace();
    if (place == null) return null;
    final PsiMember member = PsiTreeUtil.getParentOfType(place, PsiMember.class, true);
    if (member instanceof PsiClass) return null;
    return member;
  }

  public @Nullable PsiClass enclosingClass(GdslMembersHolderConsumer consumer) {
    final PsiElement place = consumer.getPlace();
    if (place == null) return null;
    return PsiTreeUtil.getParentOfType(place, PsiClass.class, true);
  }

  private static @Nullable String getInvokedMethodName(GrCall call) {
    if (call instanceof GrMethodCall) {
      final GrExpression expr = ((GrMethodCall)call).getInvokedExpression();
      if (expr instanceof GrReferenceExpression) {
        return ((GrReferenceExpression)expr).getReferenceName();
      }
    }
    return null;
  }
}
