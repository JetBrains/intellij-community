// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Yura Cangea
 */
public class FileTreeStructure extends AbstractTreeStructure {
  private static final Logger LOG = Logger.getInstance("#com.intellij.chooser.FileTreeStructure");

  private final RootFileElement myRootElement;
  private final FileChooserDescriptor myChooserDescriptor;
  private boolean myShowHidden;
  private final Project myProject;

  public FileTreeStructure(Project project, FileChooserDescriptor chooserDescriptor) {
    myProject = project;
    final VirtualFile[] rootFiles = VfsUtilCore.toVirtualFileArray(chooserDescriptor.getRoots());
    final String name = rootFiles.length == 1 && rootFiles[0] != null ? rootFiles[0].getPresentableUrl() : chooserDescriptor.getTitle();
    myRootElement = new RootFileElement(rootFiles, name, chooserDescriptor.isShowFileSystemRoots());
    myChooserDescriptor = chooserDescriptor;
    myShowHidden = myChooserDescriptor.isShowHiddenFiles();
  }

  @Override
  public boolean isToBuildChildrenInBackground(@NotNull final Object element) {
    return true;
  }

  public final boolean areHiddensShown() {
    return myShowHidden;
  }

  public final void showHiddens(final boolean showHidden) {
    myShowHidden = showHidden;
  }

  @NotNull
  @Override
  public final Object getRootElement() {
    return myRootElement;
  }

  @NotNull
  @Override
  public Object[] getChildElements(@NotNull Object nodeElement) {
    if (!(nodeElement instanceof FileElement)) {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    FileElement element = (FileElement)nodeElement;
    VirtualFile file = element.getFile();

    if (file == null || !file.isValid()) {
      if (element == myRootElement) {
        return myRootElement.getChildren();
      }
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
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
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
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
  @Nullable
  public Object getParentElement(@NotNull Object element) {
    if (element instanceof FileElement) {

      final FileElement fileElement = (FileElement)element;

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
        String localPath = parent.getPath().substring(0,
                                                      parent.getPath().length() - JarFileSystem.JAR_SEPARATOR.length());
        parent = LocalFileSystem.getInstance().findFileByPath(localPath);
      }

      if (parent != null && parent.isValid() && parent.equals(rootElementFile)) {
        return myRootElement;
      }

      if (parent == null) {
        return myRootElement;
      }
      return new FileElement(parent, parent.getName());
    }
    return null;
  }

  @Nullable
  private static VirtualFile getValidFile(FileElement element) {
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
  @NotNull
  public NodeDescriptor createDescriptor(@NotNull Object element, NodeDescriptor parentDescriptor) {
    LOG.assertTrue(element instanceof FileElement, element.getClass().getName());
    VirtualFile file = ((FileElement)element).getFile();
    Icon closedIcon = file == null ? null : myChooserDescriptor.getIcon(file);
    String name = file == null ? null : myChooserDescriptor.getName(file);
    String comment = file == null ? null : myChooserDescriptor.getComment(file);

    return new FileNodeDescriptor(myProject, (FileElement)element, parentDescriptor, closedIcon, name, comment);
  }
}
