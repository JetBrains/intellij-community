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

package org.jetbrains.plugins.groovy.refactoring;

import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author Maxim.Medvedev
 */
public class GroovyChangeContextUtil {
  private static final Key<PsiClass> QUALIFIER_CLASS_KEY = Key.create("QUALIFIER_CLASS_KEY");
  private static final Key<PsiClass> REF_TO_CLASS = Key.create("REF_TO_CLASS");
  private static final Key<PsiMember> REF_TO_MEMBER = Key.create("REF_TO_MEMBER");
  private static final Key<Object> KEY_ENCODED = Key.create("KEY_ENCODED");

  private GroovyChangeContextUtil() {
  }

  public static void encodeContextInfo(PsiElement element) {
    encodeContextInfo(element, element);
  }

  public static void encodeContextInfo(PsiElement element, PsiElement scope) {
    if (!(element instanceof GroovyPsiElement)) return;
    if (PsiUtil.isThisReference(element)) {
      GrReferenceExpression thisExpr = (GrReferenceExpression)element;
      final PsiClass containingClass = PsiTreeUtil.getParentOfType(thisExpr, PsiClass.class);
      element.putCopyableUserData(KEY_ENCODED, KEY_ENCODED);
      thisExpr.putCopyableUserData(QUALIFIER_CLASS_KEY, containingClass);
    }
    else if (element instanceof GrReferenceExpression) {
      GrReferenceExpression refExpr = (GrReferenceExpression)element;
      final GrExpression qualifier = refExpr.getQualifierExpression();
      if (qualifier == null) {
        PsiElement refElement = refExpr.resolve();
        element.putCopyableUserData(KEY_ENCODED, KEY_ENCODED);
        if (refElement != null && !PsiTreeUtil.isContextAncestor(scope, refElement, false)) {
          if (refElement instanceof GrAccessorMethod) refElement = ((GrAccessorMethod)refElement).getProperty();
          if (refElement instanceof PsiClass) {
            refExpr.putCopyableUserData(REF_TO_CLASS, (PsiClass)refElement);
          }
          else if (refElement instanceof PsiMember) {
            refExpr.putCopyableUserData(REF_TO_MEMBER, (PsiMember)refElement);
          }
        }
      }
    }
    else if (element instanceof GrCodeReferenceElement) {
      final PsiElement resolvedElement = ((GrCodeReferenceElement)element).resolve();
      element.putCopyableUserData(KEY_ENCODED, KEY_ENCODED);
      if (resolvedElement instanceof PsiClass && !PsiTreeUtil.isContextAncestor(scope, resolvedElement, false)) {
        element.putCopyableUserData(REF_TO_CLASS, (PsiClass)resolvedElement);
      }
    }

    for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      encodeContextInfo(child, scope);
    }

  }

  public static void decodeContextInfo(PsiElement element, @Nullable PsiClass thisClass, @Nullable GrExpression thisAccessExpr) {
    if (!(element instanceof GroovyPsiElement)) return;
    for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      decodeContextInfo(child, thisClass, thisAccessExpr);
    }
    if (element.getCopyableUserData(KEY_ENCODED) != null) {
      element.putCopyableUserData(KEY_ENCODED, null);
      final PsiManager manager = element.getManager();
      if (PsiUtil.isThisReference(element)) {
        final PsiClass thisQualClass = element.getCopyableUserData(QUALIFIER_CLASS_KEY);
        element.putCopyableUserData(QUALIFIER_CLASS_KEY, null);
        if (thisAccessExpr != null && !manager.areElementsEquivalent(thisClass, thisQualClass)) {
          element.replace(thisAccessExpr);
          return;
        }
      }

      else if (element instanceof GrReferenceExpression) {
        final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(element.getProject());
        final GrReferenceExpression refExpr = (GrReferenceExpression)element;
        final PsiElement resolvedElement = refExpr.resolve();
        final PsiMember memberRef = refExpr.getCopyableUserData(REF_TO_MEMBER);
        refExpr.putCopyableUserData(REF_TO_MEMBER, null);
        if (memberRef != null && memberRef.isValid()) {
          final PsiClass memberClass = memberRef.getContainingClass();
          if (memberClass != null) {
            if (memberRef.hasModifierProperty(PsiModifier.STATIC)) {
              if (!manager.areElementsEquivalent(memberRef, resolvedElement)) {
                final PsiElement qualifier = refExpr.getQualifier();
                if (!(qualifier instanceof GrReferenceExpression)) {
                  refExpr.setQualifier(factory.createReferenceExpressionFromText(memberClass.getQualifiedName()));
                  JavaCodeStyleManager.getInstance(manager.getProject()).shortenClassReferences(refExpr.getQualifier());
                  return;
                }
              }
            }
            else if (thisAccessExpr instanceof GrReferenceExpression) {
              final PsiElement qualifier = refExpr.getQualifier();
              if (!(qualifier instanceof GrReferenceExpression)) {
                refExpr.setQualifier(thisAccessExpr);
                return;
              }
            }
          }
        }
      }

      PsiClass refClass = element.getCopyableUserData(REF_TO_CLASS);
      element.putCopyableUserData(REF_TO_CLASS, null);

      if (refClass != null && refClass.isValid()) {
        final PsiReference ref = element.getReference();
        if (ref != null) {
          ref.bindToElement(refClass);
        }
      }
    }
  }

  public static void clearContextInfo(PsiElement scope) {
    scope.putCopyableUserData(QUALIFIER_CLASS_KEY, null);
    scope.putCopyableUserData(REF_TO_CLASS, null);
    scope.putCopyableUserData(REF_TO_MEMBER, null);
    for (PsiElement child = scope.getFirstChild(); child != null; child = child.getNextSibling()) {
      clearContextInfo(child);
    }
  }
}
