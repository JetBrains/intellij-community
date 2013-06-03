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
package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;

/**
 * @author Maxim.Medvedev
 */
public class GrModifierFix extends Intention {
  private final String myModifier;
  private final String myText;
  private final boolean myDoSet;
  private final PsiModifierList myModifierList;

  public GrModifierFix(@NotNull PsiMember member,
                       @NotNull PsiModifierList modifierList,
                       @GrModifier.GrModifierConstant String modifier,
                       boolean showContainingClass,
                       boolean doSet) {
    myModifierList = modifierList;
    myModifier = modifier;
    myDoSet = doSet;

    myText = org.jetbrains.plugins.groovy.codeInspection.bugs.GrModifierFix.initText(member, showContainingClass, modifier, doSet);
  }

  @NotNull
  @Override
  public String getText() {
    return myText;
  }

  @Override
  protected void processIntention(@NotNull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
    assert myModifierList.isValid();
    myModifierList.setModifierProperty(myModifier, myDoSet);
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(PsiElement element) {
        return element instanceof PsiModifierList || element instanceof PsiModifierListOwner;
      }
    };
  }

  @NotNull
  public String getFamilyName() {
    return GroovyBundle.message("change.modifier.family.name");
  }
}
