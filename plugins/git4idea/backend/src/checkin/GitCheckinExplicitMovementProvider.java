// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.checkin;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.vcs.FilePath;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Extension point to force two-step committing of moved files.
 * This is necessary, when files are heavily modified and git rename detection might fail to detect renames.
 * 4ex: if files were converted from one programming language to another.
 * <p>
 * This will create two commits: commit with explicit file movements,
 * and commit with content modifications in these files and the rest of affected files
 */
public abstract class GitCheckinExplicitMovementProvider {
  public static final ExtensionPointName<GitCheckinExplicitMovementProvider> EP_NAME =
    ExtensionPointName.create("Git4Idea.GitCheckinExplicitMovementProvider");

  public abstract boolean isEnabled(@NotNull Project project);

  /**
   * @return Text for checkbox in commit options
   */
  public abstract @NotNull String getDescription();

  /**
   * @return commit message for the commit with movements
   */
  public abstract @NotNull String getCommitMessage(@NotNull String originalCommitMessage);

  /**
   * This method could be called several times per commit operation. For instance, to update commit options UI so that it reflects current
   * local changes state. Use {@link #afterMovementsCommitted(Project, List)} to perform after-commit cleanup if necessary.
   *
   * @return file movements, that should be committed explicitly
   */
  public abstract @NotNull Collection<Movement> collectExplicitMovements(@NotNull Project project,
                                                                @NotNull List<FilePath> beforePaths,
                                                                @NotNull List<FilePath> afterPaths);

  /**
   * Notifies that commit with explicit movements was created
   */
  public void afterMovementsCommitted(@NotNull Project project,
                                      @NotNull List<Couple<FilePath>> movedPaths) { }

  public static class Movement {
    private final @NotNull FilePath myBeforePath;
    private final @NotNull FilePath myAfterPath;

    public Movement(@NotNull FilePath beforePath, @NotNull FilePath afterPath) {
      myBeforePath = beforePath;
      myAfterPath = afterPath;
    }

    public @NotNull FilePath getBefore() {
      return myBeforePath;
    }

    public @NotNull FilePath getAfter() {
      return myAfterPath;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Movement movement = (Movement)o;
      return Objects.equals(myBeforePath, movement.myBeforePath) &&
             Objects.equals(myAfterPath, movement.myAfterPath);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myBeforePath, myAfterPath);
    }
  }
}
