// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.diff.impl.DiffRequestProcessor;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.actions.diff.lst.LocalChangeListDiffTool;
import com.intellij.openapi.vcs.changes.ui.ChangesTree;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
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

  private final MergingUpdateQueue mySelectInEditor;

  public EditorTabPreview(@NotNull DiffRequestProcessor changeProcessor,
                          @NotNull JComponent contentPanel, @NotNull ChangesTree changesTree) {
    myProject = ObjectUtils.assertNotNull(changeProcessor.getProject());

    mySelectInEditor = new MergingUpdateQueue("selectInEditorQueue", 100, true, MergingUpdateQueue.ANY_COMPONENT, myProject, null, true);
    mySelectInEditor.setRestartTimerOnAdd(true);

    myChangeProcessor = changeProcessor;
    MyDiffPreviewProvider previewProvider = new MyDiffPreviewProvider(changeProcessor, this::getCurrentName);
    myPreviewDiffVirtualFile = new PreviewDiffVirtualFile(previewProvider);
    myVcsConfiguration = VcsConfiguration.getInstance(myProject);

    //do not open file aggressively on start up, do it later
    DumbService.getInstance(myProject).smartInvokeLater(() -> {
      if (myProject.isDisposed()) return;

      changesTree.addSelectionListener(() -> {
        mySelectInEditor.queue(Update.create(this, () -> {
          if (skipPreviewUpdate()) return;

          setDiffPreviewVisible(true);
        }));
      });
    });

    new DumbAwareAction() {
      {
        copyShortcutFrom(ActionManager.getInstance().getAction(IdeActions.ACTION_NEXT_DIFF));
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        openEditorPreview(true);
      }
    }.registerCustomShortcutSet(contentPanel, null);
  }

  @Nullable
  protected abstract String getCurrentName();

  protected abstract void doRefresh();

  protected boolean skipPreviewUpdate() {
    return ToolWindowManager.getInstance(myProject).isEditorComponentActive();
  }

  protected boolean isContentEmpty() {
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

    if (!isVisible || isContentEmpty()) {
      FileEditorManager.getInstance(myProject).closeFile(myPreviewDiffVirtualFile);
    }
    else {
      openEditorPreview(false);
    }
  }

  @Override
  public void setAllowExcludeFromCommit(boolean value) {
    myChangeProcessor.putContextUserData(LocalChangeListDiffTool.ALLOW_EXCLUDE_FROM_COMMIT, value);
    myChangeProcessor.updateRequest(true);
  }

  @NotNull
  private PreviewDiffVirtualFile getVcsContentFile() {
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

  protected abstract boolean hasContent();

  public void closeEditorPreviewIfEmpty() {
    if (!hasContent()) closeEditorPreview();
  }

  public void closeEditorPreview() {
    FileEditorManager.getInstance(myProject).closeFile(getVcsContentFile());
  }

  public void openEditorPreview(boolean focus) {
    if (hasContent()) {
      boolean wasOpen = FileEditorManager.getInstance(myProject).isFileOpen(myPreviewDiffVirtualFile);

      FileEditor[] fileEditors = FileEditorManager.getInstance(myProject).openFile(getVcsContentFile(), focus, true);

      if (!wasOpen) {
        DumbAwareAction action = new DumbAwareAction() {
          {
            setShortcutSet(CommonShortcuts.ESCAPE);
          }

          @Override
          public void actionPerformed(@NotNull AnActionEvent e) {
            ToolWindowManager.getInstance(myProject).getToolWindow("Commit").activate(() -> { });
          }
        };
        action.registerCustomShortcutSet(fileEditors[0].getComponent(), null);

        Disposer.register(fileEditors[0], () -> {
          action.unregisterCustomShortcutSet(fileEditors[0].getComponent());
        });
      }
    }
  }
}
