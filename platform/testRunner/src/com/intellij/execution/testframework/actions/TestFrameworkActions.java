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

/*
 * User: anna
 * Date: 25-May-2007
 */
package com.intellij.execution.testframework.actions;

import com.intellij.execution.testframework.Filter;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.TestFrameworkPropertyListener;
import com.intellij.execution.testframework.TestFrameworkRunningModel;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.config.AbstractProperty;

public class TestFrameworkActions {
  public static void installFilterAction(final TestFrameworkRunningModel model) {
    final TestConsoleProperties properties = model.getProperties();
    final TestFrameworkPropertyListener<Boolean> propertyListener = new TestFrameworkPropertyListener<Boolean>() {
        public void onChanged(final Boolean value) {
          model.setFilter(getFilter(properties));
        }
      };
    addPropertyListener(TestConsoleProperties.HIDE_PASSED_TESTS, propertyListener, model, true);
    addPropertyListener(TestConsoleProperties.HIDE_IGNORED_TEST, propertyListener, model, true);
    addPropertyListener(TestConsoleProperties.HIDE_SUCCESSFUL_CONFIG, propertyListener, model, true);
  }

  private static Filter getFilter(TestConsoleProperties properties) {
    final boolean shouldFilterPassed = TestConsoleProperties.HIDE_PASSED_TESTS.value(properties);
    final Filter hidePassedFilter = shouldFilterPassed ? Filter.NOT_PASSED.or(Filter.DEFECT) : Filter.NO_FILTER;

    final boolean shouldFilterIgnored = TestConsoleProperties.HIDE_IGNORED_TEST.value(properties);
    final Filter hideIgnoredFilter;
    if (shouldFilterIgnored) {
      final Filter ignoredFilter = Filter.IGNORED.not();
      hideIgnoredFilter = !shouldFilterPassed ? ignoredFilter.or(Filter.HAS_PASSED) : ignoredFilter;
    }
    else {
      hideIgnoredFilter = Filter.NO_FILTER;
    }

    final boolean hideSuccessfulConfigs = TestConsoleProperties.HIDE_SUCCESSFUL_CONFIG.value(properties);
    final Filter hideConfigsFilter = hideSuccessfulConfigs ? Filter.SUCCESSFUL_CONFIGS : Filter.NO_FILTER;

    return hidePassedFilter.and(hideIgnoredFilter).and(hideConfigsFilter);
  }

  public static void addPropertyListener(final AbstractProperty<Boolean> property,
                                         final TestFrameworkPropertyListener<Boolean> propertyListener,
                                         final TestFrameworkRunningModel model,
                                         final boolean sendValue) {
    final TestConsoleProperties properties = model.getProperties();
    if (sendValue) {
      properties.addListenerAndSendValue(property, propertyListener);
    }
    else {
      properties.addListener(property, propertyListener);
    }
    Disposer.register(model, new Disposable() {
      public void dispose() {
        properties.removeListener(property, propertyListener);
      }
    });
  }
}