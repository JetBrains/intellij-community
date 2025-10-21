// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.branch;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.vcs.log.VcsLogUi;
import com.intellij.vcs.log.data.VcsLogData;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

@Service(Service.Level.PROJECT)
public final class CherryPickedCommitsHighlighterHolder implements Disposable {
  private final @NotNull Project myProject;
  private final @NotNull Map<VcsLogUi, CherryPickedCommitsHighlighter> myHighlighters;

  private CherryPickedCommitsHighlighterHolder(@NotNull Project project) {
    myProject = project;
    myHighlighters = new HashMap<>();
  }

  public @NotNull CherryPickedCommitsHighlighter getInstance(@NotNull VcsLogData dataProvider, @NotNull VcsLogUi ui) {
    CherryPickedCommitsHighlighter highlighter = myHighlighters.get(ui);
    if (highlighter == null) {
      highlighter = new CherryPickedCommitsHighlighter(myProject, GitRepositoryManager.getInstance(myProject), dataProvider, ui, this);
      myHighlighters.put(ui, highlighter);
      if (ui instanceof Disposable) {
        Disposer.register((Disposable)ui, new Disposable() {
          @Override
          public void dispose() {
            CherryPickedCommitsHighlighter removed = myHighlighters.remove(ui);
            if (removed != null) Disposer.dispose(removed); // check for null in case we dispose DeepComparatorHolder before ui
          }
        });
      }
    }
    return highlighter;
  }

  @Override
  public void dispose() {
    myHighlighters.clear();
  }
}
