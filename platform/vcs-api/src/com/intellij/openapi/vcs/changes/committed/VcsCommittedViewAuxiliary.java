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
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.actionSystem.AnAction;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class VcsCommittedViewAuxiliary {

  @NotNull private final List<AnAction> myToolbarActions;
  @NotNull private final List<AnAction> myPopupActions;
  @NotNull private final Runnable myCalledOnViewDispose;

  public VcsCommittedViewAuxiliary(@NotNull List<AnAction> popupActions, @NotNull Runnable calledOnViewDispose,
                                   @NotNull List<AnAction> toolbarActions) {
    myToolbarActions = toolbarActions;
    myPopupActions = popupActions;
    myCalledOnViewDispose = calledOnViewDispose;
  }

  @NotNull
  public List<AnAction> getPopupActions() {
    return myPopupActions;
  }

  @NotNull
  public Runnable getCalledOnViewDispose() {
    return myCalledOnViewDispose;
  }

  @NotNull
  public List<AnAction> getToolbarActions() {
    return myToolbarActions;
  }
}
