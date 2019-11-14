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

import static com.intellij.openapi.util.text.StringUtil.notNullize;

public abstract class EditorTabPreview implements ChangesViewPreview {
  private final Project myProject;
  @NotNull private final PreviewDiffVirtualFile myPreviewDiffVirtualFile;
  private final VcsConfiguration myVcsConfiguration;
  private final DiffRequestProcessor myChangeProcessor;

  public EditorTabPreview(@NotNull DiffRequestProcessor changeProcessor,
                   @NotNull JComponent contentPanel, @NotNull ChangesTree changesTree) {
    myProject = changeProcessor.getProject();

    myChangeProcessor = changeProcessor;
    MyDiffPreviewProvider previewProvider = new MyDiffPreviewProvider(changeProcessor, this::getCurrentName);
    myPreviewDiffVirtualFile = new PreviewDiffVirtualFile(previewProvider);
    myVcsConfiguration = VcsConfiguration.getInstance(myProject);

    //do not open file aggressively on start up, do it later
    DumbService.getInstance(myProject).smartInvokeLater(() -> {
      if (myProject.isDisposed()) return;

      changesTree.addSelectionListener(() -> {
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
        FileEditorManager.getInstance(myProject).openFile(myPreviewDiffVirtualFile, true, true);
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
    if (myVcsConfiguration.LOCAL_CHANGES_DETAILS_PREVIEW_SHOWN) {
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
    myChangeProcessor.putContextUserData(LocalChangeListDiffTool.ALLOW_EXCLUDE_FROM_COMMIT, value);
    myChangeProcessor.updateRequest(true);
  }

  @NotNull
  protected PreviewDiffVirtualFile getVcsContentFile() {
    return myPreviewDiffVirtualFile;
  }

  private static class MyDiffPreviewProvider implements DiffPreviewProvider {
    @NotNull
    private final DiffRequestProcessor myChangeProcessor;
    @NotNull
    private final Supplier<String> myGetName;

    private MyDiffPreviewProvider(@NotNull DiffRequestProcessor changeProcessor, @NotNull Supplier<String> getName) {
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
      return notNullize(myGetName.get());
    }
  }
}
