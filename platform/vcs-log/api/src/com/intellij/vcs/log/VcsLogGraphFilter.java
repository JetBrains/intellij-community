package com.intellij.vcs.log;

/**
 * Filter that is able to work on graph.
 */
public interface VcsLogGraphFilter extends VcsLogFilter {

  boolean matches(int hash);

}
