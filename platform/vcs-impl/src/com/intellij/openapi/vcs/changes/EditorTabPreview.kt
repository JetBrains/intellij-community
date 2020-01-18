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
  @NotNull private final PreviewDiffVirtualFile myPreviewFile;
  private final DiffRequestProcessor myDiffProcessor;

  private final MergingUpdateQueue myUpdatePreviewQueue;

  public EditorTabPreview(@NotNull DiffRequestProcessor diffProcessor, @NotNull JComponent contentPanel, @NotNull ChangesTree changesTree) {
    myProject = ObjectUtils.assertNotNull(diffProcessor.getProject());

    myUpdatePreviewQueue =
      new MergingUpdateQueue("updatePreviewQueue", 100, true, MergingUpdateQueue.ANY_COMPONENT, myProject, null, true);
    myUpdatePreviewQueue.setRestartTimerOnAdd(true);

    myDiffProcessor = diffProcessor;
    MyDiffPreviewProvider previewProvider = new MyDiffPreviewProvider(diffProcessor, this::getCurrentName);
    myPreviewFile = new PreviewDiffVirtualFile(previewProvider);

    //do not open file aggressively on start up, do it later
    DumbService.getInstance(myProject).smartInvokeLater(() -> {
      if (myProject.isDisposed()) return;

      changesTree.addSelectionListener(() -> {
        myUpdatePreviewQueue.queue(Update.create(this, () -> {
          if (skipPreviewUpdate()) return;

          setPreviewVisible(true);
        }));
      });
    });

    new DumbAwareAction() {
      {
        copyShortcutFrom(ActionManager.getInstance().getAction(IdeActions.ACTION_NEXT_DIFF));
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        openPreview(true);
      }
    }.registerCustomShortcutSet(contentPanel, null);
  }

  @Nullable
  protected abstract String getCurrentName();

  protected boolean skipPreviewUpdate() {
    return ToolWindowManager.getInstance(myProject).isEditorComponentActive();
  }

  @Override
  public void updatePreview(boolean fromModelRefresh) {
    if (myDiffProcessor instanceof DiffPreviewUpdateProcessor) {
      ((DiffPreviewUpdateProcessor)myDiffProcessor).refresh(false);
    }
    if (!hasContent()) closePreview();
  }

  @Override
  public void setPreviewVisible(boolean isPreviewVisible) {
    updatePreview(false);

    if (isPreviewVisible) {
      openPreview(false);
    }
    else {
      closePreview();
    }
  }

  @Override
  public void setAllowExcludeFromCommit(boolean value) {
    myDiffProcessor.putContextUserData(LocalChangeListDiffTool.ALLOW_EXCLUDE_FROM_COMMIT, value);
    myDiffProcessor.updateRequest(true);
  }

  private static class MyDiffPreviewProvider implements DiffPreviewProvider {
    @NotNull
    private final DiffRequestProcessor myDiffProcessor;
    @NotNull
    private final Supplier<String> myGetName;

    private MyDiffPreviewProvider(@NotNull DiffRequestProcessor diffProcessor, @NotNull Supplier<String> getName) {
      myDiffProcessor = diffProcessor;
      myGetName = getName;
    }

    @NotNull
    @Override
    public DiffRequestProcessor createDiffRequestProcessor() {
      return myDiffProcessor;
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

  public void closePreview() {
    FileEditorManager.getInstance(myProject).closeFile(myPreviewFile);
  }

  public void openPreview(boolean focus) {
    if (hasContent()) {
      boolean wasOpen = FileEditorManager.getInstance(myProject).isFileOpen(myPreviewFile);

      FileEditor[] fileEditors = FileEditorManager.getInstance(myProject).openFile(myPreviewFile, focus, true);

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
