// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.todo.nodes;

import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.todo.TodoFileDirAndModuleComparator;
import com.intellij.ide.todo.TodoTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class TodoTreeHelper {
  private final Project myProject;

  public static TodoTreeHelper getInstance(Project project) {
    return project.getService(TodoTreeHelper.class);
  }

  public TodoTreeHelper(final Project project) {
    myProject = project;
  }

  public void addPackagesToChildren(@NotNull ArrayList<? super AbstractTreeNode<?>> children,
                                    @Nullable Module module,
                                    @NotNull TodoTreeBuilder builder) {
    addDirsToChildren(collectContentRoots(module), children, builder);
  }

  protected @NotNull List<? extends VirtualFile> collectContentRoots(@Nullable Module module) {
    final List<VirtualFile> roots = new ArrayList<>();
    ContainerUtil.addAll(roots, module != null ?
                                ModuleRootManager.getInstance(module).getContentRoots() :
                                ProjectRootManager.getInstance(myProject).getContentRoots());
    return roots;
  }

  protected void addDirsToChildren(@NotNull List<? extends VirtualFile> roots,
                                   @NotNull ArrayList<? super AbstractTreeNode<?>> children,
                                   @NotNull TodoTreeBuilder builder) {
    final PsiManager psiManager = PsiManager.getInstance(myProject);
    for (VirtualFile dir : roots) {
      final PsiDirectory directory = psiManager.findDirectory(dir);
      if (directory == null) {
        continue;
      }
      final Iterator<PsiFile> files = builder.getFiles(directory);
      if (!files.hasNext()) continue;
      TodoDirNode dirNode = new TodoDirNode(myProject, directory, builder);
      if (!children.contains(dirNode)) {
        children.add(dirNode);
      }
    }
  }

  public Collection<AbstractTreeNode<?>> getDirectoryChildren(PsiDirectory psiDirectory, TodoTreeBuilder builder, boolean isFlatten) {
    ArrayList<AbstractTreeNode<?>> children = new ArrayList<>();
    if (!isFlatten || !skipDirectory(psiDirectory)) {
      final Iterator<PsiFile> iterator = builder.getFiles(psiDirectory);
      while (iterator.hasNext()) {
        final PsiFile psiFile = iterator.next();
        // Add files
        final PsiDirectory containingDirectory = psiFile.getContainingDirectory();
        TodoFileNode todoFileNode = new TodoFileNode(getProject(), psiFile, builder, false);
        if (psiDirectory.equals(containingDirectory) && !children.contains(todoFileNode)) {
          children.add(todoFileNode);
          continue;
        }
        // Add directories (find first ancestor directory that is in our psiDirectory)
        PsiDirectory _dir = psiFile.getContainingDirectory();
        while (_dir != null) {
          if (skipDirectory(_dir)){
            break;
          }
          final PsiDirectory parentDirectory = _dir.getParentDirectory();
          TodoDirNode todoDirNode = new TodoDirNode(getProject(), _dir, builder);
          if (parentDirectory != null && psiDirectory.equals(parentDirectory) && !children.contains(todoDirNode)) {
            children.add(todoDirNode);
            break;
          }
          _dir = parentDirectory;
        }
      }
    }
    else { // flatten packages
      final PsiDirectory parentDirectory = psiDirectory.getParentDirectory();
      if (
        parentDirectory == null ||
        !skipDirectory(parentDirectory) ||
        !ProjectRootManager.getInstance(getProject()).getFileIndex().isInContent(parentDirectory.getVirtualFile())
      ) {
        final Iterator<PsiFile> iterator = builder.getFiles(psiDirectory);
        while (iterator.hasNext()) {
          final PsiFile psiFile = iterator.next();
          // Add files
          TodoFileNode todoFileNode = new TodoFileNode(getProject(), psiFile, builder, false);
          if (psiDirectory.equals(psiFile.getContainingDirectory()) && !children.contains(todoFileNode)) {
            children.add(todoFileNode);
            continue;
          }
          // Add directories
          final PsiDirectory _dir = psiFile.getContainingDirectory();
          if (_dir == null || skipDirectory(_dir)){
            continue;
          }
          TodoDirNode todoDirNode = new TodoDirNode(getProject(), _dir, builder);
          if (PsiTreeUtil.isAncestor(psiDirectory, _dir, true) && !children.contains(todoDirNode) && !builder.isDirectoryEmpty(_dir)) {
            children.add(todoDirNode);
          }
        }
      }
      else {
        final Iterator<PsiFile> iterator = builder.getFiles(psiDirectory);
        while (iterator.hasNext()) {
          final PsiFile psiFile = iterator.next();
          final PsiDirectory containingDirectory = psiFile.getContainingDirectory();
          TodoFileNode todoFileNode = new TodoFileNode(getProject(), psiFile, builder, false);
          if (psiDirectory.equals(containingDirectory) && !children.contains(todoFileNode)) {
            children.add(todoFileNode);
          }
        }
      }
    }
   children.sort(TodoFileDirAndModuleComparator.INSTANCE);
   return children;
  }

  public boolean skipDirectory(PsiDirectory directory) {
    return false;
  }

  public @Nullable PsiElement getSelectedElement(Object userObject) {
    if (userObject instanceof TodoDirNode descriptor) {
      return descriptor.getValue();
    }

    else if (userObject instanceof TodoFileNode descriptor) {
      return descriptor.getValue();
    }
    return null;
  }

  public boolean contains(ProjectViewNode node, Object element) {
    return false;
  }


  public Project getProject() {
    return myProject;
  }
}
