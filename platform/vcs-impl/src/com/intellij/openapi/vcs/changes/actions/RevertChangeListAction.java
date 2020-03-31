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
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class RevertChangeListAction extends RevertCommittedStuffAbstractAction {
  public RevertChangeListAction() {
    super(true);
  }

  @Override
  protected Change @Nullable [] getChanges(@NotNull AnActionEvent e, boolean isFromUpdate) {
    if (isFromUpdate) {
      return e.getData(VcsDataKeys.CHANGES);
    }
    else {
      return e.getData(VcsDataKeys.CHANGES_WITH_MOVED_CHILDREN);
    }
  }
}
