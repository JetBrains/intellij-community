package org.jetbrains.plugins.github;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Information about Github repository.
 *
 * @author oleg
 * @author Kirill Likhodedov
 */
public class RepositoryInfo {

  @NotNull private final String myName;
  @NotNull private final String myCloneUrl;
  @NotNull private final String myOwnerName;
  @Nullable private final String myParentName;
  private final boolean myFork;

  public RepositoryInfo(@NotNull String name, @NotNull String cloneUrl, @NotNull String ownerName, @Nullable String parentName,
                        boolean fork) {
    myName = name;
    myParentName = parentName;
    myCloneUrl = cloneUrl;
    myOwnerName = ownerName;
    myFork = fork;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public String getOwnerName() {
    return myOwnerName;
  }

  public boolean isFork() {
    return myFork;
  }

  /**
   * @return The name of the parent of this repository, or null.
   *         Null is returned if this repository doesn't have a parent, i. e. is not a fork,
   *         or if the parent information was not retrieved by the time of constructing of this RepositoryInfo object.
   *         To be sure use {@link #isFork()}.
   */
  @Nullable
  public String getParentName() {
    return myParentName;
  }

  @NotNull
  public String getCloneUrl() {
    return myCloneUrl;
  }

}
