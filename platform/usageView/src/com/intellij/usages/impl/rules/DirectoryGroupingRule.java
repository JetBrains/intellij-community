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
package com.intellij.usages.impl.rules;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageGroup;
import com.intellij.usages.UsageView;
import com.intellij.usages.rules.UsageGroupingRule;
import com.intellij.usages.rules.UsageInFile;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author yole
 */
public class DirectoryGroupingRule implements UsageGroupingRule {
  public static DirectoryGroupingRule getInstance(Project project) {
    return ServiceManager.getService(project, DirectoryGroupingRule.class);
  }

  protected final Project myProject;

  public DirectoryGroupingRule(Project project) {
    myProject = project;
  }

  @Nullable
  public UsageGroup groupUsage(Usage usage) {
    if (usage instanceof UsageInFile) {
      UsageInFile usageInFile = (UsageInFile)usage;
      VirtualFile file = usageInFile.getFile();
      if (file != null) {
        VirtualFile dir = file.getParent();
        if (dir == null) return null;
        return getGroupForFile(dir);
      }
    }
    return null;
  }

  protected UsageGroup getGroupForFile(VirtualFile dir) {
    return new DirectoryGroup(dir);
  }

  private class DirectoryGroup implements UsageGroup, TypeSafeDataProvider {
    private final VirtualFile myDir;

    public void update() {
    }

    private DirectoryGroup(VirtualFile dir) {
      myDir = dir;
    }

    public Icon getIcon(boolean isOpen) {
      return isOpen ? PlatformIcons.DIRECTORY_OPEN_ICON : PlatformIcons.DIRECTORY_CLOSED_ICON;
    }

    @NotNull
    public String getText(UsageView view) {
      return myDir.getPresentableUrl();
    }

    public FileStatus getFileStatus() {
      return isValid() ? FileStatusManager.getInstance(myProject).getStatus(myDir) : null;
    }

    public boolean isValid() {
      return myDir.isValid();
    }

    public void navigate(boolean focus) throws UnsupportedOperationException {
      final PsiDirectory directory = getDirectory();
      if (directory != null && directory.canNavigate()) {
        directory.navigate(focus);
      }
    }

    private PsiDirectory getDirectory() {
      return PsiManager.getInstance(myProject).findDirectory(myDir);
    }
    public boolean canNavigate() {
      final PsiDirectory directory = getDirectory();
      return directory != null && directory.canNavigate();
    }

    public boolean canNavigateToSource() {
      return false;
    }

    public int compareTo(UsageGroup usageGroup) {
      return getText(null).compareTo(usageGroup.getText(null));
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof DirectoryGroup)) return false;
      return myDir.equals(((DirectoryGroup)o).myDir);
    }

    public int hashCode() {
      return myDir.hashCode();
    }

    public void calcData(final DataKey key, final DataSink sink) {
      if (!isValid()) return;
      if (PlatformDataKeys.VIRTUAL_FILE == key) {
        sink.put(PlatformDataKeys.VIRTUAL_FILE, myDir);
      }
      if (LangDataKeys.PSI_ELEMENT == key) {
        sink.put(LangDataKeys.PSI_ELEMENT, getDirectory());
      }
    }
  }
}
