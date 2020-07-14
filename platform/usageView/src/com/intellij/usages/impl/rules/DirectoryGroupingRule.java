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
import com.intellij.usages.*;
import com.intellij.usages.rules.SingleParentUsageGroupingRule;
import com.intellij.usages.rules.UsageGroupingRuleEx;
import com.intellij.usages.rules.UsageInFile;
import com.intellij.util.IconUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.*;

/**
 * @author yole
 */
public class DirectoryGroupingRule extends SingleParentUsageGroupingRule implements DumbAware, UsageGroupingRuleEx {
  public static DirectoryGroupingRule getInstance(Project project) {
    return ServiceManager.getService(project, DirectoryGroupingRule.class);
  }

  protected final Project myProject;
  private final boolean myFlattenDirs;
  /**
   * A flag specifying
   */
  private boolean compactMiddleDirectories;

  public DirectoryGroupingRule(Project project) {
    this(project, true, false);
  }

  /**
   * @param compactMiddleDirectories if true then middle directories that do not contain any UsageNodes and only one GroupNode
   *                                 will be merged with the child directory in the usage tree
   */
  DirectoryGroupingRule(Project project, boolean flattenDirs, boolean compactMiddleDirectories) {
    myProject = project;
    myFlattenDirs = flattenDirs;
    this.compactMiddleDirectories = compactMiddleDirectories;
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

  private final class DirectoryGroup implements UsageGroup, TypeSafeDataProvider, CompactGroup {
    private final VirtualFile myDir;
    private Icon myIcon;
    private volatile String relativePathText;

    private DirectoryGroup(@NotNull VirtualFile dir) {
      myDir = dir;
      relativePathText = myDir.getPath();
      update();
    }

    private DirectoryGroup(@NotNull VirtualFile dir, @NotNull String relativePathText) {
      myDir = dir;
      this.relativePathText = relativePathText;
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

      if (compactMiddleDirectories) {
        List<String> parentPathList = CompactGroupHelper.pathToPathList(myDir.getPath());
        List<String> relativePathList = CompactGroupHelper.pathToPathList(relativePathText);
        if (parentPathList.size() == relativePathList.size()) {
          VirtualFile baseDir = ProjectUtil.guessProjectDir(myProject);
          String relativePath = null;
          if (baseDir != null && baseDir.getParent() != null) {
            relativePath = VfsUtilCore.getRelativePath(myDir, baseDir.getParent(), File.separatorChar)
              .replace("\\", "/");
          }
          return relativePath == null ?
                 relativePathText.startsWith("/") ? relativePathText.substring(1) : relativePathText : relativePath;
        }
        return relativePathText.startsWith("/") ? relativePathText.substring(1) : relativePathText;
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

    @Override
    public boolean hasCommonParent(@NotNull CompactGroup group) {
      if (group instanceof DirectoryGroup) {
        return !CompactGroupHelper.findLongestCommonParent(this.relativePathText, ((DirectoryGroup)group).relativePathText).isEmpty();
      }
      return false;
    }

    @Override
    public boolean isParentOf(@NotNull CompactGroup group) {
      if (group instanceof DirectoryGroup) {
        return (((DirectoryGroup)group).myDir.getPath().startsWith(this.myDir.getPath()));
      }
      return false;
    }

    @Override
    public CompactGroup merge(@NotNull CompactGroup group) {
      if (this.isParentOf(group)) {
        return new DirectoryGroup(((DirectoryGroup)group).myDir, ((DirectoryGroup)group).relativePathText);
      }
      return this;
    }

    @NotNull
    @Override
    public List<CompactGroup> split(@NotNull CompactGroup group, boolean doNothingIfSubGroup) {
      List<String> paths;
      if (group instanceof DirectoryGroup) {
        if (this.isParentOf(group)) {
          if (doNothingIfSubGroup) {
            return new ArrayList<>();
          }
        }
        VirtualFile myDir = this.myDir;
        paths = CompactGroupHelper.findLongestCommonParent(this.relativePathText, ((DirectoryGroup)group).relativePathText);

        if (!paths.isEmpty()) {
          VirtualFile parent = myDir;
          List<String> parentPath = CompactGroupHelper.pathToPathList(parent.getPath());
          List<String> newCommonPath = CompactGroupHelper.pathToPathList(paths.get(0));
          Collections.reverse(parentPath);
          Collections.reverse(newCommonPath);

          while (parent.getParent() != null && !CompactGroupHelper.listStartsWith(parentPath, newCommonPath)) {
            parent = parent.getParent();
            parentPath = CompactGroupHelper.pathToPathList(parent.getPath());
            newCommonPath = CompactGroupHelper.pathToPathList(paths.get(0));
            Collections.reverse(parentPath);
            Collections.reverse(newCommonPath);
          }

          ArrayList<CompactGroup> newGroups = new ArrayList<>();
          newGroups.add(new DirectoryGroup(parent, paths.get(0)));
          if (paths.size() == 2) {
            if (this.isParentOf(group)) {
              newGroups.add(new DirectoryGroup(((DirectoryGroup)group).myDir, paths.get(1)));
            }
            else {
              newGroups.add(new DirectoryGroup(myDir, paths.get(1)));
            }
          }
          else if (paths.size() == 3) {
            newGroups.add(new DirectoryGroup(myDir, paths.get(1)));
            newGroups.add(new DirectoryGroup(((DirectoryGroup)group).myDir, paths.get(2)));
          }
          return newGroups;
        }
      }

      return new ArrayList<>();
    }
  }
}
