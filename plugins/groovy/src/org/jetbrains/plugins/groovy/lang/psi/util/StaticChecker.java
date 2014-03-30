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
package org.jetbrains.plugins.groovy.lang.psi.util;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GrQualifiedReference;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;

/**
 * @author Max Medvedev
 */
public class StaticChecker {
  public static boolean isStaticsOK(@NotNull PsiModifierListOwner member,
                                    @NotNull PsiElement place,
                                    @Nullable PsiElement resolveContext,
                                    boolean filterStaticAfterInstanceQualifier) {
    if (!(member instanceof PsiMember)) return true;

    if (!(place instanceof GrReferenceExpression)) return true;

    if (member instanceof PsiClass && PsiTreeUtil.isAncestor(member, place, false)) return true;

    GrExpression qualifier = ((GrReferenceExpression)place).getQualifierExpression();
    final PsiClass containingClass = getContainingClass((PsiMember)member);
    if (qualifier != null) {
      return checkQualified(member, place, filterStaticAfterInstanceQualifier, qualifier, containingClass);
    }
    else {
      return checkNonQualified(member, place, resolveContext, containingClass);
    }
  }

  private static boolean checkNonQualified(PsiModifierListOwner member,
                                           PsiElement place,
                                           PsiElement resolveContext,
                                           PsiClass containingClass) {
    if (containingClass == null) return true;
    if (member instanceof GrVariable && !(member instanceof GrField)) return true;
    if (member.hasModifierProperty(PsiModifier.STATIC)) return true;
    if (CommonClassNames.JAVA_LANG_OBJECT.equals(containingClass.getQualifiedName())) return true;

    if (resolveContext != null) {
      PsiElement stopAt = PsiTreeUtil.findCommonParent(place, resolveContext);
      while (place != null && place != stopAt && !(place instanceof GrMember)) {
        if (place instanceof PsiFile) break;
        if (place instanceof GrClosableBlock) return true;
        place = place.getParent();
      }
      if (place == null || place instanceof PsiFile || place == stopAt) return true;
      if (place instanceof GrTypeDefinition) {
        return !(((GrTypeDefinition)place).hasModifierProperty(PsiModifier.STATIC) ||
                 ((GrTypeDefinition)place).getContainingClass() == null);
      }
      return !((GrMember)place).hasModifierProperty(PsiModifier.STATIC);
    }
    else {
      while (place != null) {
        place = place.getParent();
        if (place instanceof PsiClass && InheritanceUtil.isInheritorOrSelf((PsiClass)place, containingClass, true)) return true;
        if (place instanceof GrClosableBlock) return true;
        if (place instanceof PsiMember && ((PsiMember)place).hasModifierProperty(PsiModifier.STATIC)) {
          return false;
        }
      }
      return true;
    }
  }

  private static boolean checkQualified(PsiModifierListOwner member,
                                        PsiElement place,
                                        boolean filterStaticAfterInstanceQualifier,
                                        GrExpression qualifier, PsiClass containingClass) {
    final boolean isStatic = member.hasModifierProperty(PsiModifier.STATIC);
    if (qualifier instanceof GrReferenceExpression) {
      if ("class".equals(((GrReferenceExpression)qualifier).getReferenceName())) {
        //invoke static members of class from A.class.foo()
        final PsiType type = qualifier.getType();
        if (type instanceof PsiClassType) {
          final PsiClass psiClass = ((PsiClassType)type).resolve();
          if (psiClass != null && CommonClassNames.JAVA_LANG_CLASS.equals(psiClass.getQualifiedName())) {
            final PsiType[] params = ((PsiClassType)type).getParameters();
            if (params.length == 1 && params[0] instanceof PsiClassType) {
              if (place.getManager().areElementsEquivalent(containingClass, ((PsiClassType)params[0]).resolve())) {
                return member.hasModifierProperty(PsiModifier.STATIC);
              }
            }
          }
        }
      }
      else if (PsiUtil.isThisOrSuperRef(qualifier)) {
        //static members may be invoked from this.<...>
        final boolean isInStatic = isInStaticContext((GrReferenceExpression)qualifier);
        if (PsiUtil.isThisReference(qualifier) && isInStatic) {
          return checkJavaLangClassMember(place, containingClass, member) || member.hasModifierProperty(PsiModifier.STATIC);
        }

        return !isStatic || !filterStaticAfterInstanceQualifier || CodeInsightSettings.getInstance().SHOW_STATIC_AFTER_INSTANCE;
      }

      PsiElement qualifierResolved = ((GrReferenceExpression)qualifier).resolve();
      if (qualifierResolved instanceof PsiClass || qualifierResolved instanceof PsiPackage) { //static context
        if (member instanceof PsiClass) {
          return true;
        }

        if (isStatic) {
          return true;
        }

        //non-physical method, e.g. gdk
        if (containingClass == null) {
          return isStatic;
        }
        if (checkJavaLangClassMember(place, containingClass, member)) return true;


        return false;
      }
    }

    //instance context
    if (member instanceof PsiClass) {
      return false;
    }
    return !isStatic || !filterStaticAfterInstanceQualifier || CodeInsightSettings.getInstance().SHOW_STATIC_AFTER_INSTANCE;
  }

