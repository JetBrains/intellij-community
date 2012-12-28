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
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diff.DiffRequest;
import com.intellij.openapi.diff.DiffViewer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ChangeRequestChain;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
*/
public class ShowPrevChangeAction extends ShowChangeAbstractAction {

  @Override
  protected boolean isEnabled(@NotNull ChangeRequestChain chain) {
    return chain.canMoveBack();
  }

  @Override
  protected void actionPerformed(@NotNull AnActionEvent e, @NotNull Project project, @NotNull ChangeRequestChain chain,
                                 @NotNull DiffViewer diffViewer) {
    DiffRequest request = chain.moveBack();
    openRequest(diffViewer, request);
  }

}