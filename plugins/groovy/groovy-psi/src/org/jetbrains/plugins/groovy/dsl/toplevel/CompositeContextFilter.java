// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.dsl.toplevel;

import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.dsl.GroovyClassDescriptor;

import java.util.List;

/**
 * @author peter
 */
public final class CompositeContextFilter implements ContextFilter {
  private final List<? extends ContextFilter> myFilters;
  private final boolean myAnd;

  private CompositeContextFilter(List<? extends ContextFilter> filters, boolean and) {
    myFilters = filters;
    myAnd = and;
  }

  @Override
  public boolean isApplicable(GroovyClassDescriptor descriptor, ProcessingContext ctx) {
    for (ContextFilter filter : myFilters) {
      if (myAnd != filter.isApplicable(descriptor, ctx)) {
        return !myAnd;
      }
    }
    return myAnd;
  }

  @NotNull
  public static ContextFilter compose(@NotNull List<? extends ContextFilter> filters, boolean and) {
    if (filters.size() == 1) {
      return filters.get(0);
    }
    return new CompositeContextFilter(filters, and);
  }
}
