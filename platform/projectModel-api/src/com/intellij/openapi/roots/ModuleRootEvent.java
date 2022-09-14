// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.RootsChangeRescanningInfo;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import org.jetbrains.annotations.NotNull;

import java.util.EventObject;
import java.util.List;

public abstract class ModuleRootEvent extends EventObject {

  protected ModuleRootEvent(@NotNull Project project) {
    super(project);
  }

  public abstract boolean isCausedByFileTypesChange();

  /**
   * If you migrate {@link ModuleRootListener} to {@link com.intellij.workspaceModel.ide.WorkspaceModelChangeListener},
   * you should still keep {@link ModuleRootListener} implementation in the following cases:
   * <ul>
   * <li> your code needs to know about changes in {@link SyntheticLibrary}, {@link AdditionalLibraryRootsProvider} or {DirectoryIndexExcludePolicy}.</li>
   * <li> your code needs to be notified about explicit calls of {@link ProjectRootManagerEx#makeRootsChange(Runnable, RootsChangeRescanningInfo)}</li>
   * <li> your code needs to know about creation/deletion of folders/files what are associate with {@link ContentEntry}. See also {@link com.intellij.util.indexing.EntityIndexingServiceEx#createWorkspaceEntitiesRootsChangedInfo(List)}
   * </ul>
   * <br/>
   * These APIs are going to be deprecated or made internal. But since there are still usages in IJ code and in plugins, you should make sure
   * your code works in these cases as well.
   * <br/>
   * If it's your case, add the following check to your {@link ModuleRootListener}:
   *  <pre>
   *  void rootsChanged(@NotNull ModuleRootEvent event) {
   *    if(event.isCausedByWorkspaceModelChangesOnly()) return;
   *  }
   *  </pre>
   *  This way it will only handle the legacy events, while new more granular Workspace events will be handled by your {@link com.intellij.workspaceModel.ide.WorkspaceModelChangeListener}.
   */
  public abstract boolean isCausedByWorkspaceModelChangesOnly();

  public @NotNull Project getProject() {
    return (Project)getSource();
  }
}
