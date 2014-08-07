/*
 * Copyright 2000-2010 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs.ex;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RollbackLineStatusRangeAction extends RollbackLineStatusAction {
  @NotNull private final LineStatusTracker myTracker;
  @Nullable private final Editor myEditor;
  @NotNull private final Range myRange;

  public RollbackLineStatusRangeAction(@NotNull LineStatusTracker tracker, @NotNull Range range, @Nullable Editor editor) {
    myTracker = tracker;
    myEditor = editor;
    myRange = range;
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(true);
  }

  public void actionPerformed(final AnActionEvent e) {
    rollback(myTracker, myEditor, myRange);
  }
}
