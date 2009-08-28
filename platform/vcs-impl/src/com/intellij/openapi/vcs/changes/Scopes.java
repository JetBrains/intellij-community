package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.impl.DefaultVcsRootPolicy;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Scopes {
  private final Project myProject;
  private final VcsGuess myGuess;

  private boolean myEverythingDirty;
  private final Map<AbstractVcs, VcsDirtyScopeImpl> myScopes;

  public Scopes(final Project project, final VcsGuess guess) {
    myProject = project;
    myGuess = guess;
    myScopes = new HashMap<AbstractVcs, VcsDirtyScopeImpl>();
  }

  private void markEverythingDirty() {
    myScopes.clear();
    myEverythingDirty = true;
    final DirtBuilder builder = new DirtBuilder(myGuess);
    DefaultVcsRootPolicy.getInstance(myProject).markDefaultRootsDirty(builder, myGuess);
    takeDirt(builder);
  }

  public void takeDirt(final DirtBuilder dirt) {
    if (dirt.isEverythingDirty()) {
      markEverythingDirty();
      return;
    }
    final List<FilePathUnderVcs> dirs = dirt.getDirsForVcs();
    for (FilePathUnderVcs dir : dirs) {
      getScope(dir.getVcs()).addDirtyDirRecursively(dir.getPath());
    }
    final List<FilePathUnderVcs> files = dirt.getFilesForVcs();
    for (FilePathUnderVcs file : files) {
      getScope(file.getVcs()).addDirtyFile(file.getPath());
    }
  }

  public void addDirtyDirRecursively(@NotNull final AbstractVcs vcs, @NotNull final FilePath newcomer) {
    getScope(vcs).addDirtyDirRecursively(newcomer);
  }

  public void addDirtyFile(@NotNull final AbstractVcs vcs, @NotNull final FilePath newcomer) {
    getScope(vcs).addDirtyFile(newcomer);
  }

  public VcsInvalidated retrieveAndClear() {
    final ArrayList<VcsDirtyScope> scopesList = new ArrayList<VcsDirtyScope>(myScopes.values());
    final VcsInvalidated result = new VcsInvalidated(scopesList, myEverythingDirty);
    myEverythingDirty = false;
    myScopes.clear();
    return result;
  }

  private VcsDirtyScopeImpl getScope(final AbstractVcs vcs) {
    VcsDirtyScopeImpl scope = myScopes.get(vcs);
    if (scope == null) {
      scope = new VcsDirtyScopeImpl(vcs, myProject);
      myScopes.put(vcs, scope);
    }
    return scope;
  }
}
