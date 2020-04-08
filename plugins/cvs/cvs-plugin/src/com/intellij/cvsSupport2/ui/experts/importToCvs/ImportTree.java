// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.render.RenderingUtil;
import com.intellij.util.IconUtil;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.netbeans.lib.cvsclient.file.AbstractFileObject;
import org.netbeans.lib.cvsclient.file.ICvsFileSystem;
import org.netbeans.lib.cvsclient.util.IIgnoreFileFilter;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * @author lesya
 */
public class ImportTree extends NodeRenderer {
  private final Collection<VirtualFile> myExcludedFiles = new HashSet<>();
  private final Collection<VirtualFile> myIncludedFiles = new HashSet<>();
  private final Project myProject;
  private final FileSystemTree myFileSystemTree;
  private final CvsWizard myWizard;

  public ImportTree(@Nullable Project project, FileSystemTree fileSystemTree, CvsWizard wizard) {
    myProject = project;
    myFileSystemTree = fileSystemTree;
    myWizard = wizard;
  }

  @Override
  public void customizeCellRenderer(@NotNull JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    if (customize(tree, value, selected, expanded, leaf, row, hasFocus)) return;
    super.customizeCellRenderer(tree, value, selected, expanded, leaf, row, hasFocus);
  }

  private boolean customize(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    if (!(value instanceof DefaultMutableTreeNode)) {
      return false;
    }
    final DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
    final Object userObject = node.getUserObject();
    if (!(userObject instanceof NodeDescriptor)) {
      return false;
    }
    final NodeDescriptor descriptor = (NodeDescriptor)userObject;
    final Object element = descriptor.getElement();
    if (!(element instanceof FileElement)) {
      return false;
    }
    final FileElement fileElement = (FileElement)element;
    if (!isExcluded(fileElement)) {
      return false;
    }
    setIcon(descriptor.getIcon() == null ? null : IconLoader.getDisabledIcon(descriptor.getIcon()));
    final String text = tree.convertValueToText(value, selected, expanded, leaf, row, hasFocus);
    append(text, new SimpleTextAttributes(SimpleTextAttributes.STYLE_STRIKEOUT, RenderingUtil.getForeground(tree)));
    return true;
  }

  public AnAction createExcludeAction() {
    return new AnAction(CvsBundle.messagePointer("import.wizard.exclude.from.import.action.name"), PlatformIcons.DELETE_ICON) {
      @Override
      public void update(@NotNull AnActionEvent e) {
        final VirtualFile[] selectedFiles = myFileSystemTree.getSelectedFiles();
        final Presentation presentation = e.getPresentation();
        presentation.setEnabled(isAtLeastOneFileIncluded(selectedFiles));
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        final VirtualFile[] selectedFiles = myFileSystemTree.getSelectedFiles();
        for (VirtualFile selectedFile : selectedFiles) {
          exclude(selectedFile);
        }
        myWizard.updateStep();
        myFileSystemTree.getTree().repaint();
      }
    };
  }

  private boolean isAtLeastOneFileIncluded(VirtualFile[] selectedFiles) {
    if (selectedFiles == null || selectedFiles.length == 0) return false;
    for (VirtualFile selectedFile : selectedFiles) {
      if (!isExcluded(selectedFile)) {
        return true;
      }
    }
    return false;
  }

