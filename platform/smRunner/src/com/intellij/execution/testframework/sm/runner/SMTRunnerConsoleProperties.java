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

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RuntimeConfiguration;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.util.config.Storage;

/**
 * @author: Roman Chernyatchik
 */
public class SMTRunnerConsoleProperties extends TestConsoleProperties {
  private final RuntimeConfiguration myConfiguration;

  /**
   * @param config
   * @param testFrameworkName Prefix for storage which keeps runner settings. E.g. "RubyTestUnit"
   * @param executor
   */
  public SMTRunnerConsoleProperties(final RuntimeConfiguration config, 
                                    final String testFrameworkName,
                                    Executor executor)
  {
    super(new Storage.PropertiesComponentStorage(testFrameworkName + "Support.", PropertiesComponent.getInstance()), config.getProject(),
          executor);
    myConfiguration = config;
  }

  public RuntimeConfiguration getConfiguration() {
    return myConfiguration;
  }
}
