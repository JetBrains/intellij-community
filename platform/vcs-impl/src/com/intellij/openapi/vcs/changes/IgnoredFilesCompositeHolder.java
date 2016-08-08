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
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * @author irengrig
 *         Date: 2/10/11
 *         Time: 3:57 PM
 */
public class IgnoredFilesCompositeHolder implements IgnoredFilesHolder {
  private final Map<AbstractVcs, IgnoredFilesHolder> myHolderMap;
  private final Project myProject;
  private AbstractVcs myCurrentVcs;
  private final ProjectLevelVcsManager myVcsManager;
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.IgnoredFilesCompositeHolder");

  public IgnoredFilesCompositeHolder(final Project project) {
    super();
    myProject = project;
    myHolderMap = new HashMap<>();
    myVcsManager = ProjectLevelVcsManager.getInstance(myProject);
  }

  @Override
  public void cleanAll() {
    myHolderMap.clear();
  }

  @Override
  public void cleanAndAdjustScope(VcsModifiableDirtyScope scope) {
    final AbstractVcs vcs = scope.getVcs();
    if (myHolderMap.containsKey(vcs)) {
      myHolderMap.get(vcs).cleanAndAdjustScope(scope);
    }
  }

  @Override
  public FileHolder copy() {
    final IgnoredFilesCompositeHolder result = new IgnoredFilesCompositeHolder(myProject);
    for (Map.Entry<AbstractVcs, IgnoredFilesHolder> entry : myHolderMap.entrySet()) {
      result.myHolderMap.put(entry.getKey(), (IgnoredFilesHolder) entry.getValue().copy());
    }
    return result;
  }

  @Override
  public HolderType getType() {
    return HolderType.IGNORED;
  }

  @Nullable
  public IgnoredFilesHolder getAppropriateIgnoredHolder() {
    if (!myHolderMap.containsKey(myCurrentVcs)) {
      LOG.error("current vcs: " + myCurrentVcs);
      return null;
    }
    return myHolderMap.get(myCurrentVcs);
  }

  @Override
  public void addFile(VirtualFile file) {
    if (!myHolderMap.containsKey(myCurrentVcs)) {
      LOG.error("current vcs: " + myCurrentVcs + " file: " + file.getPath());
    }
    myHolderMap.get(myCurrentVcs).addFile(file);
  }

  @Override
  public boolean containsFile(VirtualFile file) {
    final AbstractVcs vcs = myVcsManager.getVcsFor(file);
    if (vcs == null) return false;
    final IgnoredFilesHolder ignoredFilesHolder = myHolderMap.get(vcs);
    return ignoredFilesHolder == null ? false : ignoredFilesHolder.containsFile(file);
  }

  @Override
  public Collection<VirtualFile> values() {
    if (myHolderMap.size() == 1) return myHolderMap.values().iterator().next().values();
    final HashSet<VirtualFile> result = new HashSet<>();
    for (IgnoredFilesHolder holder : myHolderMap.values()) {
      result.addAll(holder.values());
    }
    return result;
  }

  @Override
  public void notifyVcsStarted(AbstractVcs vcs) {
    myCurrentVcs = vcs;
    if (! myHolderMap.containsKey(vcs)) {
      myHolderMap.put(vcs, vcs.reportsIgnoredDirectories() ? new RecursiveFileHolder(myProject, HolderType.IGNORED) :
        new MapIgnoredFilesHolder(myProject));
    }
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof IgnoredFilesCompositeHolder)) {
      return false;
    }
    IgnoredFilesCompositeHolder other = (IgnoredFilesCompositeHolder) obj;
    return myHolderMap.equals(other.myHolderMap);
  }

  @Override
  public int hashCode() {
    return myHolderMap.hashCode();
  }

}
