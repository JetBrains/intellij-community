package com.intellij.vcs.log.ui.filter;

import com.intellij.vcs.log.VcsLogFilter;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;

/**
 * Graphical UI for filtering commits in the log.
 *
 * @author Kirill Likhodedov
 */
public interface VcsLogFilterUi {

  /**
   * Returns the component which will be added to the Log toolbar.
   */
  @NotNull
  JComponent getRootComponent();

  /**
   * Returns the filters currently active, i.e. switched on by user.
   */
  @NotNull
  Collection<VcsLogFilter> getFilters();

}
