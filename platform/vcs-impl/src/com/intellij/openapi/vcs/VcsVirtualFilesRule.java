// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs;

import com.intellij.ide.IdeView;
import com.intellij.ide.impl.dataRules.GetDataRule;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link VcsDataKeys#VIRTUAL_FILES}
 */
@ApiStatus.Internal
public final class VcsVirtualFilesRule implements GetDataRule {
  private static final Logger LOG = Logger.getInstance(VcsVirtualFilesRule.class);

  @Override
  public @Nullable Object getData(@NotNull DataProvider dataProvider) {
    VirtualFile[] files = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataProvider);
    if (files != null) {
      return JBIterable.of(files);
    }

    VirtualFile file = CommonDataKeys.VIRTUAL_FILE.getData(dataProvider);
    if (file != null) {
      return JBIterable.of(file);
    }

    IdeView view = LangDataKeys.IDE_VIEW.getData(dataProvider);
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
