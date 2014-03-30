/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.vcs.log;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * <p>Represents a commit in a VCS. A commit is represented by its {@link Hash} and the hashes of its parents.</p>
 *
 * <p>It is highly recommended to use the standard VcsCommit implementation which can be achieved from the
 *    {@link VcsLogObjectsFactory#createCommit(Hash, List) VcsLogObjectsFactory}.
 *    Otherwise it is important to implement the equals() and hashcode() methods so that they consider only the hash, and not other fields.
 *    If this rule is abandoned, it may lead to incorrect log building if different {@link VcsCommit} implementations meet together.</p>
 *
 * @author Kirill Likhodedov
 */
public interface VcsCommit {

  /**
   * Returns the hash of this commit.
   */
  @NotNull
  Hash getHash();

  /**
   * <p>Returns parents of this commit.</p>
   * <p>A commit usually has one parent, but it can have two in the case of merge, more in the case of so called octopus merge,
   *    or zero parents if it is the initial commit in the log
   *    (or one of initial commits, if histories from several repositories are merged together).</p>
   */
  @NotNull
  List<Hash> getParents();

}
