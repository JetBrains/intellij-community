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

import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import org.intellij.plugins.intelliLang.IntelliLangBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AnnotateFix extends LocalQuickFixOnPsiElement {
  @NotNull
  private final String myAnnotationName;

  @Nullable
  private final String myArgList;

  private AnnotateFix(@NotNull PsiModifierListOwner element, @NotNull String annotationClassname, @Nullable String argList) {
    super(element);
    myAnnotationName = annotationClassname;
    myArgList = argList;
  }

  public static boolean canApplyOn(PsiModifierListOwner element) {
    return PsiUtilEx.isInSourceContent(element) && element.getModifierList() != null;
  }

  @Nullable
  public static AnnotateFix create(@NotNull PsiElement element, @NotNull String annotationClassname) {
    return create(element, annotationClassname, null);
  }

  @Nullable
  public static AnnotateFix create(@NotNull PsiElement element, @NotNull String annotationClassname, @Nullable String argList) {
    if (!(element instanceof PsiModifierListOwner)) {
      element = AnnotationUtilEx.getAnnotatedElementFor(element, AnnotationUtilEx.LookupType.PREFER_DECLARATION);
      // this element may be from a different file
      if (element == null) return null;
    }
    return new AnnotateFix((PsiModifierListOwner)element, annotationClassname, argList);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    if (myArgList == null) {
      return IntelliLangBundle.message("annotate.fix.family.name", StringUtil.getShortName(myAnnotationName));
    } else {
      return IntelliLangBundle.message("annotate.fix.family.name", StringUtil.getShortName(myAnnotationName) + myArgList);
    }
  }

  @Override
  public @NotNull String getText() {
    return getFamilyName();
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiFile file, @NotNull PsiElement startElement, @NotNull PsiElement endElement) {
    final PsiModifierList modifierList = ((PsiModifierListOwner)startElement).getModifierList();
    if (modifierList == null) {
      return;
    }
    final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
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
    if (requirement != InitializerRequirement.NONE_REQUIRED && myArgList == null && !IntentionPreviewUtils.isIntentionPreviewActive()) {
      ((NavigationItem)list).navigate(true);
    }
  }
}
