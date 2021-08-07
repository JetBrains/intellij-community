// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components;

import org.jetbrains.annotations.Nullable;

public class ComponentCategorizer {
  private ComponentCategorizer() {
  }

  @Nullable
  public static ComponentCategory getCategory(PersistentStateComponent<?> component) {
    State state = component.getClass().getAnnotation(State.class);
    return state != null ? state.category() : ComponentCategory.OTHER;
  }
}
