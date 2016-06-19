/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.execution.junit.testDiscovery;

import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.util.Pair;

public class TestBySource extends JUnitTestDiscoveryRunnableState {
  public TestBySource(JUnitConfiguration configuration,
                      ExecutionEnvironment environment) {
    super(configuration, environment);
  }

  @Override
  protected String getChangeList() {
    return null;
  }

  @Override
  protected Pair<String, String> getPosition() {
    final JUnitConfiguration.Data data = getConfiguration().getPersistentData();
    return Pair.create(data.getMainClassName(), data.getMethodName());
  }
}
