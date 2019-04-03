// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.repo;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.VcsDirtyScope;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class GitConflictsHolder implements Disposable {
  @NotNull private final GitRepository myRepository;
  @NotNull private final List<GitConflict> myConflicts = new ArrayList<>();

  private final Object LOCK = new Object();

  public GitConflictsHolder(@NotNull GitRepository repository) {
    myRepository = repository;
  }

  @NotNull
  public List<GitConflict> getConflicts() {
    synchronized (LOCK) {
      return new ArrayList<>(myConflicts);
    }
  }

  @Override
  public void dispose() {
    synchronized (LOCK) {
      myConflicts.clear();
    }
  }

  public void refresh(@NotNull VcsDirtyScope scope, @NotNull Collection<GitConflict> conflicts) {
    synchronized (LOCK) {
      Map<FilePath, GitConflict> map = new HashMap<>();
      for (GitConflict conflict : myConflicts) {
        if (scope.belongsTo(conflict.getFilePath())) continue;
        map.put(conflict.getFilePath(), conflict);
      }

      for (GitConflict conflict : conflicts) {
        map.put(conflict.getFilePath(), conflict);
      }

      myConflicts.clear();
      myConflicts.addAll(map.values());
    }
  }
}
