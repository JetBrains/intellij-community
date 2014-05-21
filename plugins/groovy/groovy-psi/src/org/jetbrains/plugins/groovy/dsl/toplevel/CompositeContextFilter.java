/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.dsl.toplevel;

import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
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
  public static ContextFilter compose(@NotNull List<ContextFilter> filters, boolean and) {
    if (filters.size() == 1) {
      return filters.get(0);
    }
    return new CompositeContextFilter(filters, and);
  }
}
