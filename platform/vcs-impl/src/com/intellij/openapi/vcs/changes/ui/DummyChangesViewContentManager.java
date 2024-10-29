// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

@ApiStatus.Internal
public class DummyChangesViewContentManager implements ChangesViewContentI {
  @Override
  public void attachToolWindow(@NotNull ToolWindow toolWindow) {
  }

  @Override
  public void addContent(@NotNull Content content) {
    Disposer.dispose(content);
  }

  @Override
  public void removeContent(@NotNull Content content) {
  }

  @Override
  public void setSelectedContent(@NotNull Content content) {
  }

  @Override
  public void setSelectedContent(@NotNull Content content, boolean requestFocus) {
  }

  @Nullable
  @Override
  public <T> T getActiveComponent(@NotNull Class<T> aClass) {
    return null;
  }

  @Override
  public void selectContent(@NotNull String tabName) {
  }

  @NotNull
  @Override
  public List<Content> findContents(@NotNull Predicate<Content> predicate) {
    return Collections.emptyList();
  }

  @Override
  public @Nullable Content findContent(@NotNull String tabName) {
    return null;
  }
}
