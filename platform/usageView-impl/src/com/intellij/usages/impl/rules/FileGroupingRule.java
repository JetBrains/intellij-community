// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.impl.rules;

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.UiDataProvider;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.usages.NamedPresentably;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageGroup;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.rules.SingleParentUsageGroupingRule;
import com.intellij.usages.rules.UsageGroupingRuleEx;
import com.intellij.usages.rules.UsageInFile;
import com.intellij.util.IconUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class FileGroupingRule extends SingleParentUsageGroupingRule implements DumbAware, UsageGroupingRuleEx {
  private final Project myProject;

  public FileGroupingRule(Project project) {
    myProject = project;
  }

  @Override
  public @Nullable UsageGroup getParentGroupFor(@NotNull Usage usage, UsageTarget @NotNull [] targets) {
    VirtualFile virtualFile;
    if (usage instanceof UsageInFile && (virtualFile = ((UsageInFile)usage).getFile()) != null) {
      return new FileUsageGroup(myProject, virtualFile);
    }
    return null;
  }

  @Override
  public int getRank() {
    return UsageGroupingRulesDefaultRanks.FILE_STRUCTURE.getAbsoluteRank();
  }

  @Override
  public @Nullable String getGroupingActionId() {
    return "UsageGrouping.FileStructure";
  }

  @Override
  public boolean isGroupingActionInverted() {
    return true;
  }

  public static class FileUsageGroup implements UsageGroup, UiDataProvider, NamedPresentably {
    private final Project myProject;
    private final VirtualFile myFile;
    private @NlsSafe String myPresentableName;
    private Icon myIcon;

    public FileUsageGroup(@NotNull Project project, @NotNull VirtualFile file) {
      myProject = project;
      myFile = file instanceof VirtualFileWindow ? ((VirtualFileWindow)file).getDelegate() : file;
      myPresentableName = myFile.getName();
      update();
    }

    private Icon getIconImpl() {
      return IconUtil.getIcon(myFile, Iconable.ICON_FLAG_READ_STATUS, myProject);
    }

    @Override
    public void update() {
      if (isValid()) {
        myIcon = getIconImpl();
        myPresentableName = myFile.getName();
      }
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof FileUsageGroup fileUsageGroup)) return false;

      return myFile.equals(fileUsageGroup.myFile);
    }

    @Override
    public int hashCode() {
      return myFile.hashCode();
    }

    @Override
    public Icon getIcon() {
      return myIcon;
    }

    @Override
    public @NotNull String getPresentableGroupText() {
      return myPresentableName;
    }

    @Override
    public FileStatus getFileStatus() {
      return !myProject.isDisposed() && isValid() ? FileStatusManager.getInstance(myProject).getStatus(myFile) : null;
    }

    @Override
    public boolean isValid() {
      return myFile.isValid();
    }

    @Override
    public void navigate(boolean focus) throws UnsupportedOperationException {
      if (!myProject.isDisposed()) FileEditorManager.getInstance(myProject).openFile(myFile, focus);
    }

    @Override
    public boolean canNavigate() {
      return myFile.isValid();
    }

    @Override
    public boolean canNavigateToSource() {
      return canNavigate();
    }

    @Override
    public int compareTo(@NotNull UsageGroup otherGroup) {
      int compareTexts = getPresentableGroupText().compareToIgnoreCase(otherGroup.getPresentableGroupText());
      if (compareTexts != 0) return compareTexts;
      if (otherGroup instanceof FileUsageGroup) {
        return myFile.getPath().compareTo(((FileUsageGroup)otherGroup).myFile.getPath());
      }
      return 0;
    }

    @Override
    public void uiDataSnapshot(@NotNull DataSink sink) {
      sink.set(CommonDataKeys.VIRTUAL_FILE, myFile);
      sink.lazy(CommonDataKeys.PSI_ELEMENT, () -> {
        return getPsiFile();
      });
    }

    @ApiStatus.Internal
    public @Nullable PsiFile getPsiFile() {
      return myFile.isValid() ? PsiManager.getInstance(myProject).findFile(myFile) : null;
    }

    @Override
    public @NotNull String getPresentableName() {
      return myPresentableName;
    }
  }
}
