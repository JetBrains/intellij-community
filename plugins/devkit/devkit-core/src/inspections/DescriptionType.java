// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInsight.intention.CommonIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.Nullable;

public enum DescriptionType {

  INTENTION(CommonIntentionAction.class.getName(), IntentionAction.class.getName(), "intentionDescriptions", true),
  INSPECTION(InspectionProfileEntry.class.getName(), null, "inspectionDescriptions", false),
  POSTFIX_TEMPLATES(PostfixTemplate.class.getName(), null, "postfixTemplates", true);

  private final String myClassName;
  @Nullable private final String myFallbackClassName;
  private final String myDescriptionFolder;
  private final boolean myFixedDescriptionFilename;

  DescriptionType(String className,
                  @Nullable String fallbackClassName,
                  String descriptionFolder,
                  boolean fixedDescriptionFilename) {
    myFallbackClassName = fallbackClassName;
    myFixedDescriptionFilename = fixedDescriptionFilename;
    myClassName = className;
    myDescriptionFolder = descriptionFolder;
  }

  public boolean matches(PsiClass psiClass) {
    PsiClass baseClass = JavaPsiFacade.getInstance(psiClass.getProject()).findClass(myClassName, psiClass.getResolveScope());
    if (baseClass != null) {
      return psiClass.isInheritor(baseClass, true);
    }

    return myFallbackClassName != null && InheritanceUtil.isInheritor(psiClass, myFallbackClassName);
  }

  public String getDescriptionFolder() {
    return myDescriptionFolder;
  }

  public boolean isFixedDescriptionFilename() {
    return myFixedDescriptionFilename;
  }
}
