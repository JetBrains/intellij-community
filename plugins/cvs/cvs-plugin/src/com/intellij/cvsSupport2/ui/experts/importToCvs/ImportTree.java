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
package com.intellij.cvsSupport2.ui.experts.importToCvs;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.cvsIgnore.IgnoredFilesInfo;
import com.intellij.cvsSupport2.cvsIgnore.IgnoredFilesInfoImpl;
import com.intellij.cvsSupport2.ui.experts.CvsWizard;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.fileChooser.FileElement;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.Nullable;
import org.netbeans.lib.cvsclient.file.AbstractFileObject;
import org.netbeans.lib.cvsclient.file.ICvsFileSystem;
import org.netbeans.lib.cvsclient.util.IIgnoreFileFilter;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.io.File;
import java.util.Collection;
import java.util.Map;

/**
 * @author lesya
 */
public class ImportTree extends NodeRenderer {
  private final Collection<VirtualFile> myExcludedFiles = new HashSet<VirtualFile>();
  private final Project myProject;
  private final FileSystemTree myFileSystemTree;
  private final CvsWizard myWizard;

  public ImportTree(@Nullable Project project, FileSystemTree fileSystemTree, CvsWizard wizard) {
    myProject = project;
    myFileSystemTree = fileSystemTree;
    myWizard = wizard;
  }

  public void customizeCellRenderer(JTree tree,
                                    Object value,
                                    boolean selected,
                                    boolean expanded,
                                    boolean leaf,
                                    int row,
                                    boolean hasFocus) {
    if (value instanceof DefaultMutableTreeNode) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
      Object userObject = node.getUserObject();
      if (userObject instanceof NodeDescriptor) {
        NodeDescriptor descriptor = (NodeDescriptor)userObject;
        Object element = descriptor.getElement();
        if (element instanceof FileElement) {
          FileElement fileElement = (FileElement)element;
          if (isExcluded(fileElement)) {
            if (expanded) {
              setIcon(IconLoader.getDisabledIcon(descriptor.getOpenIcon()));
            }
            else {
              setIcon(IconLoader.getDisabledIcon(descriptor.getClosedIcon()));
            }
            String text = tree.convertValueToText(value, selected, expanded, leaf, row, hasFocus);
            append(text, new SimpleTextAttributes(SimpleTextAttributes.STYLE_STRIKEOUT,
                                                  tree.getForeground()));
            return;
          }
        }
      }
    }


    super.customizeCellRenderer(tree, value, selected, expanded, leaf, row, hasFocus);
  }

  public AnAction createExcludeAction() {
    return new AnAction(CvsBundle.message("import.wizard.exclude.from.import.action.name"), null, PlatformIcons.DELETE_ICON) {
      public void update(AnActionEvent e) {
        VirtualFile[] selectedFiles = myFileSystemTree.getSelectedFiles();
        Presentation presentation = e.getPresentation();
        presentation.setEnabled(allFilesAreIncluded(selectedFiles));
      }

      public void actionPerformed(AnActionEvent e) {
        VirtualFile[] selectedFiles = myFileSystemTree.getSelectedFiles();
        for (VirtualFile selectedFile : selectedFiles) {
          exclude(selectedFile);
        }
        myWizard.updateStep();
        myFileSystemTree.getTree().repaint();
      }
    };
  }

  private boolean allFilesAreIncluded(VirtualFile[] selectedFiles) {
    if (selectedFiles == null || selectedFiles.length == 0) return false;
    for (VirtualFile selectedFile : selectedFiles) {
      if (isExcluded(selectedFile)) return false;
    }
    return true;
  }

  public AnAction createIncludeAction() {
    return new AnAction(CvsBundle.message("import.wizard.include.to.import.action.name"), null, PlatformIcons.ADD_ICON) {
      public void update(AnActionEvent e) {
        VirtualFile[] selectedFiles = myFileSystemTree.getSelectedFiles();
        Presentation presentation = e.getPresentation();
        presentation.setEnabled(allFilesAreInExcluded(selectedFiles));
      }

      public void actionPerformed(AnActionEvent e) {
        VirtualFile[] selectedFiles = myFileSystemTree.getSelectedFiles();
        for (VirtualFile selectedFile : selectedFiles) {
          include(selectedFile);
        }
        myWizard.updateStep();
        myFileSystemTree.getTree().repaint();
      }
    };
  }

  private void include(VirtualFile selectedFile) {
    myExcludedFiles.remove(selectedFile);

  }

  private void exclude(VirtualFile selectedFile) {
    myExcludedFiles.add(selectedFile);
  }

  private boolean allFilesAreInExcluded(VirtualFile[] selectedFiles) {
    if (selectedFiles == null || selectedFiles.length == 0) return false;
    for (VirtualFile selectedFile : selectedFiles) {
      if (!myExcludedFiles.contains(selectedFile)) return false;
    }
    return true;
  }

  private boolean isExcluded(FileElement fileElement) {
    VirtualFile file = fileElement.getFile();
    return file != null && isExcluded(file);
  }

  public boolean isExcluded(VirtualFile file) {
    for (final VirtualFile virtualFile : myExcludedFiles) {
      if (VfsUtil.isAncestor(virtualFile, file, false)) return true;
    }
    return false;
  }

  public IIgnoreFileFilter getIgnoreFileFilter() {
    final Collection<File> ignoredFiles = new HashSet<File>();
    for (final VirtualFile myExcludedFile : myExcludedFiles) {
      ignoredFiles.add(CvsVfsUtil.getFileFor(myExcludedFile));

    }
    return new IIgnoreFileFilter() {
      private final Map<File, IgnoredFilesInfo> myParentToIgnoresMap = new HashMap<File, IgnoredFilesInfo>();

      public boolean shouldBeIgnored(AbstractFileObject abstractFileObject, ICvsFileSystem cvsFileSystem) {
        File file = cvsFileSystem.getLocalFileSystem().getFile(abstractFileObject);
        if (file.isDirectory() && file.getName().equals(CvsUtil.CVS)) return true;

        if (FileTypeManager.getInstance().isFileIgnored(abstractFileObject.getName())) return true;
        if (myProject != null) {
          final VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(file);
          if (vFile != null && ProjectRootManager.getInstance(myProject).getFileIndex().isIgnored(vFile)) {
            return true;
          }
        }

        if (ignoredFiles.contains(file)) return true;
        File parentFile = file.getParentFile();
        if (parentFile == null) return false;
        if (!myParentToIgnoresMap.containsKey(parentFile)) {
          myParentToIgnoresMap.put(parentFile,
                                   IgnoredFilesInfoImpl.createForFile(
                                     new File(parentFile,
                                              CvsUtil.CVS_IGNORE_FILE)));
        }

        return myParentToIgnoresMap.get(parentFile).shouldBeIgnored(file.getName());
      }
    };
  }
}
