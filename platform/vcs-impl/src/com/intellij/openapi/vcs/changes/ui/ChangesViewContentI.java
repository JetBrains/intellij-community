// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

public interface ChangesViewContentI {
  void attachToolWindow(@NotNull ToolWindow toolWindow);

  void addContent(@NotNull Content content);

  void removeContent(@NotNull Content content);

  void setSelectedContent(@NotNull Content content);

  void setSelectedContent(@NotNull Content content, boolean requestFocus);

  <T> @Nullable T getActiveComponent(@NotNull Class<T> aClass);

  void selectContent(@NotNull String tabName);

  @NotNull
  List<Content> findContents(@NotNull Predicate<Content> predicate);

  @Nullable
  Content findContent(@NotNull String tabName);
}
