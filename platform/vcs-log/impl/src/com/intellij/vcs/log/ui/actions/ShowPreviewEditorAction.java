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
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

import static com.intellij.util.ObjectUtils.notNull;

//not used currently
@SuppressWarnings("ComponentNotRegistered")
public class ShowPreviewEditorAction extends DumbAwareAction {
  public static final DataKey<DiffPreviewProvider> DATA_KEY = DataKey.create("com.intellij.vcs.log.ui.actions.ShowPreviewEditorAction.DiffPreviewProvider");

  public ShowPreviewEditorAction() {
    super("Show Diff Preview in Editor", null, AllIcons.Actions.ChangeView);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (!Registry.is("show.diff.preview.as.editor.tab")) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    e.getPresentation().setEnabledAndVisible(e.getProject() != null && e.getData(DATA_KEY) != null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    FileEditorManager.getInstance(notNull(e.getProject())).openFile(new MyDiffVirtualFile(e.getRequiredData(DATA_KEY)), true);
  }

  public interface DiffPreviewProvider {
    @NotNull
    DiffRequestProcessor createDiffRequestProcessor();

    @NotNull
    Object getOwner();
  }

  private static class MyDiffVirtualFile extends DiffVirtualFile {
    @NotNull private final DiffPreviewProvider myProvider;

    private MyDiffVirtualFile(@NotNull DiffPreviewProvider provider) {
      super("Diff");
      myProvider = provider;
    }

    @Override
    public boolean isValid() {
      Object owner = myProvider.getOwner();
      if (!(owner instanceof Disposable)) return true;
      return !Disposer.isDisposed((Disposable)owner);
    }

    @NotNull
    @Override
    public DiffRequestProcessor createProcessor(@NotNull Project project) {
      return myProvider.createDiffRequestProcessor();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      MyDiffVirtualFile file = (MyDiffVirtualFile)o;
      return myProvider.getOwner().equals(file.myProvider.getOwner());
    }

    @Override
    public int hashCode() {
      return Objects.hash(myProvider.getOwner());
    }
  }
}
