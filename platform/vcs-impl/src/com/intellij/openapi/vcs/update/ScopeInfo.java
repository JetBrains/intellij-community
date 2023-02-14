// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.update;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

import static org.jetbrains.annotations.Nls.Capitalization.Title;

public interface ScopeInfo {
  FilePath[] getRoots(VcsContext context, final ActionInfo actionInfo);

  @Nls(capitalization = Title) String getScopeName(VcsContext dataContext, final ActionInfo actionInfo);

  boolean filterExistsInVcs();

  ScopeInfo PROJECT = new ScopeInfo() {
    @Override
    public String getScopeName(VcsContext dataContext, final ActionInfo actionInfo) {
      return VcsBundle.message("update.project.scope.name");
    }

    @Override
    public boolean filterExistsInVcs() {
      return true;
    }

    @Override
    public FilePath[] getRoots(VcsContext context, final ActionInfo actionInfo) {
      ArrayList<FilePath> result = new ArrayList<>();
      Project project = context.getProject();
      final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
      final AbstractVcs[] vcses = vcsManager.getAllActiveVcss();
      for(AbstractVcs vcs: vcses) {
        if (actionInfo.getEnvironment(vcs) != null) {
          final VirtualFile[] files = vcsManager.getRootsUnderVcs(vcs);
          for(VirtualFile file: files) {
            result.add(VcsUtil.getFilePath(file));
          }
        }
      }
      return result.toArray(new FilePath[0]);
    }
  };

  ScopeInfo FILES = new ScopeInfo() {
    @Override
    public String getScopeName(VcsContext dataContext, final ActionInfo actionInfo) {
      FilePath[] roots = getRoots(dataContext, actionInfo);
      if (roots.length == 0) {
        return VcsBundle.message("update.files.scope.name");
      }
      boolean directory = roots[0].isDirectory();
      if (roots.length == 1) {
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
    public FilePath @NotNull [] getRoots(VcsContext context, final ActionInfo actionInfo) {
      return context.getSelectedFilePaths();
    }

  };
}
