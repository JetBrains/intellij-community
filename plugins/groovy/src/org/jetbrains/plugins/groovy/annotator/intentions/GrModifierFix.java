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
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;

/**
 * @author Maxim.Medvedev
 */
public class GrModifierFix implements IntentionAction {
  private final PsiMember myMember;
  private final String myModifier;
  private final boolean showContainingClass;

  public GrModifierFix(PsiMember member, String modifier, boolean showContainingClass) {
    myMember = member;
    myModifier = modifier;
    this.showContainingClass = showContainingClass;
  }

  @NotNull
  public String getText() {
    if (showContainingClass) {
      final PsiClass containingClass = myMember.getContainingClass();
      String containingClassName;
      if (containingClass != null) {
        containingClassName = containingClass.getName() + ".";
      }
      else {
        containingClassName = "";
      }
      String modifierText = VisibilityUtil.toPresentableText(myModifier);

      return GroovyBundle.message("change.modifier", containingClassName + myMember.getName(), modifierText);
    }
    else {
      return GroovyBundle.message("change.modifier", myMember.getName(), myModifier);
    }
  }

  @NotNull
  public String getFamilyName() {
    return GroovyBundle.message("change.modifier.family.name");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    final PsiReference ref = file.findReferenceAt(editor.getCaretModel().getOffset());
    if (ref == null) return false;
    final PsiElement element = ref.getElement();
    if (element instanceof GrReferenceElement) {
      final GroovyResolveResult resolveResult = ((GrReferenceElement)element).advancedResolve();
      if (!resolveResult.isAccessible() && resolveResult.getElement() instanceof PsiMember) {
        return true;
      }
    }
    return false;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PsiModifierList list = myMember.getModifierList();
    if (list != null) {
      list.setModifierProperty(myModifier, true);
    }
  }

  public boolean startInWriteAction() {
    return true;
  }
}
