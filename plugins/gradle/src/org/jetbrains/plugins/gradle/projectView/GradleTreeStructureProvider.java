/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.projectView;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.ProjectRootsUtil;
import com.intellij.ide.projectView.impl.nodes.*;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.util.Collection;

/**
 * @author Vladislav.Soroka
 * @since 2/4/2016
 */
public class GradleTreeStructureProvider implements TreeStructureProvider, DumbAware {
  @NotNull
  @Override
  public Collection<AbstractTreeNode> modify(@NotNull AbstractTreeNode parent,
                                             @NotNull Collection<AbstractTreeNode> children,
                                             ViewSettings settings) {
    Project project = parent.getProject();
    if (project == null) return children;

    if (parent instanceof ProjectViewProjectNode) {
      return getProjectNodeChildren(project, children);
    }

    if (parent instanceof ProjectViewModuleGroupNode) {
      Collection<AbstractTreeNode> modifiedChildren = ContainerUtil.newSmartList();
      for (AbstractTreeNode child : children) {
        if (child instanceof ProjectViewModuleNode) {
          Module module = ((ProjectViewModuleNode)child).getValue();
          if (!showUnderModuleGroup(module)) continue;

          GradleProjectViewModuleNode sourceSetNode = getGradleSourceSetNode(project, (ProjectViewModuleNode)child, settings);
          child = sourceSetNode != null ? sourceSetNode : child;
        }
        else if (child instanceof PsiDirectoryNode) {
          GradleSourceSetDirectoryNode sourceSetNode = getGradleSourceSetNode(project, (PsiDirectoryNode)child, settings);
          if (sourceSetNode != null && !showUnderModuleGroup(sourceSetNode.myModule)) continue;
          child = sourceSetNode != null ? sourceSetNode : child;
        }
        modifiedChildren.add(child);
      }
      return modifiedChildren;
    }

    if (parent instanceof GradleProjectViewModuleNode) {
      Module module = ((GradleProjectViewModuleNode)parent).getValue();
      String projectPath = ExternalSystemApiUtil.getExternalProjectPath(module);
      Collection<AbstractTreeNode> modifiedChildren = ContainerUtil.newSmartList();
      for (AbstractTreeNode child : children) {
        if (child instanceof PsiDirectoryNode) {
          PsiDirectory psiDirectory = ((PsiDirectoryNode)child).getValue();
          if (psiDirectory != null) {
            final VirtualFile virtualFile = psiDirectory.getVirtualFile();
            if (projectPath != null && FileUtil.isAncestor(projectPath, virtualFile.getPath(), false)) {
              continue;
            }
          }
        }
        modifiedChildren.add(child);
      }
      return modifiedChildren;
    }

    if (parent instanceof PsiDirectoryNode) {
      Collection<AbstractTreeNode> modifiedChildren = ContainerUtil.newSmartList();
      for (AbstractTreeNode child : children) {
        if (child instanceof PsiDirectoryNode) {
          GradleSourceSetDirectoryNode sourceSetNode = getGradleSourceSetNode(project, (PsiDirectoryNode)child, settings);
          child = sourceSetNode != null ? sourceSetNode : child;
        }
        modifiedChildren.add(child);
      }
      return modifiedChildren;
    }
    return children;
  }

