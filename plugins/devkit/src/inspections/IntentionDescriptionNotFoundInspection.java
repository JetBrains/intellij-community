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
package org.jetbrains.idea.devkit.inspections;

import com.intellij.openapi.module.Module;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.inspections.quickfix.CreateHtmlDescriptionFix;

/**
 * @author Konstantin Bulenkov
 */
public class IntentionDescriptionNotFoundInspection extends DescriptionNotFoundInspectionBase {
  @NonNls private static final String INTENTION = "com.intellij.codeInsight.intention.IntentionAction";
  @NonNls private static final String INSPECTION_DESCRIPTIONS = "intentionDescriptions";

  protected CreateHtmlDescriptionFix getFix(Module module, String descriptionDir) {
    return new CreateHtmlDescriptionFix(descriptionDir, module, true);
  }

  @NotNull
  protected String getClassName() {
    return INTENTION;
  }

  @NotNull
  protected String getHasNotDescriptionError() {
    return "Intention does not have a description";
  }

  @NotNull
  protected String getHasNotBeforeAfterError() {
    return "Intention must have 'before.*.template' and 'after.*.template' beside 'description.html'";
  }

  public static PsiDirectory[] getIntentionDescriptionsDirs(Module module) {
    final PsiPackage aPackage = JavaPsiFacade.getInstance(module.getProject()).findPackage(INSPECTION_DESCRIPTIONS);
    if (aPackage != null) {
      return aPackage.getDirectories(GlobalSearchScope.moduleWithDependenciesScope(module));
    }
    else {
      return PsiDirectory.EMPTY_ARRAY;
    }
  }

  @NotNull
  protected PsiDirectory[] getDescriptionsDirs(@NotNull Module module) {
    return getIntentionDescriptionsDirs(module);
  }

  @Nls
  @NotNull
  public String getDisplayName() {
    return "Intention Description Checker";
  }

  @NotNull
  public String getShortName() {
    return "IntentionDescriptionNotFoundInspection";
  }
}
