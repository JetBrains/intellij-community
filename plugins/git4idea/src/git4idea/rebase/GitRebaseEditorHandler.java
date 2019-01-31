/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package git4idea.rebase;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * <p>Serves as the GIT_EDITOR during interactive rebase: it is called by Git instead of vim,
 * and allows to edit the list of rebased commits, and to reword commit messages.</p>
 * <p>This handler should be registered in the {@link GitRebaseEditorService}.</p>
 */
public interface GitRebaseEditorHandler {

  /**
   * Handle the request from Git to edit some information during rebase.
   * Such information can be: the list of commits to be interactively rebased, or a commit message to be reworded.
   *
   * @param path the path of the file to edit: default text should be read from this file and should be saved to this file after editing.
   * @return the exit code which will be returned to Git from the editor.
   */
  int editCommits(@NotNull String path);

  /**
   * Unique number of the handler registered in the {@link com.intellij.ide.XmlRpcServer}
   */
  @NotNull
  UUID getHandlerNo();

  /**
   * Tells if the interactive rebase editor (with the list of commits to rebase) was cancelled by user.
   * @see #wasUnstructuredEditorCancelled()
   */
  boolean wasCommitListEditorCancelled();

  /**
   * Tells if the commit message editor (appearing e.g. during squash or reword) was cancelled by user.
   * <br/><br/>
   * Note: Returning true obviously implies that {@link #wasCommitListEditorCancelled()} if false.
   * @see #wasCommitListEditorCancelled()
   */
  boolean wasUnstructuredEditorCancelled();
}
