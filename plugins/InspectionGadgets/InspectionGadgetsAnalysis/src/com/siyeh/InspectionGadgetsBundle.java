// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public class InspectionGadgetsBundle extends DynamicBundle {
  @NonNls public static final String BUNDLE = "com.siyeh.InspectionGadgetsBundle";
  private static final InspectionGadgetsBundle INSTANCE = new InspectionGadgetsBundle();

  private InspectionGadgetsBundle() { super(BUNDLE); }

  @NotNull
  public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
    return INSTANCE.getMessage(key, params);
  }
}