package com.intellij.openapi.vcs.roots;

import com.intellij.openapi.vcs.VcsRoot;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Nadya Zabrodina
 */
public class VcsRootDetectInfo {

  private final @NotNull Collection<VcsRoot> myRoots;
  private final boolean myBelow;

  /**
   * @param roots Vcs roots important for the project.
   * @param below Pass true to indicate that the project dir is below Vcs dir,
   */
  public VcsRootDetectInfo(@NotNull Collection<VcsRoot> roots, boolean below) {
    myRoots = new ArrayList<VcsRoot>(roots);
    myBelow = below;
  }

  public boolean empty() {
    return myRoots.isEmpty();
  }

  @NotNull
  public Collection<VcsRoot> getRoots() {
    return new ArrayList<VcsRoot>(myRoots);
  }

  /**
   * Below implies totally under Vcs.
   *
   * @return true if the uppermost interesting Vcs root is above the project dir,
   * false if all vcs internal directories are immediately under the project dir or deeper.
   */
  public boolean projectIsBelowVcs() {
    return myBelow;
  }
}
