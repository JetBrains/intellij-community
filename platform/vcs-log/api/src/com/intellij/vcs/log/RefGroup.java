package com.intellij.vcs.log;

import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;

/**
 * Lets group {@link VcsRef references} to show them accordingly in the UI, for example on the branches panel.
 * Grouping decision is made by the concrete {@link VcsLogRefManager}.
 */
public interface RefGroup {

  /**
   * If a group is not-expanded, its references won't be displayed until
   * Otherwise, if a group is expanded, its references will be displayed immediately,
   * but they may possibly be somehow visually united to indicated that they are from similar structure.
   */
  boolean isExpanded();

  /**
   * Returns the name of the reference group. This reference will be displayed on the branches panel.
   */
  @NotNull
  String getName();

  /**
   * Returns references inside this group.
   */
  @NotNull
  List<VcsRef> getRefs();

  /**
   * Returns the background color of this ref group, which will be used to paint it on the Branches panel.
   */
  @NotNull
  Color getBgColor();

}
