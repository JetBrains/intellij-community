// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static java.util.Collections.emptyList;

/**
 * <p>
 *   Holds information about a remote in Git repository.
 * </p>
 *
 * <p>
 *   Git remote as defined in {@code .git/config} may contain url(s) and pushUrl(s). <br/>
 *   If no pushUrl is given, then url is used to fetch and to push. Otherwise url is used to fetch, pushUrl is used to push.
 *   If there are several urls and no pushUrls, then 1 url is used to fetch (because it is not possible and makes no sense to fetch
 *   several urls at once), and all urls are used to push. If there are several urls and at least one pushUrl, then only pushUrl(s)
 *   are used to push.
 *   There are also some rules about url substitution, like {@code url.<base>.insteadOf}.
 * </p>
 * <p>
 *   GitRemote instance constructed by {@link GitConfig#read(File)}} has all these rules applied.
 *   Thus, for example, if only one {@code url} and no {@code pushUrls} are defined for the remote,
 *   both {@link #getUrls()} and {@link #getPushUrls()} will return this url. <br/>
 *   This is made to avoid urls transformation logic from the code using GitRemote, leaving it all in GitConfig parsing.
 * </p>
 * <p>
 *   This is not applied to fetch and push specs though: {@link #getPushRefSpecs()} returns the spec,
 *   only if it is defined in {@code .git/config}.
 * </p>
 *
 * <p>
 *   NB: Not all remote preferences (defined in {@code .git/config} are stored in the object.
 *   If some additional data is needed, add the field, getter, constructor parameter and populate it in {@link GitConfig}.
 * </p>
 *
 * <p>Remotes are compared (via equals, hashcode and compareTo) only by names.</p>
 */
public final class GitRemote implements Comparable<GitRemote> {

  /**
   * This is a special instance of GitRemote used in typical git-svn configurations like:
   * [branch "trunk"]
   *   remote = .
   *   merge = refs/remotes/git-svn
   */
  public static final GitRemote DOT = new GitRemote(".", Collections.singletonList("."), emptyList(), emptyList(), emptyList());

  /**
   * Default remote name in Git is "origin".
   * Usually all Git repositories have an "origin" remote, so it can be used as a default value in some cases.
   */
  public static final String ORIGIN = "origin";

  private final @NotNull String myName;
  private final @NotNull List<String> myUrls;
  private final @NotNull Collection<String> myPushUrls;
  private final @NotNull List<String> myFetchRefSpecs;
  private final @NotNull List<String> myPushRefSpecs;

  public GitRemote(@NotNull String name, @NotNull List<String> urls, @NotNull Collection<String> pushUrls,
                   @NotNull List<String> fetchRefSpecs, @NotNull List<String> pushRefSpecs) {
    myName = name;
    myUrls = urls;
    myPushUrls = pushUrls;
    myFetchRefSpecs = fetchRefSpecs;
    myPushRefSpecs = pushRefSpecs;
  }

  public @NotNull @NlsSafe String getName() {
    return myName;
  }

  /**
   * Returns all urls specified in gitconfig in {@code remote.<name>.url}.
   * If you need url to fetch, use {@link #getFirstUrl()}, because only the first url is fetched by Git,
   * others are ignored.
   */
  public @NotNull List<String> getUrls() {
    return myUrls;
  }

  /**
   * @return the first url (to fetch) or null if and only if there are no urls defined for the remote.
   */
  public @Nullable String getFirstUrl() {
    return myUrls.isEmpty() ? null : myUrls.get(0);
  }

  public @NotNull Collection<String> getPushUrls() {
    return myPushUrls;
  }

  public @NotNull List<String> getFetchRefSpecs() {
    return myFetchRefSpecs;
  }

  public @NotNull List<String> getPushRefSpecs() {
    return myPushRefSpecs;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GitRemote gitRemote = (GitRemote)o;
    return myName.equals(gitRemote.myName);

    // other parameters don't count: remotes are equal if their names are equal
    // TODO: LOG.warn if other parameters differ
  }

  @Override
  public int hashCode() {
    return myName.hashCode();
  }

  @Override
  public @NonNls String toString() {
    return String.format("GitRemote{myName='%s', myUrls=%s, myPushUrls=%s, myFetchRefSpec='%s', myPushRefSpec='%s'}",
                         myName, myUrls, myPushUrls, myFetchRefSpecs, myPushRefSpecs);
  }

  @Override
  public int compareTo(@NotNull GitRemote o) {
    return getName().compareTo(o.getName());
  }

}
