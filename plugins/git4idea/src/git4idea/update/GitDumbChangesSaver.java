/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package git4idea.update;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;

/**
 * Does nothing to save or restore local changes.
 * This is dangerous and may lead to errors (in the case of rebase) or conflicts (in the case of merge),
 * but if users chooses not to clean the working tree, then this {@link GitChangesSaver} is used.
 *
 * @author Kirill Likhodedov
 */
public class GitDumbChangesSaver extends GitChangesSaver {

  protected GitDumbChangesSaver(Project project, ProgressIndicator indicator, String stashMessage) {
    super(project, indicator, stashMessage);
  }

  public void saveLocalChanges() {
    // do nothing
  }

  public void restoreLocalChanges() {
    // do nothing
  }

  @Override
  protected void save() throws VcsException {
  }

  @Override
  protected void load() throws VcsException {
  }
}
