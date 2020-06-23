// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vcs.checkin.VcsCheckinHandlerFactory;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;

class CvsCheckinHandlerFactory extends VcsCheckinHandlerFactory {

  CvsCheckinHandlerFactory() {
    super(CvsVcs2.getKey());
  }

  @NotNull
  @Override
  protected CheckinHandler createVcsHandler(@NotNull CheckinProjectPanel panel, @NotNull CommitContext commitContext) {
    return new CheckinHandler() {
      @Override
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