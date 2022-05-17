// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij;

import junit.framework.Test;
import org.jetbrains.annotations.Nullable;
import org.junit.runner.Description;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.manipulation.Filterable;
import org.junit.runner.manipulation.NoTestsRemainException;

public class TestMethodsFilter {

  public static void tryToFilterTests(@Nullable Test test) {
    if (!(test instanceof Filterable)) return;

    try {
      ((Filterable)test).filter(new MyFilter());
    }
    catch (NoTestsRemainException e) {
      // do nothing, in the future -- filter classes
    }
  }

  static class MyFilter extends Filter {

    @Override
    public boolean shouldRun(Description description) {
      if (!description.isTest()) return true;

      String methodName = description.getMethodName();
      if (methodName.contains("testFunResultViaCallableRefWithDirectCall")) return true;
      if (methodName.contains("testOverridingProtected")) return true;

      return false;
    }

    @Override
    public String describe() {
      return "My Super Filter";
    }
  }

}
