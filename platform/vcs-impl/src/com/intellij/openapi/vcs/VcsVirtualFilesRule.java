// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs;

import com.intellij.ide.IdeView;
import com.intellij.openapi.actionSystem.DataMap;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.actionSystem.CommonDataKeys.VIRTUAL_FILE;
import static com.intellij.openapi.actionSystem.CommonDataKeys.VIRTUAL_FILE_ARRAY;
import static com.intellij.openapi.actionSystem.LangDataKeys.IDE_VIEW;

/**
 * {@link VcsDataKeys#VIRTUAL_FILES}
 */
@ApiStatus.Internal
public final class VcsVirtualFilesRule {
  private static final Logger LOG = Logger.getInstance(VcsVirtualFilesRule.class);

  public static @Nullable Iterable<VirtualFile> getData(@NotNull DataMap dataProvider) {
    VirtualFile[] files = dataProvider.get(VIRTUAL_FILE_ARRAY);
    if (files != null) {
      return JBIterable.of(files);
    }

    VirtualFile file = dataProvider.get(VIRTUAL_FILE);
    if (file != null) {
      return JBIterable.of(file);
    }

    IdeView view = dataProvider.get(IDE_VIEW);
    if (view != null) {
      JBIterable<PsiDirectory> directories = JBIterable.of(view.getDirectories());
      if (directories.isNotEmpty()) {
        if (directories.contains(null)) {
          LOG.error("Array with null provided by " + view.getClass().getName());
        }
        return directories.filterNotNull().filterMap(o -> o.getVirtualFile()).collect();
      }
    }

    return null;
  }
}
