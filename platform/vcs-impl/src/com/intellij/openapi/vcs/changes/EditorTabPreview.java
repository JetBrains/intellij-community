package com.intellij.openapi.vcs.changes;

import com.intellij.diff.impl.DiffRequestProcessor;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.actions.diff.lst.LocalChangeListDiffTool;
import com.intellij.openapi.vcs.changes.ui.ChangesTree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Supplier;

public abstract class EditorTabPreview implements ChangesViewPreview {
  private final Project myProject;
  @NotNull private final PreviewDiffVirtualFile myPreviewDiffVirtualFile;
  private final VcsConfiguration myVcsConfiguration;
  private final DiffRequestProcessor myChangeProcessor;

  public EditorTabPreview(@NotNull DiffRequestProcessor changeProcessor, @NotNull Project project,
                   @NotNull JComponent contentPanel, @NotNull ChangesTree changesTree) {
    myProject = project;

    myChangeProcessor = changeProcessor;
    MyDiffPreviewProvider previewProvider = new MyDiffPreviewProvider(changeProcessor, this::getCurrentName);
    myPreviewDiffVirtualFile = new PreviewDiffVirtualFile(previewProvider);
    myVcsConfiguration = VcsConfiguration.getInstance(project);

    //do not open file aggressively on start up, do it later
    DumbService.getInstance(project).smartInvokeLater(() -> {
      if (project.isDisposed()) return;

      changesTree.addSelectionListener(() -> {
        if (!myVcsConfiguration.LOCAL_CHANGES_DETAILS_PREVIEW_SHOWN) return;

        if (shouldSkip()) return;

        setDiffPreviewVisible(true);
      });
    });

    new DumbAwareAction() {
      {
        copyShortcutFrom(ActionManager.getInstance().getAction("NextDiff"));
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        FileEditorManager.getInstance(project).openFile(myPreviewDiffVirtualFile, true, true);
      }
    }.registerCustomShortcutSet(contentPanel, null);
  }

  @Nullable
  protected abstract String getCurrentName();

  protected abstract void doRefresh();

  protected boolean shouldSkip() {
    return false;
  }

  @Override
  public void updatePreview(boolean fromModelRefresh) {
    if (VcsConfiguration.getInstance(myProject).LOCAL_CHANGES_DETAILS_PREVIEW_SHOWN) {
      doRefresh();
    }
  }

  @Override
  public void setDiffPreviewVisible(boolean isVisible) {
    updatePreview(false);

    if (!isVisible) {
      FileEditorManager.getInstance(myProject).closeFile(myPreviewDiffVirtualFile);
    }
    else {
      FileEditorManager.getInstance(myProject).openFile(myPreviewDiffVirtualFile, false, true);
    }
  }

  @Override
  public void setAllowExcludeFromCommit(boolean value) {
    myChangeProcessor.putContextUserData(LocalChangeListDiffTool.ALLOW_EXCLUDE_FROM_COMMIT, true);
    myChangeProcessor.updateRequest(true);
  }

  private static class MyDiffPreviewProvider implements DiffPreviewProvider {
    private final DiffRequestProcessor myChangeProcessor;
    private final Supplier<String> myGetName;

    private MyDiffPreviewProvider(DiffRequestProcessor changeProcessor, Supplier<String> getName) {
      myChangeProcessor = changeProcessor;
      myGetName = getName;
    }

    @NotNull
    @Override
    public DiffRequestProcessor createDiffRequestProcessor() {
      return myChangeProcessor;
    }

    @NotNull
    @Override
    public Object getOwner() {
      return this;
    }

    @Override
    public String getEditorTabName() {
      String name = myGetName.get();
      return name == null ? "" : name;
    }
  }
}
