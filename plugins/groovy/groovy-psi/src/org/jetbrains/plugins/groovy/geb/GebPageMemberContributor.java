// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.geb;

import com.intellij.psi.*;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ClassUtil;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.Map;

/**
 * @author Sergey Evdokimov
 */
public class GebPageMemberContributor extends NonCodeMembersContributor {

  @Override
  protected String getParentClassName() {
    return "geb.Page";
  }

  @Override
  public void processDynamicElements(@NotNull PsiType qualifierType,
                                     PsiClass aClass,
                                     @NotNull PsiScopeProcessor processor,
                                     @NotNull PsiElement place,
                                     @NotNull ResolveState state) {
    if (!ResolveUtil.shouldProcessProperties(processor.getHint(ElementClassHint.KEY))) return;

    PsiElement grCall = place.getParent();
    if (grCall instanceof GrMethodCall) {
      PsiElement grClosure = grCall.getParent();
      if (grClosure instanceof GrClosableBlock) {
        PsiElement contentField = grClosure.getParent();
        if (contentField instanceof GrField) {
          GrField f = (GrField)contentField;
          if ("content".equals(f.getName()) && f.hasModifierProperty(PsiModifier.STATIC) && f.getContainingClass() == aClass) {
            Map<String, PsiMember> elements = GebUtil.getContentElements(aClass);
            for (PsiMember element : elements.values()) {
              if (element.getNavigationElement() == place) {
                return; // Don't resolve definition.
              }
            }
          }
        }
      }
    }

    processPageElements(processor, aClass, state);
  }

  public static boolean processPageElements(PsiScopeProcessor processor,
                                            @NotNull PsiClass pageClass,
                                            ResolveState state) {
    Map<String, PsiClass> supers = ClassUtil.getSuperClassesWithCache(pageClass);
    String nameHint = ResolveUtil.getNameHint(processor);

    for (PsiClass psiClass : supers.values()) {
      Map<String, PsiMember> contentElements = GebUtil.getContentElements(psiClass);

      if (nameHint == null) {
        for (Map.Entry<String, PsiMember> entry : contentElements.entrySet()) {
          if (!processor.execute(entry.getValue(), state)) return false;
        }
      }
      else {
        PsiMember element = contentElements.get(nameHint);
        if (element != null) {
          return processor.execute(element, state);
        }
      }
    }

    return true;
  }
}
