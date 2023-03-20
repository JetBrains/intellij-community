// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl.rules;

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.CompactGroupHelper;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageGroup;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.rules.SingleParentUsageGroupingRule;
import com.intellij.usages.rules.UsageGroupingRuleEx;
import com.intellij.usages.rules.UsageInFile;
import com.intellij.util.IconUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.List;


public class DirectoryGroupingRule extends SingleParentUsageGroupingRule implements DumbAware, UsageGroupingRuleEx {
  public static DirectoryGroupingRule getInstance(Project project) {
    return project.getService(DirectoryGroupingRule.class);
  }

  protected final Project myProject;
  private final boolean myFlattenDirs;
  /**
   * A flag specifying if the middle paths (that do not contain a usage) should be compacted
   */
  private final boolean compactMiddleDirectories;

  public DirectoryGroupingRule(@NotNull Project project) {
    this(project, true, false);
  }

  /**
   * @param compactMiddleDirectories if true then middle directories that do not contain any UsageNodes and only one GroupNode
   *                                 will be merged with the child directory in the usage tree
   */
  DirectoryGroupingRule(@NotNull Project project, boolean flattenDirs, boolean compactMiddleDirectories) {
    myProject = project;
    myFlattenDirs = flattenDirs;
    this.compactMiddleDirectories = compactMiddleDirectories;
  }

  @Nullable
  @Override
  protected UsageGroup getParentGroupFor(@NotNull Usage usage, UsageTarget @NotNull [] targets) {
    if (usage instanceof UsageInFile usageInFile) {
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
  public int getRank() {
    return UsageGroupingRulesDefaultRanks.DIRECTORY_STRUCTURE.getAbsoluteRank();
  }

  @Override
  public @NotNull String getGroupingActionId() {
    return "UsageGrouping.Directory";
  }

  private final class DirectoryGroup implements UsageGroup, DataProvider {
    private final VirtualFile myDir;
    private Icon myIcon;
    private final @NlsSafe String relativePathText;

    private DirectoryGroup(@NotNull VirtualFile dir) {
      myDir = dir;
      relativePathText = myDir.getPath();
      update();
    }

    @Override
    public void update() {
      if (isValid()) {
        myIcon = IconUtil.getIcon(myDir, 0, myProject);
      }
    }

    @Override
    public Icon getIcon() {
      return myIcon;
    }

    @Override
    @NotNull
    public String getPresentableGroupText() {

      if (compactMiddleDirectories) {
        List<String> parentPathList = CompactGroupHelper.pathToPathList(myDir.getPath());
        List<String> relativePathList = CompactGroupHelper.pathToPathList(relativePathText);
        String rel = relativePathText.startsWith("/") ? relativePathText.substring(1) : relativePathText;

        if (parentPathList.size() == relativePathList.size()) {
          VirtualFile baseDir = ProjectUtil.guessProjectDir(myProject);
          String relativePath = null;
          if (baseDir != null && baseDir.getParent() != null) {
            relativePath = VfsUtilCore.getRelativePath(myDir, baseDir.getParent(), File.separatorChar);
          }
          return relativePath == null ?
                 rel : relativePath.replace("\\", "/");
        }
        return rel;
      }
      else {
        if (myFlattenDirs || myDir.getParent() == null) {
          VirtualFile baseDir = ProjectUtil.guessProjectDir(myProject);
          String relativePath = baseDir == null ? null : VfsUtilCore.getRelativePath(myDir, baseDir, File.separatorChar);
          return relativePath == null ? myDir.getPresentableUrl() : relativePath;
        }
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
      return getPresentableGroupText().compareToIgnoreCase(usageGroup.getPresentableGroupText());
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof DirectoryGroup)) return false;
      return myDir.equals(((DirectoryGroup)o).myDir);
    }

    public int hashCode() {
      return myDir.hashCode();
    }

    @Nullable
    @Override
    public Object getData(@NotNull String dataId) {
      if (CommonDataKeys.VIRTUAL_FILE.is(dataId)) {
        return myDir;
      }
      if (PlatformCoreDataKeys.BGT_DATA_PROVIDER.is(dataId)) {
        return (DataProvider)slowId -> getSlowData(slowId);
      }
      return null;
    }

    private @Nullable Object getSlowData(@NotNull String dataId) {
      if (CommonDataKeys.PSI_ELEMENT.is(dataId)) {
        return getDirectory();
      }
      return null;
    }

    @Override
    public String toString() {
      return UsageViewBundle.message("directory.0", myDir.getName());
    }
  }
}
