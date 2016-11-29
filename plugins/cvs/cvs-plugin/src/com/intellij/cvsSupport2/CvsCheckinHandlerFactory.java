/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package com.intellij.cvsSupport2;

import com.intellij.cvsSupport2.checkinProject.AdditionalOptionsPanel;
import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vcs.checkin.VcsCheckinHandlerFactory;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author yole
*/
class CvsCheckinHandlerFactory extends VcsCheckinHandlerFactory {

  CvsCheckinHandlerFactory() {
    super(CvsVcs2.getKey());
  }

  @NotNull
  @Override
  protected CheckinHandler createVcsHandler(final CheckinProjectPanel panel) {
    return new CheckinHandler() {
      @Nullable
      public RefreshableOnComponent getAfterCheckinConfigurationPanel(Disposable parentDisposable) {
        final Project project = panel.getProject();
        final CvsVcs2 cvs = CvsVcs2.getInstance(project);
        final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
        final Collection<VirtualFile> roots = panel.getRoots();
        final Collection<FilePath> files = new HashSet<>();
        for (VirtualFile root : roots) {
          final VcsRoot vcsRoot = vcsManager.getVcsRootObjectFor(root);
          if (vcsRoot == null || vcsRoot.getVcs() != cvs) {
            continue;
          }
          files.add(VcsContextFactory.SERVICE.getInstance().createFilePathOn(root));
        }
        return new AdditionalOptionsPanel(CvsConfiguration.getInstance(project), files, project);
      }
    };
  }
}