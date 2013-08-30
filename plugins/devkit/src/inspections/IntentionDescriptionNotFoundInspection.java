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
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.inspections.quickfix.CreateHtmlDescriptionFix;
import org.jetbrains.idea.devkit.util.PsiUtil;

/**
 * @author Konstantin Bulenkov
 */
public class IntentionDescriptionNotFoundInspection extends DevKitInspectionBase{
  @NonNls private static final String INTENTION = "com.intellij.codeInsight.intention.IntentionAction";
  @NonNls private static final String INSPECTION_DESCRIPTIONS = "intentionDescriptions";

  @Override
  public ProblemDescriptor[] checkClass(@NotNull PsiClass aClass, @NotNull InspectionManager manager, boolean isOnTheFly) {
    final Project project = aClass.getProject();
    final PsiIdentifier nameIdentifier = aClass.getNameIdentifier();
    final Module module = ModuleUtil.findModuleForPsiElement(aClass);

    if (nameIdentifier == null || module == null || !PsiUtil.isInstantiatable(aClass)) return null;

    final PsiClass base = JavaPsiFacade.getInstance(project).findClass(INTENTION, GlobalSearchScope.allScope(project));

    if (base == null || ! aClass.isInheritor(base, true)) return null;

    String descriptionDir = getDescriptionDirName(aClass);
    if (StringUtil.isEmptyOrSpaces(descriptionDir)) {
      return null;
    }

    for (PsiDirectory description : getIntentionDescriptionsDirs(module)) {
      PsiDirectory dir = description.findSubdirectory(descriptionDir);
      if (dir == null) continue;
      final PsiFile descr = dir.findFile("description.html");
      if (descr != null) {
        if (!hasBeforeAndAfterTemplate(dir.getVirtualFile())) {
          PsiElement problem = aClass.getNameIdentifier();
          ProblemDescriptor problemDescriptor = manager.createProblemDescriptor(problem == null ? nameIdentifier : problem,
                                                                                "Intention must have 'before.*.template' and 'after.*.template' beside 'description.html'",
                                                                                isOnTheFly,
                                                                                ProblemHighlightType.GENERIC_ERROR_OR_WARNING, false);
          return new ProblemDescriptor[]{problemDescriptor};
        }

        return null;
      }
    }


    final PsiElement problem = aClass.getNameIdentifier();
    final ProblemDescriptor problemDescriptor = manager
      .createProblemDescriptor(problem == null ? nameIdentifier : problem,
                               "Intention does not have a description", isOnTheFly, new LocalQuickFix[]{new CreateHtmlDescriptionFix(descriptionDir, module, true)},
                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
    return new ProblemDescriptor[]{problemDescriptor};
  }

  @Nullable
  private static String getDescriptionDirName(PsiClass aClass) {
    String descriptionDir = "";
    PsiClass each = aClass;
    while (each != null) {
      String name = each.getName();
      if (StringUtil.isEmptyOrSpaces(name)) {
        return null;
      }
      descriptionDir = name + descriptionDir;
      each = each.getContainingClass();
    }
    return descriptionDir;
  }

  private static boolean hasBeforeAndAfterTemplate(@NotNull VirtualFile dir) {
    boolean hasBefore = false;
    boolean hasAfter = false;

    for (VirtualFile file : dir.getChildren()) {
      String name = file.getName();
      if (name.endsWith(".template")) {
        if (name.startsWith("before.")) {
          hasBefore = true;
        }
        else if (name.startsWith("after.")) {
          hasAfter = true;
        }
      }
    }

    return hasBefore && hasAfter;
  }

  public static PsiDirectory[] getIntentionDescriptionsDirs(Module module) {
    final PsiPackage aPackage = JavaPsiFacade.getInstance(module.getProject()).findPackage(INSPECTION_DESCRIPTIONS);
    if (aPackage != null) {
      return aPackage.getDirectories(GlobalSearchScope.moduleWithDependenciesScope(module));
    } else {
      return PsiDirectory.EMPTY_ARRAY;
    }
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

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }
}