  public AnAction createIncludeAction() {
    return new AnAction(CvsBundle.messagePointer("import.wizard.include.to.import.action.name"), IconUtil.getAddIcon()) {
      @Override
      public void update(@NotNull AnActionEvent e) {
        final VirtualFile[] selectedFiles = myFileSystemTree.getSelectedFiles();
        final Presentation presentation = e.getPresentation();
        presentation.setEnabled(isAtLeastOneFileExcluded(selectedFiles));
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        final VirtualFile[] selectedFiles = myFileSystemTree.getSelectedFiles();
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
    if (myProject == null) {
      return;
    }
    if (!isIgnoredByVcs(selectedFile)) {
      return;
    }
    final VirtualFile parent = selectedFile.getParent();
    if (parent != null && isIgnoredByVcs(parent)) {
      return;
    }
    for (final VirtualFile excludedFile : myExcludedFiles) {
      if (VfsUtil.isAncestor(excludedFile, selectedFile, true)) {
        return;
      }
    }
    myIncludedFiles.add(selectedFile);
  }

  private void exclude(VirtualFile selectedFile) {
    myExcludedFiles.add(selectedFile);
    myIncludedFiles.remove(selectedFile);
  }

  private boolean isAtLeastOneFileExcluded(VirtualFile[] selectedFiles) {
    if (selectedFiles == null || selectedFiles.length == 0) {
      return false;
    }
    for (VirtualFile selectedFile : selectedFiles) {
      if (myExcludedFiles.contains(selectedFile)) {
        return true;
      }
      if (myProject == null) {
        continue;
      }
      if (!isIgnoredByVcs(selectedFile)) {
        continue;
      }
      final VirtualFile parent = selectedFile.getParent();
      if (parent == null || isIgnoredByVcs(parent) || myExcludedFiles.contains(parent)) {
        continue;
      }
      if (!myIncludedFiles.contains(selectedFile)) {
        return true;
      }
    }
    return false;
  }

  private boolean isExcluded(FileElement fileElement) {
    final VirtualFile file = fileElement.getFile();
    if (file == null) {
      return false;
    }
    return isExcluded(file);
  }

  public boolean isExcluded(VirtualFile file) {
    for (final VirtualFile excludedFile : myExcludedFiles) {
      if (VfsUtil.isAncestor(excludedFile, file, false)) {
        return true;
      }
    }
    if (myProject == null || !isIgnoredByVcs(file)) {
      return false;
    }
    for (VirtualFile includedFile : myIncludedFiles) {
      if (VfsUtil.isAncestor(includedFile, file, false)) {
        return false;
      }
    }
    return true;
  }

  public IIgnoreFileFilter getIgnoreFileFilter() {
    final Collection<File> excludedFiles = new HashSet<>();
    for (final VirtualFile excludedFile : myExcludedFiles) {
      excludedFiles.add(CvsVfsUtil.getFileFor(excludedFile));
    }
    final Collection<File> includedFiles = new HashSet<>();
    for (VirtualFile includedFile : myIncludedFiles) {
      includedFiles.add(CvsVfsUtil.getFileFor(includedFile));
    }

    return new IIgnoreFileFilter() {
      private final Map<File, IgnoredFilesInfo> myParentToIgnoresMap = new HashMap<>();

      @Override
      public boolean shouldBeIgnored(AbstractFileObject abstractFileObject, ICvsFileSystem cvsFileSystem) {
        final File file = cvsFileSystem.getLocalFileSystem().getFile(abstractFileObject);
        if (file.isDirectory() && file.getName().equals(CvsUtil.CVS)) return true;

        if (FileTypeManager.getInstance().isFileIgnored(abstractFileObject.getName())) return true;
        final VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(file);
        if (myProject != null && !includedFiles.contains(file)) {
          if (vFile != null && isIgnoredByVcs(vFile)) {
            return true;
          }
        }

        if (excludedFiles.contains(file)) return true;
        final File parentFile = file.getParentFile();
        if (parentFile == null) return false;
        if (!myParentToIgnoresMap.containsKey(parentFile)) {
          myParentToIgnoresMap.put(parentFile, IgnoredFilesInfoImpl.createForFile(new File(parentFile, CvsUtil.CVS_IGNORE_FILE)));
        }
        return myParentToIgnoresMap.get(parentFile).shouldBeIgnored(vFile);
      }
    };
  }

  private boolean isIgnoredByVcs(VirtualFile vFile) {
    return myProject != null && ProjectLevelVcsManager.getInstance(myProject).isIgnored(vFile);
  }
}
