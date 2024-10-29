// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.breadcrumbs;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

@ApiStatus.Internal
public final class BreadcrumbsForceShownSettings {
  private BreadcrumbsForceShownSettings() { }

  private static final Key<Boolean> FORCED_BREADCRUMBS = new Key<>("FORCED_BREADCRUMBS");

  public static boolean setForcedShown(@Nullable Boolean selected, @NotNull Editor editor) {
    Boolean old = getForcedShown(editor);
    editor.putUserData(FORCED_BREADCRUMBS, selected);
    return !Objects.equals(old, selected);
  }

  public static @Nullable Boolean getForcedShown(@NotNull Editor editor) {
    return editor.getUserData(FORCED_BREADCRUMBS);
  }
}
