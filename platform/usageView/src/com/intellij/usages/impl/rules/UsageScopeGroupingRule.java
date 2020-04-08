// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl.rules;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.TestSourcesFilter;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageGroup;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageView;
import com.intellij.usages.rules.PsiElementUsage;
import com.intellij.usages.rules.SingleParentUsageGroupingRule;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

class UsageScopeGroupingRule extends SingleParentUsageGroupingRule implements DumbAware {
  @Nullable
  @Override
  protected UsageGroup getParentGroupFor(@NotNull Usage usage, UsageTarget @NotNull [] targets) {
    if (!(usage instanceof PsiElementUsage)) {
      return null;
    }
    PsiElementUsage elementUsage = (PsiElementUsage)usage;

    PsiElement element = elementUsage.getElement();
    VirtualFile virtualFile = PsiUtilCore.getVirtualFile(element);

    if (virtualFile == null) {
      return null;
    }
    Project project = element.getProject();
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    boolean isInLib = fileIndex.isInLibrary(virtualFile);
    if (isInLib) return LIBRARY;
    return TestSourcesFilter.isTestSources(virtualFile, project) ? TEST : PRODUCTION;
  }

  private static final UsageScopeGroup TEST = new UsageScopeGroup(0) {
    @Override
    public Icon getIcon(boolean isOpen) {
      return AllIcons.Nodes.TestSourceFolder;
    }

    @Override
    @NotNull
    public String getText(UsageView view) {
      return "Test";
    }
  };
  private static final UsageScopeGroup PRODUCTION = new UsageScopeGroup(1) {
    @Override
    public Icon getIcon(boolean isOpen) {
      return PlatformIcons.SOURCE_FOLDERS_ICON;
    }

    @Override
    @NotNull
    public String getText(UsageView view) {
      return "Production";
    }
  };
  private static final UsageScopeGroup LIBRARY = new UsageScopeGroup(2) {
    @Override
    public Icon getIcon(boolean isOpen) {
      return PlatformIcons.LIBRARY_ICON;
    }

    @Override
    @NotNull
    public String getText(UsageView view) {
      return "Library";
    }
  };
  private abstract static class UsageScopeGroup implements UsageGroup {
    private final int myCode;

    private UsageScopeGroup(int code) {
      myCode = code;
    }

    @Override
    public void update() {
    }

    @Override
    public FileStatus getFileStatus() {
      return null;
    }

    @Override
    public boolean isValid() { return true; }
    @Override
    public void navigate(boolean focus) { }
    @Override
    public boolean canNavigate() { return false; }

    @Override
    public boolean canNavigateToSource() {
      return false;
    }

    @Override
    public int compareTo(@NotNull UsageGroup usageGroup) {
      return getText(null).compareTo(usageGroup.getText(null));
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof UsageScopeGroup)) return false;
      final UsageScopeGroup usageTypeGroup = (UsageScopeGroup)o;
      return myCode == usageTypeGroup.myCode;
    }

    public int hashCode() {
      return myCode;
    }
  }
}
