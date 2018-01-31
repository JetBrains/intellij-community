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

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultTreeModel;
import java.io.File;
import java.util.HashMap;

public class ChangesModuleGroupingPolicy implements ChangesGroupingPolicy {
  @NotNull private final Project myProject;
  @NotNull private final DefaultTreeModel myModel;
  @NotNull private final HashMap<Module, ChangesBrowserNode> myModuleCache = new HashMap<>();

  public static final String PROJECT_ROOT_TAG = "<Project Root>";

  public ChangesModuleGroupingPolicy(@NotNull Project project, @NotNull DefaultTreeModel model) {
    myProject = project;
    myModel = model;
  }

  @Override
  @Nullable
  public ChangesBrowserNode getParentNodeFor(@NotNull StaticFilePath nodePath, @NotNull ChangesBrowserNode subtreeRoot) {
    if (myProject.isDefault()) return null;

    ProjectFileIndex index = ProjectRootManager.getInstance(myProject).getFileIndex();

    VirtualFile vFile = nodePath.getVf();
    if (vFile == null) {
      vFile = LocalFileSystem.getInstance().findFileByIoFile(new File(nodePath.getPath()));
    }
    boolean hideExcludedFiles = Registry.is("ide.hide.excluded.files");
    if (vFile != null && Comparing.equal(vFile, index.getContentRootForFile(vFile, hideExcludedFiles))) {
      Module module = index.getModuleForFile(vFile, hideExcludedFiles);
      return getNodeForModule(module, subtreeRoot);
    }
    return null;
  }

  @NotNull
  private ChangesBrowserNode getNodeForModule(@Nullable Module module, @NotNull ChangesBrowserNode subtreeRoot) {
    ChangesBrowserNode node = myModuleCache.get(module);
    if (node == null) {
      node = module == null ? ChangesBrowserNode.create(myProject, PROJECT_ROOT_TAG) : new ChangesBrowserModuleNode(module);
      myModel.insertNodeInto(node, subtreeRoot, subtreeRoot.getChildCount());
      myModuleCache.put(module, node);
    }
    return node;
  }
}
