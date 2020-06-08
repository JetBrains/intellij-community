// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.util.treeView.smartTree;

import com.intellij.ui.PlaceHolder;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Maxim.Mossienko
 */
public final class TreeStructureUtil {
  public static final String PLACE = "StructureViewPopup";

  private TreeStructureUtil() {}

  public static boolean isInStructureViewPopup(@NotNull PlaceHolder<String> model) {
    return PLACE.equals(model.getPlace());
  }

  @NonNls
  public static String getPropertyName(String propertyName) {
    return propertyName + ".file.structure.state";
  }
}
