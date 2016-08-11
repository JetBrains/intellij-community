/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;

import java.util.Collection;
import java.util.Set;

/**
 * @author irengrig
 *         Date: 2/10/11
 *         Time: 4:20 PM
 */
public class MapIgnoredFilesHolder extends AbstractIgnoredFilesHolder {
  private final static Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.MapIgnoredFilesHolder");

  private final Set<VirtualFile> mySet;
  private final Set<VirtualFile> myVcsIgnoredSet;
  private final Project myProject;

  public MapIgnoredFilesHolder(Project project) {
    super(project);
    myProject = project;
    mySet = new THashSet<>();
    myVcsIgnoredSet = new THashSet<>();    //collect ignored files from VcsChangeProvider -> processIgnored
  }

  @Override
  protected void removeFile(VirtualFile file) {
    mySet.remove(file);
    myVcsIgnoredSet.remove(file);
  }

  @Override
  protected Collection<VirtualFile> keys() {
    // if mySet has a big size ->  idea will process all of this on every typing. see cleanAndAdjustScope() in AbstractIgnoredFilesHolder
    return mySet;
  }

  @Override
  public void addFile(VirtualFile file) {
    // todo fix more. take from x0x branch
    //LOG.assertTrue(! file.isDirectory());
    mySet.add(file);
  }

  public void addByVcsChangeProvider(VirtualFile file) {
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
  public void cleanAll() {
    mySet.clear();
    myVcsIgnoredSet.clear();// not sure we need to delete
  }

  @Override
  public FileHolder copy() {
    final MapIgnoredFilesHolder result = new MapIgnoredFilesHolder(myProject);
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
  }
}
