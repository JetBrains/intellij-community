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
package org.jetbrains.plugins.groovy.codeInspection.bugs;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiModifierList;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;

/**
 * @author Max Medvedev
 */
public class GrModifierFix extends GroovyFix {
  @NotNull private final PsiModifierList myModifierList;
  private final String myModifier;
  private final String myText;
  private final boolean myDoSet;

  public GrModifierFix(@NotNull PsiMember member,
                       @NotNull PsiModifierList modifierList,
                       @GrModifier.GrModifierConstant String modifier,
                       boolean showContainingClass,
                       boolean doSet) {
    myModifierList = modifierList;
    myModifier = modifier;
    myDoSet = doSet;

    myText = initText(member, showContainingClass, myModifier, myDoSet);
  }

  public static String initText(final PsiMember member, final boolean showContainingClass, final String modifier, final boolean doSet) {
    String name;
    if (showContainingClass) {
      final PsiClass containingClass = member.getContainingClass();
      String containingClassName;
      if (containingClass != null) {
        containingClassName = containingClass.getName() + ".";
      }
      else {
        containingClassName = "";
      }

      name = containingClassName + member.getName();
    }
    else {
      name = member.getName();
    }
    String modifierText = toPresentableText(modifier);

    if (doSet) {
      return GroovyBundle.message("change.modifier", name, modifierText);
    }
    else {
      return GroovyBundle.message("change.modifier.not", name, modifierText);
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

  @NotNull
  public String getFamilyName() {
    return GroovyBundle.message("change.modifier.family.name");
  }

  @Override
  protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
    assert myModifierList.isValid();
    myModifierList.setModifierProperty(myModifier, myDoSet);
  }
}
