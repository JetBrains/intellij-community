/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package git4idea.branch;

import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
* @author Kirill Likhodedov
*/
interface GitBranchOperation {

  /**
   * Executes the native operation on the given repository and returns the result.
   */
  @NotNull
  GitBranchOperationResult execute(@NotNull GitRepository repository);

  /**
   * Rolls back the operation on selected repositories. Keep empty if not {@link #rollbackable()}.
   */
  void rollback(@NotNull Collection<GitRepository> repositories);

  /**
   * Tells if this operation can be rolled back.
   */
  boolean rollbackable();

  /**
   * Tries to resolve a problem found during operation execution (for example, unmerged files preventing to checkout a branch).
   */
  @NotNull
  GitBranchOperationResult tryResolve();

  /**
   * Returns the message which should be displayed in a notification in the case of successful result.
   */
  @NotNull
  String getSuccessMessage();

  /**
   * Show fatal error notification or return false to let the executor show the common fatal error message.
   * @return true to indicate that this operation has shown the error, false to let the executor show the standard error message.
   */
  boolean showFatalError();

}
