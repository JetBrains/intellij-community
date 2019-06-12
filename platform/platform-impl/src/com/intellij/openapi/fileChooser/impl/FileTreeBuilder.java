// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileChooser.impl;

import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileElement;
import com.intellij.openapi.fileChooser.ex.RootFileElement;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.StatusBarProgress;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.util.Comparator;

/**
 * @author Yura Cangea
 *
 * @deprecated use {@link com.intellij.ui.tree.AsyncTreeModel} and {@link com.intellij.ui.tree.StructureTreeModel} instead.
 */
@ApiStatus.ScheduledForRemoval
@Deprecated
public class FileTreeBuilder extends AbstractTreeBuilder {
  private final FileChooserDescriptor myChooserDescriptor;

  public FileTreeBuilder(JTree tree,
                         DefaultTreeModel treeModel,
                         AbstractTreeStructure treeStructure,
                         Comparator<? super NodeDescriptor> comparator,
                         FileChooserDescriptor chooserDescriptor,
                         @SuppressWarnings("UnusedParameters") Runnable onInitialized) {
    super(tree, treeModel, treeStructure, comparator, false);
    myChooserDescriptor = chooserDescriptor;

    initRootNode();

    VirtualFileManager.getInstance().addVirtualFileListener(new VirtualFileListener() {
      @Override
      public void propertyChanged(@NotNull VirtualFilePropertyEvent event) {
        doUpdate();
      }

      @Override
      public void fileCreated(@NotNull VirtualFileEvent event) {
        doUpdate();
      }

      @Override
      public void fileDeleted(@NotNull VirtualFileEvent event) {
        doUpdate();
      }

      @Override
      public void fileMoved(@NotNull VirtualFileMoveEvent event) {
        doUpdate();
      }

      private void doUpdate() {
        queueUpdateFrom(getRootNode(), false);
      }
    }, this);
  }

  @Override
  protected boolean isAlwaysShowPlus(NodeDescriptor nodeDescriptor) {
    Object element = nodeDescriptor.getElement();
    if (element != null) {
      FileElement descriptor = (FileElement)element;
      VirtualFile file = descriptor.getFile();
      if (file != null) {
        if (myChooserDescriptor.isChooseJarContents() && FileElement.isArchive(file)) {
          return true;
        }
        return file.isDirectory();
      }
    }
    return true;
  }

  @Override
  protected boolean isAutoExpandNode(NodeDescriptor nodeDescriptor) {
    if (nodeDescriptor.getElement() instanceof RootFileElement) {
      return true;
    }
    else if (!SystemInfo.isWindows) {
      NodeDescriptor parent = nodeDescriptor.getParentDescriptor();
      return parent != null && parent.getElement() instanceof RootFileElement;
    }

    return false;
  }

  @Override
  protected ProgressIndicator createProgressIndicator() {
    return new StatusBarProgress();
  }
}
