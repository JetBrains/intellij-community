package com.intellij.vcs.log.data;

import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsLogFilter;
import org.jetbrains.annotations.NotNull;

/**
 * Filter that is able to work on graph.
 */
public interface VcsLogGraphFilter extends VcsLogFilter {

  boolean matches(@NotNull Hash hash);

}
