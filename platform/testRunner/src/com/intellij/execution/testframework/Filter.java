/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.execution.testframework;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class Filter {
  /**
   * All instances (and subclasses's instances) should be singletons.
   *
   * @see com.intellij.execution.junit2.TestProxy#selectChildren
   */
  protected Filter() {
  }

  public abstract boolean shouldAccept(AbstractTestProxy test);

  public List<AbstractTestProxy> select(final List<? extends AbstractTestProxy> tests) {
    final ArrayList<AbstractTestProxy> result = new ArrayList<AbstractTestProxy>();
    for (final AbstractTestProxy test : tests) {
      if (shouldAccept(test)) result.add(test);
    }
    return result;
  }

  public AbstractTestProxy detectIn(final Collection<? extends AbstractTestProxy> collection) {
    for (final AbstractTestProxy test : collection) {
      if (shouldAccept(test)) return test;
    }
    return null;
  }

  public Filter not() {
    return new NotFilter(this);
  }

  public Filter and(final Filter filter) {
    return new AndFilter(this, filter);
  }

  public Filter or(final Filter filter) {
    return new OrFilter(this, filter);
  }

  public static final Filter NO_FILTER = new Filter() {
    public boolean shouldAccept(final AbstractTestProxy test) {
      return true;
    }
  };

  public static final Filter DEFECT = new Filter() {
    public boolean shouldAccept(final AbstractTestProxy test) {
      return test.isDefect();
    }
  };

  public static final Filter NOT_PASSED = new Filter() {
    public boolean shouldAccept(final AbstractTestProxy test) {
      return !test.isPassed();
    }
  };

  public static final Filter FAILED_OR_INTERRUPTED = new Filter() {
    public boolean shouldAccept(final AbstractTestProxy test) {
      return test.isInterrupted() || test.isDefect();
    }
  };

  public static final Filter LEAF = new Filter() {
    public boolean shouldAccept(final AbstractTestProxy test) {
      return test.isLeaf();
    }
  };

  public static final Filter DEFECTIVE_LEAF = DEFECT.and(new Filter() {
    @Override
    public boolean shouldAccept(AbstractTestProxy test) {
      if (test.isLeaf()) return true;
      for (AbstractTestProxy testProxy : test.getChildren()) {
        if (testProxy.isDefect()) return false;
      }
      return true;
    }
  });

  private static class AndFilter extends Filter {
    private final Filter myFilter1;
    private final Filter myFilter2;

    public AndFilter(final Filter filter1, final Filter filter2) {
      myFilter1 = filter1;
      myFilter2 = filter2;
    }

    public boolean shouldAccept(final AbstractTestProxy test) {
      return myFilter1.shouldAccept(test) && myFilter2.shouldAccept(test);
    }
  }

  private static class NotFilter extends Filter {
    private final Filter myFilter;

    public NotFilter(final Filter filter) {
      myFilter = filter;
    }

    public boolean shouldAccept(final AbstractTestProxy test) {
      return !myFilter.shouldAccept(test);
    }
  }

  private static class OrFilter extends Filter {
    private final Filter myFilter1;
    private final Filter myFilter2;

    public OrFilter(final Filter filter1, final Filter filter2) {
      myFilter1 = filter1;
      myFilter2 = filter2;
    }

    public boolean shouldAccept(final AbstractTestProxy test) {
      return myFilter1.shouldAccept(test) || myFilter2.shouldAccept(test);
    }
  }

}
