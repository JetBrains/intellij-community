/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.codeInspection.bugs;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;

/**
 * @author Max Medvedev
 */
public class GrModifierFix extends GroovyFix {
  public static final Function<ProblemDescriptor, PsiModifierList> MODIFIER_LIST = new Function<ProblemDescriptor, PsiModifierList>() {
    @Override
    public PsiModifierList fun(ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      assert element instanceof PsiModifierList : element;
      return (PsiModifierList)element;
    }
  };

  public static final Function<ProblemDescriptor, PsiModifierList> MODIFIER_LIST_OWNER = new Function<ProblemDescriptor, PsiModifierList>() {
    @Override
    public PsiModifierList fun(ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      assert element instanceof PsiModifierListOwner : element;
      return ((PsiModifierListOwner)element).getModifierList();
    }
  };


  private final String myModifier;
  private final String myText;
  private final boolean myDoSet;
  private final Function<ProblemDescriptor, PsiModifierList> myModifierListProvider;

  public GrModifierFix(@NotNull PsiVariable member,
                       @GrModifier.GrModifierConstant String modifier,
                       boolean doSet,
                       @NotNull Function<ProblemDescriptor, PsiModifierList> modifierListProvider) {
    myModifier = modifier;
    myDoSet = doSet;
    myModifierListProvider = modifierListProvider;
    myText = initText(doSet, member.getName(), modifier);
  }

  public GrModifierFix(@NotNull PsiMember member,
                       @GrModifier.GrModifierConstant String modifier,
                       boolean showContainingClass,
                       boolean doSet,
                       @NotNull Function<ProblemDescriptor, PsiModifierList> modifierListProvider) {
    myModifier = modifier;
    myDoSet = doSet;
    myModifierListProvider = modifierListProvider;
    myText = initText(doSet, getMemberName(member, showContainingClass), modifier);
  }

  public static String initText(boolean doSet, @NotNull String name, @NotNull String modifier) {
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
  protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
    final PsiModifierList modifierList = getModifierList(descriptor);
    modifierList.setModifierProperty(myModifier, myDoSet);
  }

  private PsiModifierList getModifierList(ProblemDescriptor descriptor) {
    return myModifierListProvider.fun(descriptor);
  }
}
