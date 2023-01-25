// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;

public class VirtualFileListCellRenderer extends ColoredListCellRenderer {
  protected final Project myProject;
  private final FileStatusManager myFileStatusManager;
  private final boolean myIgnoreFileStatus;

  public VirtualFileListCellRenderer(final Project project) {
    this(project, false);
  }

  public VirtualFileListCellRenderer(final Project project, final boolean ignoreFileStatus) {
    myProject = project;
    myIgnoreFileStatus = ignoreFileStatus;
    myFileStatusManager = FileStatusManager.getInstance(project);
  }

  @Override
  protected void customizeCellRenderer(@NotNull JList list, Object value, int index, boolean selected, boolean hasFocus) {
    final FilePath path = TreeModelBuilder.getPathForObject(value);
    renderIcon(path);
    final FileStatus fileStatus = myIgnoreFileStatus ? FileStatus.NOT_CHANGED : getStatus(value, path);
    append(getName(path), new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, fileStatus.getColor(), null));
    putParentPath(value, path, path);
    setBackground(selected
                  ? (hasFocus ? UIUtil.getListSelectionBackground(true) : UIUtil.getListUnfocusedSelectionBackground())
                  : UIUtil.getListBackground());
  }

  @Nls
  protected String getName(FilePath path) {
    return path.getName();
  }

  protected FileStatus getStatus(Object value, FilePath path) {
    final FileStatus fileStatus;
    if (value instanceof Change) {
      fileStatus = ((Change) value).getFileStatus();
    }
    else {
      final VirtualFile virtualFile = path.getVirtualFile();
      if (virtualFile != null) {
        fileStatus = myFileStatusManager.getStatus(virtualFile);
      }
      else {
        fileStatus = FileStatus.NOT_CHANGED;
      }
    }
    return fileStatus;
  }

  protected void renderIcon(FilePath path) {
    if (path.isDirectory()) {
      setIcon(PlatformIcons.FOLDER_ICON);
    } else {
      setIcon(VcsUtil.getIcon(myProject, path));
    }
  }

  protected void putParentPath(Object value, FilePath path, FilePath self) {
    final File parentFile = path.getIOFile().getParentFile();
    if (parentFile != null) {
      final String parentPath = parentFile.getPath();
      append(" (", SimpleTextAttributes.GRAYED_ATTRIBUTES);
      putParentPathImpl(value, parentPath, self);
      append(")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }
  }

  protected void putParentPathImpl(Object value, @NlsSafe String parentPath, FilePath self) {
    append(parentPath, SimpleTextAttributes.GRAYED_ATTRIBUTES);
  }
}
