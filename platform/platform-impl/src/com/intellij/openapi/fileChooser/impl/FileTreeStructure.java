// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.fileChooser.impl;

import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileElement;
import com.intellij.openapi.fileChooser.ex.FileNodeDescriptor;
import com.intellij.openapi.fileChooser.ex.RootFileElement;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Yura Cangea
 */
public class FileTreeStructure extends AbstractTreeStructure {
  private static final Logger LOG = Logger.getInstance(FileTreeStructure.class);

  private final RootFileElement myRootElement;
  private final FileChooserDescriptor myChooserDescriptor;
  private boolean myShowHidden;
  private final Project myProject;

  public FileTreeStructure(Project project, FileChooserDescriptor chooserDescriptor) {
    myProject = project;
    List<VirtualFile> rootFiles = chooserDescriptor.getRoots(); // Returns RandomAccess collection
    String name = rootFiles.size() == 1 && rootFiles.get(0) != null ? rootFiles.get(0).getPresentableUrl() : chooserDescriptor.getTitle();
    myRootElement = new RootFileElement(rootFiles, name, chooserDescriptor.isShowFileSystemRoots());
    myChooserDescriptor = chooserDescriptor;
    myShowHidden = myChooserDescriptor.isShowHiddenFiles();
  }

  @Override
  public boolean isToBuildChildrenInBackground(@NotNull final Object element) {
    return true;
  }

  public final boolean areHiddenShown() {
    return myShowHidden;
  }

  public final void showHidden(final boolean showHidden) {
    myShowHidden = showHidden;
  }

  @Override
  public final @NotNull Object getRootElement() {
    return myRootElement;
  }

  @Override
  public Object @NotNull [] getChildElements(@NotNull Object nodeElement) {
    if (!(nodeElement instanceof FileElement element)) {
      return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
    }

    VirtualFile file = element.getFile();

    if (file == null || !file.isValid()) {
      if (element == myRootElement) {
        return myRootElement.getChildren();
      }
      return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
    }

    VirtualFile[] children = null;

    if (element.isArchive() && myChooserDescriptor.isChooseJarContents()) {
      String path = file.getPath();
      if (!(file.getFileSystem() instanceof JarFileSystem)) {
        file = JarFileSystem.getInstance().findFileByPath(path + JarFileSystem.JAR_SEPARATOR);
      }
      if (file != null) {
        children = file.getChildren();
      }
    }
    else {
      children = file.getChildren();
    }

    if (children == null) {
      return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
    }

    Set<FileElement> childrenSet = new HashSet<>();
    for (VirtualFile child : children) {
      if (myChooserDescriptor.isFileVisible(child, myShowHidden)) {
        final FileElement childElement = new FileElement(child, child.getName());
        childElement.setParent(element);
        childrenSet.add(childElement);
      }
    }
    return ArrayUtil.toObjectArray(childrenSet);
  }


  @Override
  public @Nullable Object getParentElement(@NotNull Object element) {
    if (element instanceof FileElement fileElement) {

      final VirtualFile elementFile = getValidFile(fileElement);
      VirtualFile rootElementFile = myRootElement.getFile();
      if (rootElementFile != null && rootElementFile.equals(elementFile)) {
        return null;
      }

      final VirtualFile parentElementFile = getValidFile(fileElement.getParent());

      if (elementFile != null && parentElementFile != null) {
        final VirtualFile parentFile = elementFile.getParent();
        if (parentElementFile.equals(parentFile)) return fileElement.getParent();
      }

      VirtualFile file = fileElement.getFile();
      if (file == null) return null;
      VirtualFile parent = file.getParent();
      if (parent != null && parent.getFileSystem() instanceof JarFileSystem && parent.getParent() == null) {
        // parent of jar contents should be local jar file
        String localPath = parent.getPath().substring(0, parent.getPath().length() - JarFileSystem.JAR_SEPARATOR.length());
        parent = LocalFileSystem.getInstance().findFileByPath(localPath);
      }
      if (parent == null || parent.isValid() && parent.equals(rootElementFile)) {
        return myRootElement;
      }

      return new FileElement(parent, parent.getName());
    }

    return null;
  }

  private static @Nullable VirtualFile getValidFile(FileElement element) {
    if (element == null) return null;
    final VirtualFile file = element.getFile();
    return file != null && file.isValid() ? file : null;
  }

  @Override
  public final void commit() { }

  @Override
  public final boolean hasSomethingToCommit() {
    return false;
  }

  @Override
  public @NotNull NodeDescriptor<?> createDescriptor(@NotNull Object element, NodeDescriptor parentDescriptor) {
    LOG.assertTrue(element instanceof FileElement, element.getClass().getName());
    VirtualFile file = ((FileElement)element).getFile();
    Icon closedIcon = file == null ? null : myChooserDescriptor.getIcon(file);
    String name = file == null ? null : myChooserDescriptor.getName(file);
    String comment = file == null ? null : myChooserDescriptor.getComment(file);
    return new FileNodeDescriptor(myProject, (FileElement)element, parentDescriptor, closedIcon, name, comment);
  }
}
