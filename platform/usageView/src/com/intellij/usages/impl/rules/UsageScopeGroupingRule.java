/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageGroup;
import com.intellij.usages.UsageView;
import com.intellij.usages.rules.PsiElementUsage;
import com.intellij.usages.rules.UsageGroupingRule;
import com.intellij.util.Icons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author max
 */
public class UsageScopeGroupingRule implements UsageGroupingRule {
  public UsageGroup groupUsage(Usage usage) {
    if (!(usage instanceof PsiElementUsage)) {
      return null;
    }
    PsiElementUsage elementUsage = (PsiElementUsage)usage;

    PsiElement element = elementUsage.getElement();
    VirtualFile virtualFile = PsiUtilBase.getVirtualFile(element);

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
    public Icon getIcon(boolean isOpen) {
      return Icons.TEST_SOURCE_FOLDER;
    }

    @NotNull
    public String getText(UsageView view) {
      return "Test";
    }
  };
  private static final UsageScopeGroup PRODUCTION = new UsageScopeGroup(1) {
    public Icon getIcon(boolean isOpen) {
      return Icons.SOURCE_FOLDERS_ICON ;
    }

    @NotNull
    public String getText(UsageView view) {
      return "Production";
    }
  };
  private static final UsageScopeGroup LIBRARY = new UsageScopeGroup(2) {
    public Icon getIcon(boolean isOpen) {
      return Icons.LIBRARY_ICON ;
    }

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

    public void update() {
    }

    public FileStatus getFileStatus() {
      return null;
    }

    public boolean isValid() { return true; }
    public void navigate(boolean focus) { }
    public boolean canNavigate() { return false; }

    public boolean canNavigateToSource() {
      return false;
    }

    public int compareTo(UsageGroup usageGroup) {
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
