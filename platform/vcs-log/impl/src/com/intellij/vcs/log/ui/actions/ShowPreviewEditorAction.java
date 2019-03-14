// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.actions;

import com.intellij.diff.editor.DiffVirtualFile;
import com.intellij.diff.impl.DiffRequestProcessor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.vcs.log.VcsLogDataKeys;
import com.intellij.vcs.log.VcsLogUi;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.function.Supplier;

import static com.intellij.util.ObjectUtils.notNull;

public class ShowPreviewEditorAction extends DumbAwareAction {
  public static final DataKey<Supplier<DiffRequestProcessor>> DATA_KEY = DataKey.create("com.intellij.diff.impl.DiffRequestProcessor");

  public ShowPreviewEditorAction() {
    super("Show Diff Preview in Editor", null, AllIcons.Actions.ChangeView);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (!Registry.is("show.diff.preview.as.editor.tab")) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    Project project = e.getProject();
    VcsLogUi owner = e.getData(VcsLogDataKeys.VCS_LOG_UI);
    Supplier<DiffRequestProcessor> diffPreviewSupplier = e.getData(DATA_KEY);
    e.getPresentation().setEnabledAndVisible(project != null && diffPreviewSupplier != null && owner != null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = notNull(e.getProject());
    VcsLogUi owner = e.getRequiredData(VcsLogDataKeys.VCS_LOG_UI);
    Supplier<DiffRequestProcessor> diffPreviewSupplier = e.getRequiredData(DATA_KEY);
    FileEditorManager.getInstance(project).openFile(new MyDiffVirtualFile(owner, diffPreviewSupplier), true);
  }

  private static class MyDiffVirtualFile extends DiffVirtualFile {
    @NotNull private final Object myOwner;
    @NotNull private final Supplier<DiffRequestProcessor> myDiffPreviewSupplier;

    private MyDiffVirtualFile(@NotNull Object owner,
                              @NotNull Supplier<DiffRequestProcessor> diffPreviewSupplier) {
      myOwner = owner;
      myDiffPreviewSupplier = diffPreviewSupplier;
    }

    @Override
    public boolean isValid() {
      if (!(myOwner instanceof Disposable)) return true;
      return !Disposer.isDisposed((Disposable)myOwner);
    }

    @NotNull
    @Override
    public Builder createProcessorAsync(@NotNull Project project) {
      return () -> myDiffPreviewSupplier.get();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      MyDiffVirtualFile file = (MyDiffVirtualFile)o;
      return myOwner.equals(file.myOwner);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myOwner);
    }
  }
}
