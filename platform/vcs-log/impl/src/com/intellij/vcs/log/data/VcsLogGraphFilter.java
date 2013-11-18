package com.intellij.vcs.log.data;

import com.intellij.vcs.log.VcsLogFilter;

/**
 * Filter that is able to work on graph.
 */
public interface VcsLogGraphFilter extends VcsLogFilter {

  boolean matches(int hash);

}
