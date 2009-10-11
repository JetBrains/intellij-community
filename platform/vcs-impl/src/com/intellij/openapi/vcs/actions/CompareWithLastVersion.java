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
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.impl.VcsBackgroundableActions;
import com.intellij.openapi.vfs.VirtualFile;

public class CompareWithLastVersion extends AbstractShowDiffAction{
  @Override
  protected VcsBackgroundableActions getKey() {
    return VcsBackgroundableActions.COMPARE_WITH;
  }

  @Override
  protected DiffActionExecutor getExecutor(final DiffProvider diffProvider, final VirtualFile selectedFile, final Project project) {
    return new DiffActionExecutor.DeletionAwareExecutor(diffProvider, selectedFile, project, getKey());
  }
}
