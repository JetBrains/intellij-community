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
import com.intellij.util.containers.hash.HashSet;

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
  private final Project myProject;

  public MapIgnoredFilesHolder(Project project) {
    super(project);
    myProject = project;
    mySet = new HashSet<VirtualFile>();
  }

  @Override
  protected void removeFile(VirtualFile file) {
    mySet.remove(file);
  }

  @Override
  protected Collection<VirtualFile> keys() {
    return mySet;
  }

  @Override
  public void addFile(VirtualFile file) {
    // todo fix more. take from x0x branch
    //LOG.assertTrue(! file.isDirectory());
    mySet.add(file);
  }

  @Override
  public boolean containsFile(VirtualFile file) {
    return mySet.contains(file);
  }

  @Override
  public Collection<VirtualFile> values() {
    return mySet;
  }

  @Override
  public void cleanAll() {
    mySet.clear();
  }

  @Override
  public FileHolder copy() {
    final MapIgnoredFilesHolder result = new MapIgnoredFilesHolder(myProject);
    result.mySet.addAll(mySet);
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
