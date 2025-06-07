// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.util.treeView.smartTree;

import com.intellij.ui.PlaceHolder;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Maxim.Mossienko
 */
public final class TreeStructureUtil {
  @ApiStatus.Internal
  public static final String PLACE = "StructureViewPopup";

  private TreeStructureUtil() {}

  public static boolean isInStructureViewPopup(@NotNull PlaceHolder model) {
    return PLACE.equals(model.getPlace());
  }

  public static @NonNls String getPropertyName(String propertyName) {
    return propertyName + ".file.structure.state";
  }
}
