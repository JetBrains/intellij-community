// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.api;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.CollectionUtil;

/**
* @author Max Medvedev
*/
public final class SpreadState {
  public static final Key<SpreadState> SPREAD_STATE = Key.create("Spread state");

  private final @Nullable PsiType containerType;
  private final @Nullable SpreadState innerState;

  private SpreadState(@Nullable PsiType type, @Nullable SpreadState state) {
    containerType = type;
    innerState = state;
  }

  public @Nullable PsiType getContainerType() {
    return containerType;
  }

  public @Nullable SpreadState getInnerState() {
    return innerState;
  }

  public static SpreadState create(PsiType type, @Nullable SpreadState state) {
    return new SpreadState(type, state);
  }

  public static @Nullable PsiType apply(@Nullable PsiType item, @Nullable SpreadState state, Project project) {
    if (state == null) return item;
    return apply(CollectionUtil.createSimilarCollection(state.getContainerType(), project, item), state.getInnerState(), project);
  }
}
