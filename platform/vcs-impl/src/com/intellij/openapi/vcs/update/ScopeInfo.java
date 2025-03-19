// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.update;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vcs.actions.VcsContextUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.jetbrains.annotations.Nls.Capitalization.Title;

public interface ScopeInfo {
  /**
   * @deprecated Use {@link #getRoots(DataContext, ActionInfo)}
   */
  @Deprecated(forRemoval = true)
  default FilePath[] getRoots(VcsContext context, @NotNull ActionInfo actionInfo) {
    DataContext dataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, context.getProject())
      .add(VcsDataKeys.FILE_PATHS, Arrays.asList(context.getSelectedFilePaths()))
      .build();
    return getRoots(dataContext, actionInfo).toArray(new FilePath[0]);
  }

  List<FilePath> getRoots(@NotNull DataContext dataContext, @NotNull ActionInfo actionInfo);

  @Nls(capitalization = Title) String getScopeName(@NotNull DataContext dataContext, final ActionInfo actionInfo);

  boolean filterExistsInVcs();

  ScopeInfo PROJECT = new ScopeInfo() {
    @Override
    public String getScopeName(@NotNull DataContext dataContext, final ActionInfo actionInfo) {
      return VcsBundle.message("update.project.scope.name");
    }

    @Override
    public boolean filterExistsInVcs() {
      return true;
    }

    @Override
    public List<FilePath> getRoots(@NotNull DataContext dataContext, @NotNull ActionInfo actionInfo) {
      ArrayList<FilePath> result = new ArrayList<>();
      Project project = dataContext.getData(CommonDataKeys.PROJECT);
      final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
      final AbstractVcs[] vcses = vcsManager.getAllActiveVcss();
      for (AbstractVcs vcs : vcses) {
        if (actionInfo.getEnvironment(vcs) != null) {
          final VirtualFile[] files = vcsManager.getRootsUnderVcs(vcs);
          for (VirtualFile file : files) {
            result.add(VcsUtil.getFilePath(file));
          }
        }
      }
      return result;
    }
  };

  ScopeInfo FILES = new ScopeInfo() {
    @Override
    public String getScopeName(@NotNull DataContext dataContext, final ActionInfo actionInfo) {
      List<FilePath> roots = getRoots(dataContext, actionInfo);
      if (roots.isEmpty()) {
        return VcsBundle.message("update.files.scope.name");
      }
      boolean directory = roots.get(0).isDirectory();
      if (roots.size() == 1) {
        if (directory) {
          return VcsBundle.message("update.directory.scope.name");
        }
        else {
          return VcsBundle.message("update.file.scope.name");
        }
      }
      else {
        if (directory) {
          return VcsBundle.message("update.directories.scope.name");
        }
        else {
          return VcsBundle.message("update.files.scope.name");
        }
      }
    }

    @Override
    public boolean filterExistsInVcs() {
      return true;
    }

    @Override
    public List<FilePath> getRoots(@NotNull DataContext dataContext, final @NotNull ActionInfo actionInfo) {
      return VcsContextUtil.selectedFilePaths(dataContext);
    }
  };
}
