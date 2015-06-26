/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.usages.impl.rules;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageGroup;
import com.intellij.usages.UsageView;
import com.intellij.usages.rules.PsiElementUsage;
import com.intellij.usages.rules.UsageGroupingRule;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author max
 */
public class UsageScopeGroupingRule implements UsageGroupingRule, DumbAware {
  @Override
  public UsageGroup groupUsage(@NotNull Usage usage) {
    if (!(usage instanceof PsiElementUsage)) {
      return null;
    }
    PsiElementUsage elementUsage = (PsiElementUsage)usage;

    PsiElement element = elementUsage.getElement();
    VirtualFile virtualFile = PsiUtilCore.getVirtualFile(element);

    if (virtualFile == null) {
      return null;
    }
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(element.getProject()).getFileIndex();
    boolean isInLib = fileIndex.isInLibraryClasses(virtualFile) || fileIndex.isInLibrarySource(virtualFile);
    if (isInLib) return LIBRARY;
    boolean isInTest = fileIndex.isInTestSourceContent(virtualFile);
    return isInTest ? TEST : PRODUCTION;
  }

  private static final UsageScopeGroup TEST = new UsageScopeGroup(0) {
    @Override
    public Icon getIcon(boolean isOpen) {
      return AllIcons.Modules.TestSourceFolder;
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