  private static boolean showUnderModuleGroup(Module module) {
    if (ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, module)) {
      String projectPath = ExternalSystemApiUtil.getExternalProjectPath(module);
      for (VirtualFile root : ModuleRootManager.getInstance(module).getContentRoots()) {
        if (projectPath != null && !FileUtil.isAncestor(projectPath, root.getPath(), true)) {
          return true;
        }
      }
      return false;
    }
    return true;
  }

  @NotNull
  private static Collection<AbstractTreeNode> getProjectNodeChildren(@NotNull Project project,
                                                                     @NotNull Collection<AbstractTreeNode> children) {
    Collection<AbstractTreeNode> modifiedChildren = ContainerUtil.newSmartList();
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    for (AbstractTreeNode child : children) {
      Pair<VirtualFile, PsiDirectoryNode> parentNodePair = null;
      if (child instanceof ProjectViewModuleGroupNode) {
        final ProjectViewModuleGroupNode groupNode = (ProjectViewModuleGroupNode)child;
        final Collection<AbstractTreeNode> groupNodeChildren = groupNode.getChildren();
        for (final AbstractTreeNode node : groupNodeChildren) {
          if (node instanceof PsiDirectoryNode) {
            final PsiDirectoryNode psiDirectoryNode = (PsiDirectoryNode)node;
            final PsiDirectory psiDirectory = psiDirectoryNode.getValue();
            if (psiDirectory == null) {
              parentNodePair = null;
              break;
            }

            final VirtualFile virtualFile = psiDirectory.getVirtualFile();
            final Module module = fileIndex.getModuleForFile(virtualFile);
            if (!ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, module)) {
              parentNodePair = null;
              break;
            }

            if (parentNodePair == null || VfsUtilCore.isAncestor(virtualFile, parentNodePair.first, false)) {
              parentNodePair = Pair.pair(virtualFile, psiDirectoryNode);
            }
            else if (!VfsUtilCore.isAncestor(parentNodePair.first, virtualFile, false)) {
              parentNodePair = null;
              break;
            }
          }
          else {
            parentNodePair = null;
            break;
          }
        }
      }
      modifiedChildren.add(parentNodePair != null ? parentNodePair.second : child);
    }
    return modifiedChildren;
  }


  @Nullable
  private static GradleProjectViewModuleNode getGradleSourceSetNode(@NotNull Project project,
                                                                    @NotNull ProjectViewModuleNode moduleNode,
                                                                    ViewSettings settings) {
    Module module = moduleNode.getValue();
    String sourceSetName = GradleProjectResolverUtil.getSourceSetName(module);
    if (sourceSetName == null) return null;
    return new GradleProjectViewModuleNode(project, module, settings);
  }

  @Nullable
  private static GradleSourceSetDirectoryNode getGradleSourceSetNode(@NotNull Project project,
                                                                     @NotNull PsiDirectoryNode directoryNode,
                                                                     ViewSettings settings) {

    PsiDirectory psiDirectory = directoryNode.getValue();
    if (psiDirectory == null) return null;

    final VirtualFile virtualFile = psiDirectory.getVirtualFile();
    if (!ProjectRootsUtil.isModuleContentRoot(virtualFile, project)) return null;

    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final Module module = fileIndex.getModuleForFile(virtualFile);
    String sourceSetName = GradleProjectResolverUtil.getSourceSetName(module);
    if (sourceSetName == null) return null;
    return new GradleSourceSetDirectoryNode(project, psiDirectory, settings, module, sourceSetName, directoryNode.getFilter());
  }

  private static class GradleSourceSetDirectoryNode extends PsiDirectoryNode {
    private String mySourceSetName;
    private Module myModule;

    public GradleSourceSetDirectoryNode(Project project,
                                        PsiDirectory psiDirectory,
                                        ViewSettings settings,
                                        Module module,
                                        String sourceSetName,
                                        PsiFileSystemItemFilter filter) {
      super(project, psiDirectory, settings, filter);
      mySourceSetName = sourceSetName;
      myModule = module;
    }

    @Override
    protected boolean shouldShowModuleName() {
      return false;
    }

    @Override
    protected void updateImpl(PresentationData data) {
      super.updateImpl(data);
      PsiDirectory psiDirectory = getValue();
      assert psiDirectory != null;
      VirtualFile directoryFile = psiDirectory.getVirtualFile();
      if (StringUtil.isNotEmpty(mySourceSetName) &&
          !StringUtil.equalsIgnoreCase(mySourceSetName.replace("-", ""), directoryFile.getName().replace("-", ""))) {
        data.addText("[" + mySourceSetName + "]", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      }
    }
  }

  private static class GradleProjectViewModuleNode extends ProjectViewModuleNode {


    public GradleProjectViewModuleNode(Project project, Module value, ViewSettings viewSettings) {
      super(project, value, viewSettings);
    }

    @Override
    public void update(PresentationData presentation) {
      super.update(presentation);
      String sourceSetName = GradleProjectResolverUtil.getSourceSetName(getValue());
      if (sourceSetName != null) {
        presentation.setPresentableText(sourceSetName);
        presentation.addText(sourceSetName, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      }
    }

    @Override
    protected boolean showModuleNameInBold() {
      return false;
    }
  }
}