  private static boolean checkJavaLangClassMember(PsiElement place, PsiClass containingClass, PsiModifierListOwner member) {
    if (containingClass == null) return false;
    //members from java.lang.Class can be invoked without ".class"
    final String qname = containingClass.getQualifiedName();
    if (qname != null && qname.startsWith("java.")) {
      if (CommonClassNames.JAVA_LANG_OBJECT.equals(qname)) {
        //special check for toString(). Only Class.toString() should be resolved here
        return !(member instanceof PsiMethod && "toString".equals(((PsiMethod)member).getName()));
      }
      else if (CommonClassNames.JAVA_LANG_CLASS.equals(qname)) {
        return true;
      }

      if (containingClass.isInterface()) {
        PsiClass javaLangClass =
          JavaPsiFacade.getInstance(place.getProject()).findClass(CommonClassNames.JAVA_LANG_CLASS, place.getResolveScope());
        if (javaLangClass != null && javaLangClass.isInheritor(containingClass, true)) {
          return true;
        }
      }
    }
    return false;
  }

  @Nullable
  private static PsiClass getContainingClass(PsiMember member) {
    PsiClass aClass = member.getContainingClass();

    if (aClass != null) return aClass;

    if (member instanceof GrGdkMethod && !member.hasModifierProperty(PsiModifier.STATIC)) {
      PsiMethod method = ((GrGdkMethod)member).getStaticMethod();
      PsiParameter[] parameters = method.getParameterList().getParameters();
      if (parameters.length > 0) {
        PsiType type = parameters[0].getType();
        if (type instanceof PsiClassType) {
          return ((PsiClassType)type).resolve();
        }
      }
    }
    return null;
  }

  public static boolean isInStaticContext(GrQualifiedReference refExpression) {
    PsiClass targetClass = null;
    if (PsiUtil.isThisReference(refExpression) && refExpression.getQualifier() != null) {
      targetClass = (PsiClass)((GrReferenceExpression)refExpression.getQualifier()).resolve();
    }
    return isInStaticContext(refExpression, targetClass);
  }

  public static boolean isInStaticContext(GrQualifiedReference refExpression, @Nullable PsiClass targetClass) {
    PsiElement qualifier = refExpression.getQualifier();
    if (qualifier != null && !PsiUtil.isThisOrSuperRef(refExpression)) {
      if (PsiUtil.isInstanceThisRef(qualifier) || PsiUtil.isSuperReference(qualifier)) {
        return false;
      }
      else if (PsiUtil.isThisReference(qualifier)) { //instance 'this' already is processed. So it static 'this'
        return true;
      }
      return qualifier instanceof GrReferenceExpression && ((GrReferenceExpression)qualifier).resolve() instanceof PsiClass;
    }


    if (PsiUtil.isSuperReference(refExpression)) return false;
    //this reference should be checked as all other refs


    PsiElement run = refExpression;
    while (run != null && run != targetClass) {
      if (targetClass == null && run instanceof PsiClass) return false;
      if (run instanceof PsiModifierListOwner && ((PsiModifierListOwner)run).hasModifierProperty(PsiModifier.STATIC)) return true;
      run = run.getParent();
    }
    return false;
  }
}
