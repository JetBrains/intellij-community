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
package org.jetbrains.plugins.gradle.execution.test.runner;

import com.intellij.execution.Executor;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreeSelectionModel;
import java.io.File;

/**
 * @author Vladislav.Soroka
 * @since 2/17/14
 */
public class GradleConsoleProperties extends SMTRunnerConsoleProperties {
  @Nullable private File gradleTestReport;

  public GradleConsoleProperties(final ExternalSystemRunConfiguration configuration, Executor executor) {
    super(configuration, configuration.getSettings().getExternalSystemId().getReadableName(), executor);
  }

  public void setGradleTestReport(@Nullable File gradleTestReport) {
    this.gradleTestReport = gradleTestReport;
  }

  @Nullable
  public File getGradleTestReport() {
    return gradleTestReport;
  }

  @Override
  protected int getSelectionMode() {
    return TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION;
  }
}
