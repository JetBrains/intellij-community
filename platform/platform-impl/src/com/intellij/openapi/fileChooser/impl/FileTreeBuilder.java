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

/**
 * @author Yura Cangea
 */
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.util.Comparator;

public class FileTreeBuilder extends AbstractTreeBuilder {
  private final FileChooserDescriptor myChooserDescriptor;

  private final Runnable myOnInitialized;

  public FileTreeBuilder(JTree tree,
                         DefaultTreeModel treeModel,
                         AbstractTreeStructure treeStructure,
                         Comparator<NodeDescriptor> comparator,
                         FileChooserDescriptor chooserDescriptor,
                         @Nullable Runnable onInitialized) {
    super(tree, treeModel, treeStructure, comparator, false);

    myOnInitialized = onInitialized;

    myChooserDescriptor = chooserDescriptor;
    initRootNode();

    installVirtualFileListener();
  }


  protected void onRootNodeInitialized() {
    if (myOnInitialized != null) {
      myOnInitialized.run();
    }
  }

  private void installVirtualFileListener() {

    VirtualFileAdapter myVirtualFileListener = new VirtualFileAdapter() {
      public void propertyChanged(VirtualFilePropertyEvent event) {
        getUpdater().addSubtreeToUpdate(getRootNode());
      }

      public void fileCreated(VirtualFileEvent event) {
        getUpdater().addSubtreeToUpdate(getRootNode());
      }

      public void fileDeleted(VirtualFileEvent event) {
        getUpdater().addSubtreeToUpdate(getRootNode());
      }

      public void fileMoved(VirtualFileMoveEvent event) {
        getUpdater().addSubtreeToUpdate(getRootNode());
      }
    };

    VirtualFileManager.getInstance().addVirtualFileListener(myVirtualFileListener,this);
  }


  protected boolean isAlwaysShowPlus(NodeDescriptor nodeDescriptor) {
    final Object element = nodeDescriptor.getElement();
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

  protected boolean isAutoExpandNode(NodeDescriptor nodeDescriptor) {
    if (nodeDescriptor.getElement() instanceof RootFileElement) {
      return true;
    } else if (!SystemInfo.isWindows) {
      NodeDescriptor parent = nodeDescriptor.getParentDescriptor();
      return parent != null && parent.getElement() instanceof RootFileElement;
    }

    return false;
  }

  @NotNull
  protected ProgressIndicator createProgressIndicator() {
    return new StatusBarProgress();
  }
}
