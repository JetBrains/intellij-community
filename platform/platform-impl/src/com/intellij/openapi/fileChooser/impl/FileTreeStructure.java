/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

  public boolean isToBuildChildrenInBackground(final Object element) {
    return true;
  }

  public final boolean areHiddensShown() {
    return myShowHidden;
  }

  public final void showHiddens(final boolean showHidden) {
    myShowHidden = showHidden;
  }

  public final Object getRootElement() {
    return myRootElement;
  }

  public Object[] getChildElements(Object nodeElement) {
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


  @Nullable
  public Object getParentElement(Object element) {
    if (element instanceof FileElement) {

      final FileElement fileElement = (FileElement)element;

      final VirtualFile elementFile = getValidFile(fileElement);
      if (elementFile != null && myRootElement.getFile() != null && myRootElement.getFile().equals(elementFile)) {
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

      if (parent != null && parent.isValid() && parent.equals(myRootElement.getFile())) {
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

  public final void commit() { }

  public final boolean hasSomethingToCommit() {
    return false;
  }

  @NotNull
  public NodeDescriptor createDescriptor(Object element, NodeDescriptor parentDescriptor) {
    LOG.assertTrue(element instanceof FileElement, element.getClass().getName());
    VirtualFile file = ((FileElement)element).getFile();
    Icon closedIcon = file == null ? null : myChooserDescriptor.getIcon(file);
    String name = file == null ? null : myChooserDescriptor.getName(file);
    String comment = file == null ? null : myChooserDescriptor.getComment(file);

    return new FileNodeDescriptor(myProject, (FileElement)element, parentDescriptor, closedIcon, name, comment);
  }
}
