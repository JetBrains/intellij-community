/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.actions.diff;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.util.diff.DiffDialogHints;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ShowDiffContext {
  @NotNull private final DiffDialogHints myDialogHints;
  @NotNull private final Map<String, Object> myContext;
  @NotNull private final List<AnAction> myActions;

  public ShowDiffContext() {
    this(DiffDialogHints.NON_MODAL, Collections.<AnAction>emptyList(), Collections.<String, Object>emptyMap());
  }

  public ShowDiffContext(boolean isModal) {
    this(isModal ? DiffDialogHints.MODAL : DiffDialogHints.NON_MODAL,
         Collections.<AnAction>emptyList(),
         Collections.<String, Object>emptyMap());
  }

  public ShowDiffContext(@NotNull DiffDialogHints dialogHints,
                         @NotNull List<AnAction> actions,
                         @NotNull Map<String, Object> context) {
    myDialogHints = dialogHints;
    myContext = context;
    myActions = actions;
  }

  @NotNull
  public DiffDialogHints getDialogHints() {
    return myDialogHints;
  }

  @NotNull
  public Map<String, Object> getContext() {
    return myContext;
  }

  @NotNull
  public List<AnAction> getActions() {
    return myActions;
  }
}
