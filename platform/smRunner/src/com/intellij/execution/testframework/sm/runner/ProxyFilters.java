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
package com.intellij.execution.testframework.sm.runner;

import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.Filter;
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo;

/**
 * @author Roman Chernyatchik
 */
public interface ProxyFilters {
  Filter FILTER_PASSED = new Filter() {
    @Override
    public boolean shouldAccept(final AbstractTestProxy test) {
      return ((SMTestProxy)test).getMagnitudeInfo() == TestStateInfo.Magnitude.PASSED_INDEX;
    }
  };
  Filter FILTER_ERRORS = new Filter() {
    @Override
    public boolean shouldAccept(final AbstractTestProxy test) {
      return ((SMTestProxy)test).getMagnitudeInfo() == TestStateInfo.Magnitude.ERROR_INDEX;
    }
  };
  Filter FILTER_FAILURES = new Filter() {
    @Override
    public boolean shouldAccept(final AbstractTestProxy test) {
      return ((SMTestProxy)test).getMagnitudeInfo() == TestStateInfo.Magnitude.FAILED_INDEX;
    }
  };

  Filter ERROR_LEAF = FILTER_ERRORS.and(Filter.LEAF);
  Filter FAILURE_LEAF = FILTER_FAILURES.and(Filter.LEAF);
}
