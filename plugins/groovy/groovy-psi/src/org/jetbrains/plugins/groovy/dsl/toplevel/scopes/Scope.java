package org.jetbrains.plugins.groovy.dsl.toplevel.scopes;

import org.jetbrains.plugins.groovy.dsl.toplevel.ContextFilter;

import java.util.List;
import java.util.Map;

@SuppressWarnings("rawtypes")
public abstract class Scope {
  public abstract List<ContextFilter> createFilters(Map args);
}
