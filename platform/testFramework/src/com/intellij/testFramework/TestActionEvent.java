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
package com.intellij.testFramework;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.InputEvent;

/**
 * @author Dmitry Avdeev
 */
public final class TestActionEvent extends AnActionEvent {

  private static final String PLACE = "";

  public TestActionEvent(@NotNull DataContext dataContext,
                         @NotNull AnAction action) {
    super(null, dataContext, PLACE, action.getTemplatePresentation().clone(), ActionManager.getInstance(), 0);
  }

  public TestActionEvent(@NotNull AnAction action) {
    this(DataManager.getInstance().getDataContext(), action);
  }

  public TestActionEvent(DataContext context) {
    super(null, context, PLACE, new Presentation(), ActionManager.getInstance(), 0);
  }

  public TestActionEvent() {
    super(null, DataManager.getInstance().getDataContext(), PLACE, new Presentation(), ActionManager.getInstance(), 0);
  }

  public static @NotNull AnActionEvent createTestEvent() {
    return createTestEvent(null, null, null);
  }

  public static @NotNull AnActionEvent createTestEvent(@NotNull AnAction action) {
    return createTestEvent(action, null, null);
  }

  public static @NotNull AnActionEvent createTestEvent(@NotNull DataContext context) {
    return createTestEvent(null, context, null);
  }

  public static @NotNull AnActionEvent createTestEvent(@NotNull AnAction action,
                                                       @NotNull DataContext context) {
    return createTestEvent(action, context, null);
  }

  public static @NotNull AnActionEvent createTestEvent(@Nullable AnAction action,
                                                       @Nullable DataContext context,
                                                       @Nullable InputEvent inputEvent) {
    return AnActionEvent.createFromInputEvent(
      inputEvent, PLACE, action == null ? null : action.getTemplatePresentation().clone(),
      context != null ? context : DataManager.getInstance().getDataContext(), false, false);
  }

  public static @NotNull AnActionEvent createTestToolbarEvent(@Nullable Presentation presentation) {
    return AnActionEvent.createFromInputEvent(
      null, PLACE, presentation, DataManager.getInstance().getDataContext(), false, true);
  }
}
