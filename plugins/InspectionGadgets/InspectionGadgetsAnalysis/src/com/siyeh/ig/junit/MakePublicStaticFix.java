package com.siyeh.ig.junit;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

/**
* User: anna
* Date: 5/22/13
*/
class MakePublicStaticFix extends InspectionGadgetsFix {
  private final String myName;
  private final boolean myMakeStatic;

  public MakePublicStaticFix(final String name, final boolean makeStatic) {
    myName = name;
    myMakeStatic = makeStatic;
  }

  @Override
  protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
    final PsiElement element = descriptor.getPsiElement();
    if (element != null) {
      final PsiElement parent = element.getParent();
      if (parent instanceof PsiMember) {
        PsiUtil.setModifierProperty((PsiMember)parent, PsiModifier.PUBLIC, true);
        PsiUtil.setModifierProperty((PsiMember)parent, PsiModifier.STATIC, myMakeStatic);
      }
    }
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return "Make public/static";
  }
}
