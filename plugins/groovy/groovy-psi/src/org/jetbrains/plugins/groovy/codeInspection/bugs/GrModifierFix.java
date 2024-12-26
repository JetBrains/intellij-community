// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.bugs;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.*;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;

/**
 * @author Max Medvedev
 */
public class GrModifierFix extends ModCommandQuickFix {
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

  /**
   * The function must be pure
   */
  public GrModifierFix(@NotNull GrVariable member,
                       @GrModifier.GrModifierConstant String modifier,
                       boolean doSet,
                       @NotNull Function<? super ProblemDescriptor, ? extends PsiModifierList> modifierListProvider) {
    this(initText(doSet, member.getName(), modifier), modifier, doSet, modifierListProvider);
  }

  /**
   * The function must be pure
   */
  public GrModifierFix(@NotNull PsiMember member,
                       @GrModifier.GrModifierConstant String modifier,
                       boolean showContainingClass,
                       boolean doSet,
                       @NotNull Function<? super ProblemDescriptor, ? extends PsiModifierList> modifierListProvider) {
    this(initText(doSet, getMemberName(member, showContainingClass), modifier), modifier, doSet, modifierListProvider);
  }

  /**
   * The function must be pure
   */
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

  @Override
  public @NotNull String getName() {
    return myText;
  }

  @Override
  public @NotNull String getFamilyName() {
    return GroovyBundle.message("change.modifier.family.name");
  }

  @Override
  public @NotNull ModCommand perform(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiModifierList modifierList = getModifierList(descriptor);
    return ModCommand.psiUpdate(modifierList, m -> m.setModifierProperty(myModifier, myDoSet));
  }

  private PsiModifierList getModifierList(ProblemDescriptor descriptor) {
    return myModifierListProvider.fun(descriptor);
  }
}
