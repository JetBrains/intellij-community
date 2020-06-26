// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl.rules;

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.TypeSafeDataProvider;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageGroup;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageView;
import com.intellij.usages.rules.SingleParentUsageGroupingRule;
import com.intellij.usages.rules.UsageGroupingRuleEx;
import com.intellij.usages.rules.UsageInFile;
import com.intellij.util.IconUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;

/**
 * @author yole
 */
public class DirectoryGroupingRule extends SingleParentUsageGroupingRule implements DumbAware, UsageGroupingRuleEx {
  public static DirectoryGroupingRule getInstance(Project project) {
    return ServiceManager.getService(project, DirectoryGroupingRule.class);
  }

  protected final Project myProject;
  private final boolean myFlattenDirs;

  public DirectoryGroupingRule(Project project) {
    this(project, true);
  }
  DirectoryGroupingRule(Project project, boolean flattenDirs) {
    myProject = project;
    myFlattenDirs = flattenDirs;
  }

  @Nullable
  @Override
  protected UsageGroup getParentGroupFor(@NotNull Usage usage, UsageTarget @NotNull [] targets) {
    if (usage instanceof UsageInFile) {
      UsageInFile usageInFile = (UsageInFile)usage;
      VirtualFile file = usageInFile.getFile();
      if (file != null) {
        if (file instanceof VirtualFileWindow) {
          file = ((VirtualFileWindow)file).getDelegate();
        }
        VirtualFile dir = file.getParent();
        if (dir == null) return null;
        return getGroupForFile(dir);
      }
    }
    return null;
  }

  protected UsageGroup getGroupForFile(@NotNull VirtualFile dir) {
    return new DirectoryGroup(dir);
  }

  @Override
  public @NotNull String getGroupingActionId() {
    return "UsageGrouping.Directory";
  }

  private final class DirectoryGroup implements UsageGroup, TypeSafeDataProvider {
    private final VirtualFile myDir;
    private Icon myIcon;

    private DirectoryGroup(@NotNull VirtualFile dir) {
      myDir = dir;
      update();
    }

    @Override
    public void update() {
      if (isValid()) {
       myIcon = IconUtil.getIcon(myDir, 0, myProject);
      }
    }

    @Override
    public Icon getIcon(boolean isOpen) {
      return myIcon;
    }

    @Override
    @NotNull
    public String getText(UsageView view) {
      if (myFlattenDirs || myDir.getParent() == null) {
        VirtualFile baseDir = ProjectUtil.guessProjectDir(myProject);
        String relativePath = baseDir == null ? null : VfsUtilCore.getRelativePath(myDir, baseDir, File.separatorChar);
        return relativePath == null ? myDir.getPresentableUrl() : relativePath;
      }
      return myDir.getName();
    }

    @Override
    public FileStatus getFileStatus() {
      return isValid() ? FileStatusManager.getInstance(myProject).getStatus(myDir) : null;
    }

    @Override
    public boolean isValid() {
      return myDir.isValid();
    }

    @Override
    public void navigate(boolean focus) throws UnsupportedOperationException {
      final PsiDirectory directory = getDirectory();
      if (directory != null && directory.canNavigate()) {
        directory.navigate(focus);
      }
    }

    private PsiDirectory getDirectory() {
      return myDir.isValid() ? PsiManager.getInstance(myProject).findDirectory(myDir) : null;
    }
    @Override
    public boolean canNavigate() {
      final PsiDirectory directory = getDirectory();
      return directory != null && directory.canNavigate();
    }

    @Override
    public boolean canNavigateToSource() {
      return false;
    }

    @Override
    public int compareTo(@NotNull UsageGroup usageGroup) {
      return getText(null).compareToIgnoreCase(usageGroup.getText(null));
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof DirectoryGroup)) return false;
      return myDir.equals(((DirectoryGroup)o).myDir);
    }

    public int hashCode() {
      return myDir.hashCode();
    }

    @Override
    public void calcData(@NotNull final DataKey key, @NotNull final DataSink sink) {
      if (!isValid()) return;
      if (CommonDataKeys.VIRTUAL_FILE == key) {
        sink.put(CommonDataKeys.VIRTUAL_FILE, myDir);
      }
      if (CommonDataKeys.PSI_ELEMENT == key) {
        sink.put(CommonDataKeys.PSI_ELEMENT, getDirectory());
      }
    }

    @Override
    public String toString() {
      return "Directory:" + myDir.getName();
    }
  }
}
