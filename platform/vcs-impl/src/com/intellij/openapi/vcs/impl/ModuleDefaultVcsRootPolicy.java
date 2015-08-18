/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.openapi.vcs.impl;

import com.intellij.ide.impl.ProjectUtil;
import com.intellij.lifecycle.PeriodicalTasksCloser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx;
import com.intellij.openapi.vcs.impl.projectlevelman.NewMappings;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * @author yole
 */
public class ModuleDefaultVcsRootPolicy extends DefaultVcsRootPolicy {
  private static final Logger LOG = Logger.getInstance(ModuleDefaultVcsRootPolicy.class);
  private final Project myProject;
  private final VirtualFile myBaseDir;
  private final ModuleManager myModuleManager;

  public ModuleDefaultVcsRootPolicy(final Project project) {
    myProject = project;
    myBaseDir = project.getBaseDir();
    myModuleManager = ModuleManager.getInstance(myProject);
  }

  @Override
  public void addDefaultVcsRoots(final NewMappings mappingList, @NotNull final String vcsName, final List<VirtualFile> result) {
    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(myProject);
    if (myBaseDir != null && vcsName.equals(mappingList.getVcsFor(myBaseDir))) {
      final AbstractVcs vcsFor = vcsManager.getVcsFor(myBaseDir);
      if (vcsFor != null && vcsName.equals(vcsFor.getName())) {
        result.add(myBaseDir);
      }
    }
    if (ProjectUtil.isDirectoryBased(myProject) && myBaseDir != null) {
      final VirtualFile ideaDir = myBaseDir.findChild(Project.DIRECTORY_STORE_FOLDER);
      if (ideaDir != null && ideaDir.isValid() && ideaDir.isDirectory()) {
        final AbstractVcs vcsFor = vcsManager.getVcsFor(ideaDir);
        if (vcsFor != null && vcsName.equals(vcsFor.getName())) {
          result.add(ideaDir);
        }
      }
    }
    // assertion for read access inside
    final Module[] modules = ApplicationManager.getApplication().runReadAction(new Computable<Module[]>() {
      @Override
      public Module[] compute() {
        return myModuleManager.getModules();
      }
    });
    for(Module module: modules) {
      final VirtualFile[] files = ModuleRootManager.getInstance(module).getContentRoots();
      for(VirtualFile file: files) {
        // if we're currently processing moduleAdded notification, getModuleForFile() will return null, so we pass the module
        // explicitly (we know it anyway)
        VcsDirectoryMapping mapping = mappingList.getMappingFor(file, module);
        final String mappingVcs = mapping != null ? mapping.getVcs() : null;
        if (vcsName.equals(mappingVcs) && !result.contains(file)) {
          result.add(file);
        }
      }
    }
  }

  @Override
  public boolean matchesDefaultMapping(final VirtualFile file, final Object matchContext) {
    if (matchContext != null) {
      return true;
    }
    return myBaseDir != null && VfsUtilCore.isAncestor(myBaseDir, file, false);
  }

  @Override
  @Nullable
  public Object getMatchContext(final VirtualFile file) {
    return ModuleUtilCore.findModuleForFile(file, myProject);
  }

  @Override
  @Nullable
  public VirtualFile getVcsRootFor(final VirtualFile file) {
    if (myBaseDir != null && PeriodicalTasksCloser.getInstance().safeGetService(myProject, FileIndexFacade.class)
      .isValidAncestor(myBaseDir, file)) {
      return myBaseDir;
    }
    final VirtualFile contentRoot = ProjectRootManager.getInstance(myProject).getFileIndex().getContentRootForFile(file, Registry.is("ide.hide.excluded.files"));
    if (contentRoot != null) {
      return contentRoot;
    }
    if (ProjectUtil.isDirectoryBased(myProject) && (myBaseDir != null)) {
      final VirtualFile ideaDir = myBaseDir.findChild(Project.DIRECTORY_STORE_FOLDER);
      if (ideaDir != null && ideaDir.isValid() && ideaDir.isDirectory()) {
        if (VfsUtilCore.isAncestor(ideaDir, file, false)) {
          return ideaDir;
        }
      }
    }
    return null;
  }

  @NotNull
  @Override
  public Collection<VirtualFile> getDirtyRoots() {
    Collection<VirtualFile> dirtyRoots = ContainerUtil.newHashSet();

    if (ProjectUtil.isDirectoryBased(myProject) && myBaseDir != null) {
      VirtualFile ideaDir = myBaseDir.findChild(Project.DIRECTORY_STORE_FOLDER);
      if (ideaDir != null) {
        dirtyRoots.add(ideaDir);
      }
      else {
        LOG.warn(".idea was not found for base dir [" + myBaseDir.getPath() + "]");
      }
    }

    ContainerUtil.addAll(dirtyRoots, getContentRoots());

    String defaultMapping = ((ProjectLevelVcsManagerEx)ProjectLevelVcsManager.getInstance(myProject)).haveDefaultMapping();
    boolean haveDefaultMapping = !StringUtil.isEmpty(defaultMapping);
    if (haveDefaultMapping && myBaseDir != null) {
      dirtyRoots.add(myBaseDir);
    }
    return dirtyRoots;
  }

  @NotNull
  private Collection<VirtualFile> getContentRoots() {
    return ApplicationManager.getApplication().runReadAction(new Computable<List<VirtualFile>>() {
      @Override
      public List<VirtualFile> compute() {
        List<VirtualFile> contentRoots = ContainerUtil.newArrayList();
        for (Module module : myModuleManager.getModules()) {
          ContainerUtil.addAll(contentRoots, ModuleRootManager.getInstance(module).getContentRoots());
        }
        return contentRoots;
      }
    });
  }
}
