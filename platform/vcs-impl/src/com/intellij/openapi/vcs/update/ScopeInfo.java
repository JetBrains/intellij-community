/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.update;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;

import java.util.ArrayList;

public interface ScopeInfo {
  FilePath[] getRoots(VcsContext context, final ActionInfo actionInfo);
  String getScopeName(VcsContext dataContext, final ActionInfo actionInfo);
  boolean filterExistsInVcs();

  ScopeInfo PROJECT = new ScopeInfo() {
    public String getScopeName(VcsContext dataContext, final ActionInfo actionInfo) {
      return VcsBundle.message("update.project.scope.name");
    }

    public boolean filterExistsInVcs() {
      return true;
    }

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
      return result.toArray(new FilePath[result.size()]);
    }
  };

  ScopeInfo FILES = new ScopeInfo() {
    public String getScopeName(VcsContext dataContext, final ActionInfo actionInfo) {
      FilePath[] roots = getRoots(dataContext, actionInfo);
      if (roots == null || roots.length == 0) {
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

    public boolean filterExistsInVcs() {
      return true;
    }

    public FilePath[] getRoots(VcsContext context, final ActionInfo actionInfo) {
      return context.getSelectedFilePaths();
    }

  };
}
