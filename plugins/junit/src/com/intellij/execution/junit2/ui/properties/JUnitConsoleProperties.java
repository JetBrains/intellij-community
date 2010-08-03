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

package com.intellij.execution.junit2.ui.properties;

import com.intellij.execution.Executor;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.testframework.JavaAwareTestConsoleProperties;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.config.Storage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class JUnitConsoleProperties extends JavaAwareTestConsoleProperties {
  @NonNls private static final String GROUP_NAME = "JUnitSupport.";

  private final JUnitConfiguration myConfiguration;

  public JUnitConsoleProperties(@NotNull JUnitConfiguration configuration, Executor executor) {
    this(configuration, new Storage.PropertiesComponentStorage(GROUP_NAME, PropertiesComponent.getInstance()), executor);
  }

  public JUnitConsoleProperties(@NotNull JUnitConfiguration configuration, final Storage storage, Executor executor) {
    super(storage, configuration.getProject(), executor);
    myConfiguration = configuration;
  }

  public JUnitConfiguration getConfiguration() { return myConfiguration; }

  @Override
  public GlobalSearchScope getScope() {
    return myConfiguration.getPersistentData().getScope().getSourceScope(myConfiguration).getLibrariesScope();
  }
}
