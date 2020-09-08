// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import com.intellij.find.impl.FindInProjectExtension;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.impl.scopes.ModuleWithDependenciesScope;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.*;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public final class Scopes {

  private Scopes() {}

  public static Type getType(@Nullable SearchScope scope) {
    if (scope instanceof ProjectScopeImpl || scope == null) {
      return Type.PROJECT;
    }
    else if (scope instanceof ModuleWithDependenciesScope) {
      return Type.MODULE;
    }
    else if (scope instanceof GlobalSearchScopesCore.DirectoryScope) {
      return Type.DIRECTORY;
    }
    else {
      return Type.NAMED;
    }
  }

  public static String getDescriptor(SearchScope scope) {
    if (scope instanceof ProjectScopeImpl || scope == null) {
      return "";
    }
    else if (scope instanceof ModuleWithDependenciesScope) {
      return ((ModuleWithDependenciesScope)scope).getModule().getName();
    }
    else if (scope instanceof GlobalSearchScopesCore.DirectoryScope) {
      final GlobalSearchScopesCore.DirectoryScope directoryScope = (GlobalSearchScopesCore.DirectoryScope)scope;
      final String url = directoryScope.getDirectory().getPresentableUrl();
      return directoryScope.isWithSubdirectories() ? "*" + url : url;
    }
    else {
      return scope.getDisplayName();
    }
  }

  public static SearchScope createScope(@NotNull Project project, @NotNull String descriptor, @NotNull Type scopeType) {
    if (scopeType == Type.PROJECT) {
      return GlobalSearchScope.projectScope(project);
    }
    else if (scopeType == Type.MODULE) {
      final Module module = ModuleManager.getInstance(project).findModuleByName(descriptor);
      if (module != null) {
        return GlobalSearchScope.moduleScope(module);
      }
    }
    else if (scopeType == Type.DIRECTORY) {
      final boolean recursive = StringUtil.startsWithChar(descriptor, '*');
      if (recursive) {
        descriptor = descriptor.substring(1);
      }
      final String path = FileUtil.toSystemIndependentName(descriptor.substring(1));
      final VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(path);
      if (virtualFile == null) return null;
      return new GlobalSearchScopesCore.DirectoryScope(project, virtualFile, recursive);
    }
    else if (scopeType == Type.NAMED) {
      return findScopeByName(project, descriptor);
    }
    assert false;
    return null;
  }

  @Nullable
  public static SearchScope findScopeByName(@NotNull Project project, @NotNull String scopeName) {
    // can't use ScopeChooserUtils.findScopeByName() because it returns intersection scopes that can't be presented the user
    // and doesn't return all predefined scopes
    final List<SearchScope> predefinedScopes =
      PredefinedSearchScopeProvider.getInstance().getPredefinedScopes(project, null, true, false, true, true, true);
    for (SearchScope predefinedScope : predefinedScopes) {
      if (predefinedScope.getDisplayName().equals(scopeName)) {
        return predefinedScope;
      }
    }

    for (FindInProjectExtension extension : FindInProjectExtension.EP_NAME.getExtensionList()) {
      for (NamedScope scope : extension.getFilteredNamedScopes(project)) {
        if (scope.getScopeId().equals(scopeName)) {
          return GlobalSearchScopesCore.filterScope(project, scope);
        }
      }
    }

    for (NamedScopesHolder holder: NamedScopesHolder.getAllNamedScopeHolders(project)) {
      for (NamedScope scope: holder.getEditableScopes()) {
        if (scope.getScopeId().equals(scopeName)) {
          return GlobalSearchScopesCore.filterScope(project, scope);
        }
      }
    }
    return null;
  }

  public enum Type {
    PROJECT,
    MODULE,
    DIRECTORY,
    NAMED
  }
}
