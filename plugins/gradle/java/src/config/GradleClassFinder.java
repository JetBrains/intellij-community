// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.config;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.externalSystem.psi.search.ExternalModuleBuildGlobalSearchScope;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.PackageDirectoryCache;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.NonClasspathClassFinder;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.EverythingGlobalScope;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.GradleBuildClasspathManager;
import org.jetbrains.plugins.groovy.GroovyFileType;

import java.util.List;
import java.util.Map;

public final class GradleClassFinder extends NonClasspathClassFinder {

  private static final String KOTLIN_DEFAULT_EXTENSION = "kt";

  public GradleClassFinder(@NotNull Project project) {
    super(project, JavaFileType.DEFAULT_EXTENSION, GroovyFileType.DEFAULT_EXTENSION, KOTLIN_DEFAULT_EXTENSION);
  }

  @Override
  protected List<VirtualFile> calcClassRoots() {
    return GradleBuildClasspathManager.getInstance(myProject).getAllClasspathEntries();
  }

  @NotNull
  @Override
  protected PackageDirectoryCache getCache(@Nullable GlobalSearchScope scope) {
    if (scope instanceof ExternalModuleBuildGlobalSearchScope) {
      GradleBuildClasspathManager buildClasspathManager = GradleBuildClasspathManager.getInstance(myProject);
      Map<String, PackageDirectoryCache> classFinderCache = buildClasspathManager.getClassFinderCache();
      return classFinderCache.get(((ExternalModuleBuildGlobalSearchScope)scope).getExternalModulePath());
    }
    return super.getCache(scope);
  }

  @Override
  public void clearCache() {
    super.clearCache();
    GradleBuildClasspathManager buildClasspathManager = GradleBuildClasspathManager.getInstance(myProject);
    Map<String, PackageDirectoryCache> classFinderCache = buildClasspathManager.getClassFinderCache();
    classFinderCache.clear();
  }

  @Override
  public PsiClass findClass(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    PsiClass aClass = super.findClass(qualifiedName, scope);
    if (aClass == null || scope instanceof ExternalModuleBuildGlobalSearchScope || scope instanceof EverythingGlobalScope) {
      return aClass;
    }

    PsiFile containingFile = aClass.getContainingFile();
    VirtualFile file = containingFile != null ? containingFile.getVirtualFile() : null;
    return file != null &&
           !ProjectFileIndex.getInstance(myProject).isInContent(file) &&
           !ProjectFileIndex.getInstance(myProject).isInLibrary(file) ? aClass : null;
  }

  @Override
  public PsiPackage @NotNull [] getSubPackages(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
    return scope instanceof ExternalModuleBuildGlobalSearchScope ? super.getSubPackages(psiPackage, scope) : PsiPackage.EMPTY_ARRAY;
  }
}