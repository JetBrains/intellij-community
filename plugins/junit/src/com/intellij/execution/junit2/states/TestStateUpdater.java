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

package com.intellij.execution.junit2.states;

import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.Filter;
import com.intellij.rt.execution.junit.states.PoolOfTestStates;

public class TestStateUpdater {
  public static final Filter RUNNING = new Filter() {
    public boolean shouldAccept(final AbstractTestProxy test) {
      return test.getMagnitude() == PoolOfTestStates.RUNNING_INDEX;
    }
  };
  public static final Filter RUNNING_LEAF = RUNNING.and(Filter.LEAF);
}
