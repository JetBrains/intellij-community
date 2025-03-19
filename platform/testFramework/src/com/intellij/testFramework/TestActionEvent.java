// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.InputEvent;

public final class TestActionEvent extends AnActionEvent {

  private static final String PLACE = "";

  /**
   * @deprecated use {@link #createTestEvent(AnAction, DataContext)}
   */
  @Deprecated(forRemoval = true)
  public TestActionEvent(@NotNull DataContext dataContext,
                         @NotNull AnAction action) {
    super(null, dataContext, PLACE, action.getTemplatePresentation().clone(), ActionManager.getInstance(), 0);
  }

  /**
   * @deprecated use {@link #createTestEvent(DataContext)} instead
   */
  @Deprecated(forRemoval = true)
  public TestActionEvent(DataContext context) {
    super(null, context, PLACE, new Presentation(), ActionManager.getInstance(), 0);
  }

  /**
   * @deprecated use {@link #createTestEvent()} instead
   */
  @Deprecated(forRemoval = true)
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
    return createEvent(
      context != null ? context : DataManager.getInstance().getDataContext(),
      action == null ? null : action.getTemplatePresentation().clone(),
      PLACE, ActionUiKind.NONE, inputEvent);
  }

  public static @NotNull AnActionEvent createTestToolbarEvent(@Nullable Presentation presentation) {
    DataContext dataContext = DataManager.getInstance().getDataContext();
    return createEvent(dataContext, presentation, PLACE, ActionUiKind.TOOLBAR, null);
  }
}
