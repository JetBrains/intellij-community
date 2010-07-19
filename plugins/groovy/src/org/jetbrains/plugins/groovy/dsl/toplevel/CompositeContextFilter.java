package org.jetbrains.plugins.groovy.dsl.toplevel;

import com.intellij.util.ProcessingContext;
import org.jetbrains.plugins.groovy.dsl.GroovyClassDescriptor;

import java.util.List;

/**
 * @author peter
 */
public class CompositeContextFilter implements ContextFilter {
  private final List<ContextFilter> myFilters;
  private final boolean myAnd;

  private CompositeContextFilter(List<ContextFilter> filters, boolean and) {
    myFilters = filters;
    myAnd = and;
  }

  public boolean isApplicable(GroovyClassDescriptor descriptor, ProcessingContext ctx) {
    for (ContextFilter filter : myFilters) {
      if (myAnd != filter.isApplicable(descriptor, ctx)) {
        return !myAnd;
      }
    }
    return myAnd;
  }

  public static ContextFilter compose(List<ContextFilter> filters, boolean and) {
    if (filters.size() == 1) {
      return filters.get(0);
    }
    return new CompositeContextFilter(filters, and);
  }
}
