// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.bugs;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.*;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;

/**
 * @author Max Medvedev
 */
public class GrModifierFix extends GroovyFix {
  public static final Function<ProblemDescriptor, PsiModifierList> MODIFIER_LIST = descriptor -> {
    final PsiElement element = descriptor.getPsiElement();
    assert element instanceof PsiModifierList : element;
    return (PsiModifierList)element;
  };

  public static final Function<ProblemDescriptor, PsiModifierList> MODIFIER_LIST_OWNER = descriptor -> {
    final PsiElement element = descriptor.getPsiElement();
    assert element instanceof PsiModifierListOwner : element;
    return ((PsiModifierListOwner)element).getModifierList();
  };

  public static final Function<ProblemDescriptor, PsiModifierList> MODIFIER_LIST_CHILD = descriptor -> {
    final PsiElement element = descriptor.getPsiElement().getParent();
    assert element instanceof PsiModifierList : element;
    return (PsiModifierList)element;
  };

  private final String myModifier;
  private final @IntentionName String myText;
  private final boolean myDoSet;
  private final Function<? super ProblemDescriptor, ? extends PsiModifierList> myModifierListProvider;

  public GrModifierFix(@NotNull GrVariable member,
                       @GrModifier.GrModifierConstant String modifier,
                       boolean doSet,
                       @NotNull Function<? super ProblemDescriptor, ? extends PsiModifierList> modifierListProvider) {
    this(initText(doSet, member.getName(), modifier), modifier, doSet, modifierListProvider);
  }

  public GrModifierFix(@NotNull PsiMember member,
                       @GrModifier.GrModifierConstant String modifier,
                       boolean showContainingClass,
                       boolean doSet,
                       @NotNull Function<? super ProblemDescriptor, ? extends PsiModifierList> modifierListProvider) {
    this(initText(doSet, getMemberName(member, showContainingClass), modifier), modifier, doSet, modifierListProvider);
  }

  public GrModifierFix(@IntentionName @NotNull String text,
                       @GrModifier.GrModifierConstant String modifier,
                       boolean doSet,
                       @NotNull Function<? super ProblemDescriptor, ? extends PsiModifierList> modifierListProvider) {
    myText = text;
    myModifier = modifier;
    myModifierListProvider = modifierListProvider;
    myDoSet = doSet;
  }

  public static @IntentionName String initText(boolean doSet, @NlsSafe @NotNull String name, @NlsSafe @NotNull String modifier) {
    return GroovyBundle.message(
      doSet ? "change.modifier" : "change.modifier.not",
      name,
      toPresentableText(modifier)
    );
  }

  private static String getMemberName(PsiMember member, boolean showContainingClass) {
    if (showContainingClass) {
      final PsiClass containingClass = member.getContainingClass();
      String containingClassName = containingClass != null ? containingClass.getName() + "." : "";
      return containingClassName + member.getName();
    }
    else {
      return member.getName();
    }
  }

  public static String toPresentableText(String modifier) {
    return GroovyBundle.message(modifier + ".visibility.presentation");
  }

  @NotNull
  @Override
  public String getName() {
    return myText;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return GroovyBundle.message("change.modifier.family.name");
  }

  @Override
  protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) throws IncorrectOperationException {
    final PsiModifierList modifierList = getModifierList(descriptor);
    modifierList.setModifierProperty(myModifier, myDoSet);
  }

  private PsiModifierList getModifierList(ProblemDescriptor descriptor) {
    return myModifierListProvider.fun(descriptor);
  }
}
