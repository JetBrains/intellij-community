// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.geb;

import com.intellij.pom.PomDeclarationSearcher;
import com.intellij.pom.PomTarget;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;

import java.util.Map;

/**
 * @author Sergey Evdokimov
 */
public class GebContentDeclarationSearcher extends PomDeclarationSearcher {
  @Override
  public void findDeclarationsAt(@NotNull PsiElement element, int offsetInElement, Consumer<PomTarget> consumer) {
    PsiElement grCall = element.getParent();
    if (!(grCall instanceof GrMethodCall)) return;

    PsiElement grClosure = grCall.getParent();
    if (!(grClosure instanceof GrClosableBlock)) return;

    PsiElement contentField = grClosure.getParent();
    if (!(contentField instanceof GrField)) return;

    GrField field = (GrField)contentField;
    if (!"content".equals(field.getName()) || !field.hasModifierProperty(PsiModifier.STATIC)) return;

    PsiClass containingClass = field.getContainingClass();
    if (!InheritanceUtil.isInheritor(containingClass, "geb.Page")
        && !InheritanceUtil.isInheritor(containingClass, "geb.Module")) return;

    Map<String, PsiMember> contentElements = GebUtil.getContentElements(containingClass);

    for (PsiMember contentElement : contentElements.values()) {
      if (contentElement.getNavigationElement() == element) {
        consumer.consume(contentElement);
        return;
      }
    }
  }

}
