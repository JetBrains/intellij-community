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
package com.intellij.xdebugger.impl.evaluate.quick.common;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

import java.awt.*;

/**
 * @author nik
 */
public abstract class QuickEvaluateHandler {

  public abstract boolean isEnabled(@NotNull Project project);

  public boolean isEnabled(@NotNull Project project, @NotNull AnActionEvent event) {
    return isEnabled(project);
  }

  @Nullable
  public abstract AbstractValueHint createValueHint(@NotNull Project project, @NotNull Editor editor, @NotNull Point point, ValueHintType type);

  @NotNull
  public Promise<AbstractValueHint> createValueHintAsync(@NotNull Project project, @NotNull Editor editor, @NotNull Point point, ValueHintType type) {
    return Promise.resolve(createValueHint(project, editor, point, type));
  }

  public abstract boolean canShowHint(@NotNull Project project);

  public abstract int getValueLookupDelay(final Project project);
}
