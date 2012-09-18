/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless re.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.codeInspection.declaration;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.annotator.intentions.GrModifierFix;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;

/**
 * @author Max Medvedev
 */
public class GrModifierLocalFix implements LocalQuickFix {
  private final String myModifier;
  private final boolean myDoSet;
  private final String myName;

  public GrModifierLocalFix(GrMember member, String modifier, boolean showClassName, boolean doSet) {

    myModifier = modifier;
    myDoSet = doSet;

    if (showClassName) {
      final PsiClass containingClass = member.getContainingClass();
      String containingClassName;
      if (containingClass != null) {
        containingClassName = containingClass.getName() + ".";
      }
      else {
        containingClassName = "";
      }

      myName = containingClassName + member.getName();
    }
    else {
      myName = member.getName();
    }
  }


  @NotNull
  @Override
  public String getName() {
    String modifierText = GrModifierFix.toPresentableText(myModifier);

    if (myDoSet) {
      return GroovyBundle.message("change.modifier", myName, modifierText);
    }
    else {
      return GroovyBundle.message("change.modifier.not", myName, modifierText);
    }
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return GroovyBundle.message("change.modifier.family.name");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    GrMember member = PsiTreeUtil.getParentOfType(element, GrMember.class);
    assert member != null;
    GrModifierList list = member.getModifierList();
    assert list != null : member.getText();
    list.setModifierProperty(myModifier, myDoSet);
  }
}
