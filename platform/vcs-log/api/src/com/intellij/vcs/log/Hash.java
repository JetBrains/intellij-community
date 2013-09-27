package com.intellij.vcs.log;

import org.jetbrains.annotations.NotNull;

/**
 * <p>Represents a unique reference to a VCS commit.</p>
 *
 * <p>It is called "Hash", because in DVCSs it is represented by a SHA-hash value.</p>
 *
 * TODO move to vcs-api
 *
 * @author Kirill Likhodedov
 * @author erokhins
 */
public interface Hash {

  /**
   * Returns the String representation of this hash.
   */
  @NotNull
  String asString();

  /**
   * <p>
   *   Returns a short part of the {@link #asString() hash string},
   *   which may be non-unique, but usually enough to be a commit reference, and it is easier to read than the complete hash string.
   * </p>
   *
   * <p>Usually (e.g. it is default for Git) the short hash is 7 symbols long.</p>
   */
  @NotNull
  String toShortString();

}
