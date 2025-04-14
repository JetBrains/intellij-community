// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import org.jetbrains.annotations.NonNls

/**
 * Holds information about a remote in Git repository.
 *
 * Git remote as defined in `.git/config` may contain url(s) and pushUrl(s).
 * If no pushUrl is given, then url is used to fetch and to push. Otherwise url is used to fetch, pushUrl is used to push.
 * If there are several urls and no pushUrls, then 1 url is used to fetch (because it is not possible and makes no sense to fetch
 * several urls at once), and all urls are used to push. If there are several urls and at least one pushUrl, then only pushUrl(s)
 * are used to push.
 * There are also some rules about url substitution, like `url.<base>.insteadOf`.
 *
 * GitRemote instance constructed by [GitConfig.read] has all these rules applied.
 * Thus, for example, if only one `url` and no `pushUrls` are defined for the remote,
 * both [urls] and [pushUrls] will return this url.
 * This is made to avoid urls transformation logic from the code using GitRemote, leaving it all in GitConfig parsing.
 *
 * This is not applied to fetch and push specs though: [pushRefSpecs] returns the spec,
 * only if it is defined in `.git/config`.
 *
 * NB: Not all remote preferences (defined in `.git/config`) are stored in the object.
 * If some additional data is needed, add the field, getter, constructor parameter and populate it in [GitConfig].
 *
 * Remotes are compared (via equals, hashcode and compareTo) only by names.
 */
class GitRemote(
  val name: String,
  /**
   * All urls specified in gitconfig in `remote.<name>.url`.
   * If you need url to fetch, use [firstUrl], because only the first url is fetched by Git,
   * others are ignored.
   */
  val urls: List<String>,
  val pushUrls: Collection<String>,
  val fetchRefSpecs: List<String>,
  val pushRefSpecs: List<String>
) : Comparable<GitRemote> {
  /**
   * The first url (to fetch) or null if and only if there are no urls defined for the remote.
   */
  val firstUrl: String?
    get() = urls.firstOrNull()

  override fun equals(o: Any?): Boolean {
    if (this === o) return true
    if (o == null || javaClass != o.javaClass) return false

    val gitRemote = o as GitRemote
    return this.name == gitRemote.name

    // other parameters don't count: remotes are equal if their names are equal
    // TODO: LOG.warn if other parameters differ
  }

  override fun hashCode(): Int {
    return name.hashCode()
  }

  override fun toString(): @NonNls String {
    return "GitRemote(name='$name', urls=$urls, pushUrls=$pushUrls, fetchRefSpecs=$fetchRefSpecs, pushRefSpecs=$pushRefSpecs)"
  }


  override fun compareTo(o: GitRemote): Int {
    return name.compareTo(o.name)
  }

  companion object {
    /**
     * This is a special instance of GitRemote used in typical git-svn configurations like:
     * [branch "trunk"]
     * remote = .
     * merge = refs/remotes/git-svn
     */
    @JvmField
    val DOT: GitRemote = GitRemote(".", listOf("."), emptyList(), emptyList(), emptyList())

    /**
     * Default remote name in Git is "origin".
     * Usually all Git repositories have an "origin" remote, so it can be used as a default value in some cases.
     */
    const val ORIGIN: String = "origin"
  }
}
