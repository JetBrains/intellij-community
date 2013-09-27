package com.intellij.vcs.log;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Lets group {@link VcsRef references} to show them accordingly in the UI, for example on the branches panel.
 * Grouping decision is made by the concrete {@link VcsLogRefManager}.
 *
 * @author Kirill Likhodedov
 */
public interface RefGroup {

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

}
