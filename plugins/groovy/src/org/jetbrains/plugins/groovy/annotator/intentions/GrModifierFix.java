/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiModifierList;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;

/**
 * @author Maxim.Medvedev
 */
public class GrModifierFix extends GroovyFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance(GrModifierFix.class);


  @NotNull
  private final PsiModifierList myModifierList;

  @GrModifier.GrModifierConstant
  private final String myModifier;

  @NotNull
  private final String myText;

  private final boolean myShowContainingClass;
  private final boolean myDoSet;

  public GrModifierFix(@NotNull PsiMember member,
                       @NotNull PsiModifierList modifierList,
                       @GrModifier.GrModifierConstant String modifier,
                       boolean showContainingClass,
                       boolean doSet) {
    myModifier = modifier;
    myShowContainingClass = showContainingClass;
    myModifierList = modifierList;
    myDoSet = doSet;

    myText = initText(member);
  }

  @NotNull
  public String getText() {
    return myText;
  }

  @NotNull
  @Override
  public String getName() {
    return getText();
  }

  private String initText(final PsiMember member) {
    String name;
    if (myShowContainingClass) {
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
    String modifierText = toPresentableText(myModifier);

    if (myDoSet) {
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
  public String getFamilyName() {
    return GroovyBundle.message("change.modifier.family.name");
  }

  @Override
  protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
   invokeImpl();
  }

  private void invokeImpl() {
    LOG.assertTrue(myModifierList.isValid());
    myModifierList.setModifierProperty(myModifier, myDoSet);
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myModifierList.isValid();
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    invokeImpl();
  }

  public boolean startInWriteAction() {
    return true;
  }
}
