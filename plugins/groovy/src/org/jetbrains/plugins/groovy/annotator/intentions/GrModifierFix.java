/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiModifierList;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;

/**
 * @author Maxim.Medvedev
 */
public class GrModifierFix implements IntentionAction {
  @NotNull private final PsiMember myMember;
  private final String myModifier;
  private final boolean myShowContainingClass;
  private final PsiModifierList myModifierList;
  private final boolean myDoSet;

  public GrModifierFix(@NotNull PsiMember member,
                       @NotNull PsiModifierList modifierList,
                       String modifier,
                       boolean showContainingClass,
                       boolean doSet) {
    myMember = member;
    myModifier = modifier;
    myShowContainingClass = showContainingClass;
    myModifierList = modifierList;
    myDoSet = doSet;
  }

  @NotNull
  public String getText() {
    String name;
    if (myShowContainingClass) {
      final PsiClass containingClass = myMember.getContainingClass();
      String containingClassName;
      if (containingClass != null) {
        containingClassName = containingClass.getName() + ".";
      }
      else {
        containingClassName = "";
      }

      name = containingClassName + myMember.getName();
    }
    else {
      name = myMember.getName();
    }
    String modifierText = VisibilityUtil.toPresentableText(myModifier);

    if (myDoSet) {
      return GroovyBundle.message("change.modifier", name, modifierText);
    }
    else {
      return GroovyBundle.message("change.modifier.not", name, modifierText);
    }
  }

  @NotNull
  public String getFamilyName() {
    return GroovyBundle.message("change.modifier.family.name");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myModifierList != null && myModifierList.isValid();
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    myModifierList.setModifierProperty(myModifier, myDoSet);
  }

  public boolean startInWriteAction() {
    return true;
  }
}
