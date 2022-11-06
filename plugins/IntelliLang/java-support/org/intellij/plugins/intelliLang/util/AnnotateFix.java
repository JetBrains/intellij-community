/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.plugins.intelliLang.util;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import org.intellij.plugins.intelliLang.IntelliLangBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AnnotateFix implements LocalQuickFix {
  private final String myAnnotationName;
  private final String myArgList;

  public AnnotateFix(String annotationClassname) {
    this(annotationClassname, null);
  }

  public AnnotateFix(String annotationClassname, @Nullable String argList) {
    myAnnotationName = annotationClassname;
    myArgList = argList;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return IntelliLangBundle.message("annotate.fix.family.name", StringUtil.getShortName(myAnnotationName));
  }

  public static boolean canApplyOn(PsiModifierListOwner element) {
    return PsiUtilEx.isInSourceContent(element) && element.getModifierList() != null;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
     PsiElement element = descriptor.getPsiElement();
    if (!(element instanceof PsiModifierListOwner)) {
      element = AnnotationUtilEx.getAnnotatedElementFor(element, AnnotationUtilEx.LookupType.PREFER_DECLARATION);
      // this element may be from a different file
      if (element == null) {
        return;
      }
    }
    final PsiModifierList modifierList = ((PsiModifierListOwner)element).getModifierList();
    if (modifierList == null) {
      return;
    }
    if (!FileModificationService.getInstance().preparePsiElementsForWrite(element)) {
      return;
    }
    WriteAction.run(() -> {
      final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
      try {
        final PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(myAnnotationName, modifierList.getResolveScope());
        final InitializerRequirement requirement = InitializerRequirement.calcInitializerRequirement(psiClass);

        final String argList;
        if (myArgList == null) {
          argList = switch (requirement) {
            case VALUE_REQUIRED, OTHER_REQUIRED -> "(\"\")";
            default -> "";
          };
        }
        else {
          argList = myArgList;
        }

        PsiAnnotation annotation = factory.createAnnotationFromText("@" + myAnnotationName + argList, modifierList);
        annotation = (PsiAnnotation)modifierList.addBefore(annotation, modifierList.getFirstChild());
        annotation = (PsiAnnotation)JavaCodeStyleManager.getInstance(project).shortenClassReferences(annotation);

        final PsiAnnotationParameterList list = annotation.getParameterList();
        if (requirement != InitializerRequirement.NONE_REQUIRED && myArgList == null) {
          ((NavigationItem)list).navigate(true);
        }
      }
      catch (IncorrectOperationException e) {
        Logger.getInstance(getClass().getName()).error(e);
      }
    });
  }
}
