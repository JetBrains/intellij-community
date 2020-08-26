// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.scope.packageSet.CustomScopesProvider;
import com.intellij.psi.search.scope.packageSet.FilteredPackageSet;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public final class OpenFilesScope extends NamedScope {
  public static final OpenFilesScope INSTANCE = new OpenFilesScope();

  private OpenFilesScope() {
    super("Open Files", () -> getNameText(), AllIcons.FileTypes.Any_type, new FilteredPackageSet(getNameText()) {
      @Override
      public boolean contains(@NotNull VirtualFile file, @NotNull Project project) {
        FileEditorManager manager = project.isDisposed() ? null : FileEditorManager.getInstance(project);
        return manager != null && manager.isFileOpen(file);
      }
    });
  }

  public static final class Provider implements CustomScopesProvider {
    @NotNull
    @Override
    public List<NamedScope> getCustomScopes() {
      return Collections.singletonList(INSTANCE);
    }
  }

  public static @NotNull @Nls String getNameText() {
    return IdeBundle.message("scope.open.files");
  }
}
