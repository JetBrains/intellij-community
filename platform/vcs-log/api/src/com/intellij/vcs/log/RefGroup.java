// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;

/**
 * Groups {@link VcsRef references} to show them accordingly in the UI, for example on the branches panel.
 * Grouping decision is made by the concrete {@link VcsLogRefManager}.
 */
public interface RefGroup {

  /**
   * If a group is not-expanded, its references won't be displayed until
   * Otherwise, if a group is expanded, its references will be displayed immediately,
   * but they might be somehow visually united to indicated that they are from similar structure.
   */
  boolean isExpanded();

  /**
   * Returns the name of the reference group. This reference will be displayed on the branches panel.
   */
  @NotNull
  @Nls
  String getName();

  /**
   * Returns references inside this group.
   */
  @NotNull
  List<VcsRef> getRefs();

  /**
   * Returns the colors of this ref group, which will be used to paint it in the table.
   */
  @NotNull
  List<Color> getColors();
}
