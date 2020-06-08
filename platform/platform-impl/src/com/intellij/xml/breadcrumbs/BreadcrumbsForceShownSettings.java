// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.breadcrumbs;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class BreadcrumbsForceShownSettings {
  private BreadcrumbsForceShownSettings() { }

  private static final Key<Boolean> FORCED_BREADCRUMBS = new Key<>("FORCED_BREADCRUMBS");

  public static boolean setForcedShown(@Nullable Boolean selected, @NotNull Editor editor) {
    Boolean old = getForcedShown(editor);
    editor.putUserData(FORCED_BREADCRUMBS, selected);
    return !Objects.equals(old, selected);
  }

  @Nullable
  public static Boolean getForcedShown(@NotNull Editor editor) {
    return editor.getUserData(FORCED_BREADCRUMBS);
  }
}
