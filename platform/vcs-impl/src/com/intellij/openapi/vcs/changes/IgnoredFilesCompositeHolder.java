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

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IgnoredFilesCompositeHolder implements IgnoredFilesHolder {
  private final Map<AbstractVcs, IgnoredFilesHolder> myVcsIgnoredHolderMap;
  private IgnoredFilesHolder myIdeIgnoredFilesHolder;
  private final Project myProject;
  private AbstractVcs myCurrentVcs;
  private final ProjectLevelVcsManager myVcsManager;

  public IgnoredFilesCompositeHolder(final Project project) {
    super();
    myProject = project;
    myVcsIgnoredHolderMap = new HashMap<>();
    myIdeIgnoredFilesHolder = new RecursiveFileHolder<>(myProject, HolderType.IGNORED);
    myVcsManager = ProjectLevelVcsManager.getInstance(myProject);
  }

  @Override
  public void cleanAll() {
    myVcsIgnoredHolderMap.values().forEach(IgnoredFilesHolder::cleanAll);
    myVcsIgnoredHolderMap.clear();
    myIdeIgnoredFilesHolder.cleanAll();
  }

  @Override
  public void cleanAndAdjustScope(VcsModifiableDirtyScope scope) {
    final AbstractVcs vcs = scope.getVcs();
    if (myVcsIgnoredHolderMap.containsKey(vcs)) {
      myVcsIgnoredHolderMap.get(vcs).cleanAndAdjustScope(scope);
    }
    myIdeIgnoredFilesHolder.cleanAndAdjustScope(scope);
  }

  @Override
  public FileHolder copy() {
    final IgnoredFilesCompositeHolder result = new IgnoredFilesCompositeHolder(myProject);
    for (Map.Entry<AbstractVcs, IgnoredFilesHolder> entry : myVcsIgnoredHolderMap.entrySet()) {
      result.myVcsIgnoredHolderMap.put(entry.getKey(), (IgnoredFilesHolder)entry.getValue().copy());
    }
    result.myIdeIgnoredFilesHolder = (IgnoredFilesHolder)myIdeIgnoredFilesHolder.copy();
    return result;
  }

  @Override
  public HolderType getType() {
    return HolderType.IGNORED;
  }

  @Override
  public void addFile(VirtualFile file) {
    myIdeIgnoredFilesHolder.addFile(file);
  }

  public boolean isInUpdatingMode() {
    return myVcsIgnoredHolderMap.values().stream()
      .anyMatch((holder) -> (holder instanceof VcsIgnoredFilesHolder) && ((VcsIgnoredFilesHolder)holder).isInUpdatingMode());
  }

  @Override
  public boolean containsFile(VirtualFile file) {
    if (myIdeIgnoredFilesHolder.containsFile(file)) return true;
    final AbstractVcs vcs = myVcsManager.getVcsFor(file);
    if (vcs == null) return false;
    final IgnoredFilesHolder ignoredFilesHolder = myVcsIgnoredHolderMap.get(vcs);
    return ignoredFilesHolder != null && ignoredFilesHolder.containsFile(file);
  }

  @Override
  public Collection<VirtualFile> values() {
    final HashSet<VirtualFile> result = ContainerUtil.newHashSet();
    result.addAll(myIdeIgnoredFilesHolder.values());
    result.addAll(
      myVcsIgnoredHolderMap.values().stream().map(IgnoredFilesHolder::values).flatMap(set -> set.stream()).collect(Collectors.toSet()));
    return result;
  }

  @Override
  public int getDirNum() {
    return myIdeIgnoredFilesHolder.getDirNum() + myVcsIgnoredHolderMap.values().stream().mapToInt(IgnoredFilesHolder::getDirNum).sum();
  }

  @Override
  public int getFilesNum() {
    return myIdeIgnoredFilesHolder.getFilesNum() + myVcsIgnoredHolderMap.values().stream().mapToInt(IgnoredFilesHolder::getFilesNum).sum();
  }

  @Override
  public void notifyVcsStarted(AbstractVcs vcs) {
    myCurrentVcs = vcs;
    if (myVcsIgnoredHolderMap.containsKey(vcs)) return;

    IgnoredFilesHolder ignoredFilesHolder =
      ObjectUtils.chooseNotNull(getHolderFromEP(vcs, myProject), new RecursiveFileHolder<>(myProject, HolderType.IGNORED));
    ignoredFilesHolder.notifyVcsStarted(vcs);
    myVcsIgnoredHolderMap.put(vcs, ignoredFilesHolder);
  }

  @Nullable
  public IgnoredFilesHolder getActiveVcsHolder() {
    return getIgnoredHolderByVcs(myCurrentVcs);
  }

  @Nullable
  private IgnoredFilesHolder getIgnoredHolderByVcs(AbstractVcs vcs) {
    if (!myVcsIgnoredHolderMap.containsKey(vcs)) return null;
    return myVcsIgnoredHolderMap.get(vcs);
  }


  @Nullable
  private static VcsIgnoredFilesHolder getHolderFromEP(AbstractVcs vcs, @NotNull Project project) {
    Optional<VcsIgnoredFilesHolder> ignoredFilesHolder =
      Stream.of(Extensions.getExtensions(VcsIgnoredFilesHolder.VCS_IGNORED_FILES_HOLDER_EP, project))
        .filter(holder -> holder.getVcs().equals(vcs))
        .findFirst();
    return ignoredFilesHolder.isPresent() ? ignoredFilesHolder.get() : null;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof IgnoredFilesCompositeHolder)) {
      return false;
    }
    IgnoredFilesCompositeHolder other = (IgnoredFilesCompositeHolder)obj;
    return myVcsIgnoredHolderMap.equals(other.myVcsIgnoredHolderMap) && myIdeIgnoredFilesHolder.equals(other.myIdeIgnoredFilesHolder);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myVcsIgnoredHolderMap, myIdeIgnoredFilesHolder);
  }
}
