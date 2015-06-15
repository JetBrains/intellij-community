/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import java.util.Collection;

/**
 * Tells to filter by branches with given names.
 * <p/>
 * There are two filters possible here:<ul>
 * <li>accept only given branches: {@link #getBranchNames()};</li>
 * <li>deny the given branches: {@link #getExcludedBranchNames()}</li></ul>
 * Note though that accepted branch names have higher precedence over excluded ones:
 * only those commits are excluded, which are contained <b>only</b> in excluded branches:
 * i.e. if a commit contains in an excluded branch, and in a non-excluded branch, then it should be shown.
 * <p/>
 * That means, in particular, that a filter with one accepted branch will show all and only commits from that branch,
 * and excluded branches will have no effect.
 */
public interface VcsLogBranchFilter extends VcsLogFilter {

  @NotNull
  Collection<String> getBranchNames();

  @NotNull
  Collection<String> getExcludedBranchNames();
}
