// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.lang.properties.codeInspection.unused.ImplicitPropertyUsageProvider;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScopes;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ipp.base.Intention;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.references.MessageBundleReferenceContributor;
import org.jetbrains.idea.devkit.util.PsiUtil;

final class IntentionsImplicitPropertyUsageProvider implements ImplicitPropertyUsageProvider {
  private static final String _FAMILY_NAME = ".family.name";
  private static final String _NAME = ".name";

  @Override
  public boolean isUsed(@NotNull Property property) {
    PsiFile containingFile = property.getContainingFile();
    Project project = containingFile.getProject();
    if (PsiUtil.isIdeaProject(project) &&
        containingFile.getName().endsWith(MessageBundleReferenceContributor.BUNDLE_PROPERTIES)) {
      String name = property.getName();
      if (name != null) {
        int length = name.length();
        int limit;
        if (name.endsWith(_FAMILY_NAME)) {
          limit = length - _FAMILY_NAME.length();
        }
        else if (name.endsWith(_NAME)) {
          limit = length - _NAME.length();
        }
        else {
          return false;
        }

        boolean toUpperCase = true;
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < limit; i++) {
          if (name.charAt(i) == '.') {
            toUpperCase = true;
          }
          else {
            if (toUpperCase) {
              buf.append(Character.toUpperCase(name.charAt(i)));
            }
            else {
              buf.append(name.charAt(i));
            }
            toUpperCase = false;
          }
        }

        PsiClass[] classes =
          PsiShortNamesCache.getInstance(project).getClassesByName(buf.toString(), GlobalSearchScopes.projectProductionScope(project));
        return ContainerUtil.exists(classes, aClass -> InheritanceUtil.isInheritor(aClass, Intention.class.getName()));
      }
    }
    return false;
  }
}
