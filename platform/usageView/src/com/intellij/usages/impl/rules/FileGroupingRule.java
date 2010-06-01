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
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.usages.NamedPresentably;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageGroup;
import com.intellij.usages.UsageView;
import com.intellij.usages.rules.UsageGroupingRule;
import com.intellij.usages.rules.UsageInFile;
import com.intellij.util.IconUtil;
import com.intellij.injected.editor.VirtualFileWindow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author max
 */
public class FileGroupingRule implements UsageGroupingRule {
  private final Project myProject;

  public FileGroupingRule(Project project) {
    myProject = project;
  }

  public UsageGroup groupUsage(Usage usage) {
    if (usage instanceof UsageInFile) {
      return new FileUsageGroup(myProject, ((UsageInFile)usage).getFile());
    }
    return null;
  }

  protected static class FileUsageGroup implements UsageGroup, TypeSafeDataProvider, NamedPresentably {
    private final Project myProject;
    private final VirtualFile myFile;
    private String myPresentableName;
    private Icon myIcon;

    public FileUsageGroup(Project project, VirtualFile file) {
      myProject = project;
      myFile = file instanceof VirtualFileWindow ? ((VirtualFileWindow)file).getDelegate() : file;
      myPresentableName = myFile.getName();
      update();
    }

    private Icon getIconImpl() {
      return IconUtil.getIcon(myFile, Iconable.ICON_FLAG_READ_STATUS, myProject);
    }

    public void update() {
      if (isValid()) {
        myIcon = getIconImpl();
        myPresentableName = myFile.getName();
      }
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof FileUsageGroup)) return false;

      final FileUsageGroup fileUsageGroup = (FileUsageGroup)o;

      if (myFile != null ? !myFile.equals(fileUsageGroup.myFile) : fileUsageGroup.myFile != null) return false;

      return true;
    }

    public int hashCode() {
      return myFile != null ? myFile.hashCode() : 0;
    }

    public Icon getIcon(boolean isOpen) {
      return myIcon;
    }

    @NotNull
    public String getText(UsageView view) {
      return myPresentableName;
    }

    public FileStatus getFileStatus() {
      return isValid() ? FileStatusManager.getInstance(myProject).getStatus(myFile) : null;
    }

    public boolean isValid() {
      return myFile.isValid();
    }

    public void navigate(boolean focus) throws UnsupportedOperationException {
      FileEditorManager.getInstance(myProject).openFile(myFile, focus);
    }

    public boolean canNavigate() {
      return true;
    }

    public boolean canNavigateToSource() {
      return canNavigate();
    }

    public int compareTo(UsageGroup usageGroup) {
      return getText(null).compareTo(usageGroup.getText(null));
    }

    public void calcData(final DataKey key, final DataSink sink) {
      if (!isValid()) return;
      if (key == PlatformDataKeys.VIRTUAL_FILE) {
        sink.put(PlatformDataKeys.VIRTUAL_FILE, myFile);
      }
      if (key == LangDataKeys.PSI_ELEMENT) {
        sink.put(LangDataKeys.PSI_ELEMENT, getPsiFile());
      }
    }

    @Nullable
    public PsiFile getPsiFile() {
      return myFile != null && myFile.isValid() ? PsiManager.getInstance(myProject).findFile(myFile) : null;
    }

    @NotNull
    public String getPresentableName() {
      return myPresentableName;
    }
  }
}
