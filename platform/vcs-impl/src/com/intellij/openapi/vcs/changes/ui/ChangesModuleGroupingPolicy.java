// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultTreeModel;
import java.util.HashMap;

import static com.intellij.openapi.vcs.changes.ui.DirectoryChangesGroupingPolicy.DIRECTORY_POLICY;

public class ChangesModuleGroupingPolicy implements ChangesGroupingPolicy {
  @NotNull private final Project myProject;
  @NotNull private final DefaultTreeModel myModel;
  @NotNull private final HashMap<Module, ChangesBrowserNode> myModuleCache = new HashMap<>();
  @NotNull private final ProjectFileIndex myIndex;

  public static final String PROJECT_ROOT_TAG = "<Project Root>";

  public ChangesModuleGroupingPolicy(@NotNull Project project, @NotNull DefaultTreeModel model) {
    myProject = project;
    myModel = model;
    myIndex = ProjectFileIndex.getInstance(myProject);
  }

  @Override
  @Nullable
  public ChangesBrowserNode getParentNodeFor(@NotNull StaticFilePath nodePath, @NotNull ChangesBrowserNode subtreeRoot) {
    if (myProject.isDefault()) return null;

    VirtualFile vFile = nodePath.resolve();
    if (vFile != null && Comparing.equal(vFile, myIndex.getContentRootForFile(vFile, hideExcludedFiles()))) {
      Module module = myIndex.getModuleForFile(vFile, hideExcludedFiles());
      return getNodeForModule(module, nodePath, subtreeRoot);
    }
    return null;
  }

  @NotNull
  private ChangesBrowserNode getNodeForModule(@Nullable Module module,
                                              @NotNull StaticFilePath nodePath,
                                              @NotNull ChangesBrowserNode subtreeRoot) {
    ChangesBrowserNode node = myModuleCache.get(module);
    if (node == null) {
      DirectoryChangesGroupingPolicy policy = DIRECTORY_POLICY.get(subtreeRoot);
      ChangesBrowserNode<?> parent =
        policy != null && !isTopLevel(nodePath) ? policy.getParentNodeInternal(nodePath, subtreeRoot) : subtreeRoot;

      node = module == null ? ChangesBrowserNode.create(myProject, PROJECT_ROOT_TAG) : new ChangesBrowserModuleNode(module);
      myModel.insertNodeInto(node, parent, parent.getChildCount());
      myModuleCache.put(module, node);
    }
    return node;
  }

  private boolean isTopLevel(@NotNull StaticFilePath nodePath) {
    StaticFilePath parentPath = nodePath.getParent();
    VirtualFile parentFile = parentPath != null ? parentPath.resolve() : null;

    return parentFile == null || myIndex.getContentRootForFile(parentFile, hideExcludedFiles()) == null;
  }

  private static boolean hideExcludedFiles() {
    return Registry.is("ide.hide.excluded.files");
  }
}
