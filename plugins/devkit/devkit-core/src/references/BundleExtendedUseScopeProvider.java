// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.references;

import com.intellij.lang.properties.codeInspection.unused.ExtendedUseScopeProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.java.stubs.index.JavaShortClassNameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

import static com.intellij.psi.search.GlobalSearchScope.projectScope;

/**
 * Expand use scope of keys in ActionsBundle.properties to all modules where ActionsBundle class is accessible.
 * <p>
 * Used by UnusedPropertyInspection.
 */
final class BundleExtendedUseScopeProvider implements ExtendedUseScopeProvider {

  private static final String PLATFORM_RESOURCES_MODULE = "intellij.platform.resources";

  @Override
  public @Nullable GlobalSearchScope getExtendedUseScope(@NotNull PsiFile propertiesFile) {
    String name = FileUtil.getNameWithoutExtension(propertiesFile.getName());

    if (name.isEmpty()) return null;

    Project project = propertiesFile.getProject();

    Module platformResourcesModule = ModuleManager.getInstance(project).findModuleByName(PLATFORM_RESOURCES_MODULE);
    if (platformResourcesModule != null) { // only needed in IJ project
      Collection<PsiClass> bundleClasses = JavaShortClassNameIndex.getInstance().getClasses(name, project, projectScope(project));
      if (bundleClasses.isEmpty()) return null;

      if (bundleClasses.size() == 1) {
        PsiClass psiClass = bundleClasses.iterator().next();
        Module bundleModule = ModuleUtilCore.findModuleForPsiElement(psiClass);
        if (bundleModule != null) {
          // always include intellij.platform.resources XMLs, most of the usages of platform actions and extensions are there
          return bundleModule.getModuleWithDependentsScope().union(platformResourcesModule.getModuleScope());
        }
      }
    }
    // probably too-generic name
    return null;
  }
}
