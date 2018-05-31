/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package git4idea

import git4idea.config.GitSharedSettings
import git4idea.repo.GitRepository

/**
 * Checks if there is a protected remote branch among the given branches, and returns one of them, or `null` otherwise.
 *
 * `branches` are given in the "local" format, e.g. `origin/master`.
 */
fun findProtectedRemoteBranch(repository: GitRepository, branches: Collection<String>): String? {
  val settings = GitSharedSettings.getInstance(repository.project)
  // protected branches hold patterns for branch names without remote names
  return repository.branches.remoteBranches.
    filter { settings.isBranchProtected(it.nameForRemoteOperations) }.
    map { it.nameForLocalOperations }.
    firstOrNull { branches.contains(it) }
}
