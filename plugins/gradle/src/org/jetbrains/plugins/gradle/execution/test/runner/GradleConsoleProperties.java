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
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.util.config.BooleanProperty;
import com.intellij.util.config.DumbAwareToggleBooleanProperty;
import com.intellij.util.config.ToggleBooleanProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleBundle;

import javax.swing.*;
import javax.swing.tree.TreeSelectionModel;
import java.io.File;

/**
 * @author Vladislav.Soroka
 * @since 2/17/14
 */
public class GradleConsoleProperties extends SMTRunnerConsoleProperties {
  public static final BooleanProperty SHOW_INTERNAL_TEST_NODES = new BooleanProperty("showInternalTestNodes", false);
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
  public int getSelectionMode() {
    return TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION;
  }

  @NotNull
  @Override
  public String getWindowId() {
    return ToolWindowId.BUILD;
  }

  @Override
  public void appendAdditionalActions(DefaultActionGroup actionGroup, JComponent parent, TestConsoleProperties target) {
    super.appendAdditionalActions(actionGroup, parent, target);
    actionGroup.add(Separator.getInstance());
    actionGroup.add(createShowInternalNodesAction(target));
  }

  @NotNull
  private ToggleBooleanProperty createShowInternalNodesAction(TestConsoleProperties target) {
    String text = GradleBundle.message("gradle.test.show.internal.nodes.action.name");
    setIfUndefined(SHOW_INTERNAL_TEST_NODES, false);
    String desc = GradleBundle.message("gradle.test.show.internal.nodes.action.text");
    return new DumbAwareToggleBooleanProperty(text, desc, null, target, SHOW_INTERNAL_TEST_NODES);
  }
}
