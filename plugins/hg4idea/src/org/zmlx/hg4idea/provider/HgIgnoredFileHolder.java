/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.zmlx.hg4idea.provider;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.changes.FileHolder;
import com.intellij.openapi.vcs.changes.VcsIgnoredFilesHolder;
import com.intellij.openapi.vcs.changes.VcsModifiableDirtyScope;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgVcs;

import java.util.Collection;
import java.util.Set;

public class HgIgnoredFileHolder implements VcsIgnoredFilesHolder {
  private final Project myProject;
  private HgVcs myVcs;
  private final Set<VirtualFile> mySet;
  private final Set<VirtualFile> myVcsIgnoredSet;

  public HgIgnoredFileHolder(Project project) {
    myProject = project;
    myVcs = HgVcs.getInstance(myProject);
    mySet = ContainerUtil.newHashSet();
    myVcsIgnoredSet = ContainerUtil.newHashSet();   //collect ignored files from VcsChangeProvider -> processIgnored
  }

  @Override
  public void addFile(VirtualFile file) {
    // todo fix more. take from x0x branch
    //LOG.assertTrue(! file.isDirectory());
    mySet.add(file);
    myVcsIgnoredSet.add(file);
  }

  @Override
  public boolean containsFile(VirtualFile file) {
    return mySet.contains(file) || myVcsIgnoredSet.contains(file);
  }

  @Override
  public Collection<VirtualFile> values() {
    return ContainerUtil.union(mySet, myVcsIgnoredSet);
  }
  
 @Override
  public void cleanAndAdjustScope(final VcsModifiableDirtyScope scope) {
  }

  @Override
  public void cleanAll() {
    mySet.clear();
    myVcsIgnoredSet.clear();// not sure we need to delete
  }

  @Override
  public FileHolder copy() {
    final HgIgnoredFileHolder result = new HgIgnoredFileHolder(myProject);
    result.mySet.addAll(mySet);
    result.myVcsIgnoredSet.addAll(myVcsIgnoredSet);
    return result;
  }

  @Override
  public HolderType getType() {
    return HolderType.IGNORED;
  }

  @Override
  public void notifyVcsStarted(AbstractVcs scope) {
    cleanAll();
  }

  @NotNull
  @Override
  public AbstractVcs getVcs() {
    return myVcs;
  }
}
