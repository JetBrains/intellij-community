// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.runner;

import com.intellij.execution.Executor;
import com.intellij.execution.Location;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.testframework.JavaAwareTestConsoleProperties;
import com.intellij.execution.testframework.JavaSMTRunnerTestTreeView;
import com.intellij.execution.testframework.JavaTestLocator;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerTestTreeView;
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerTestTreeViewProvider;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.pom.Navigatable;
import com.intellij.util.config.BooleanProperty;
import com.intellij.util.config.DumbAwareToggleBooleanProperty;
import com.intellij.util.config.ToggleBooleanProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleBundle;

import javax.swing.*;
import javax.swing.tree.TreeSelectionModel;
import java.io.File;

import static com.intellij.execution.testframework.JavaAwareTestConsoleProperties.USE_WALL_TIME;

/**
 * @author Vladislav.Soroka
 */
public class GradleConsoleProperties extends SMTRunnerConsoleProperties implements SMTRunnerTestTreeViewProvider {
  public static final BooleanProperty SHOW_INTERNAL_TEST_NODES = new BooleanProperty("showInternalTestNodes", false);
  public static final SMTestLocator GRADLE_TEST_LOCATOR = JavaTestLocator.INSTANCE;

  private @Nullable File gradleTestReport;

  public GradleConsoleProperties(final ExternalSystemRunConfiguration configuration, Executor executor) {
    this(configuration, configuration.getSettings().getExternalSystemId().getReadableName(), executor);
  }

  public GradleConsoleProperties(@NotNull RunConfiguration config, @NotNull String testFrameworkName, @NotNull Executor executor) {
    super(config, testFrameworkName, executor);
  }

  public void setGradleTestReport(@Nullable File gradleTestReport) {
    this.gradleTestReport = gradleTestReport;
  }

  public @Nullable File getGradleTestReport() {
    return gradleTestReport;
  }

  @Override
  public int getSelectionMode() {
    return TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION;
  }

  @Override
  public void appendAdditionalActions(DefaultActionGroup actionGroup, JComponent parent, TestConsoleProperties target) {
    super.appendAdditionalActions(actionGroup, parent, target);
    if (Registry.is("java.test.enable.tree.live.time")) {
      actionGroup.addSeparator();
      DumbAwareToggleBooleanProperty property =
        new DumbAwareToggleBooleanProperty(JavaBundle.message("java.test.use.wall.time"), null, null, target, USE_WALL_TIME);
      actionGroup.add(property);
    }
    actionGroup.add(Separator.getInstance());
    actionGroup.add(createShowInternalNodesAction(target));
  }

  @Override
  public @Nullable Navigatable getErrorNavigatable(@NotNull Location<?> location, @NotNull String stacktrace) {
    return JavaAwareTestConsoleProperties.getStackTraceErrorNavigatable(location, stacktrace);
  }

  @Override
  public @Nullable SMTestLocator getTestLocator() {
    return GRADLE_TEST_LOCATOR;
  }

  @Override
  public boolean isEditable() {
    return true;
  }

  private @NotNull ToggleBooleanProperty createShowInternalNodesAction(TestConsoleProperties target) {
    String text = GradleBundle.message("gradle.test.show.internal.nodes.action.name");
    setIfUndefined(SHOW_INTERNAL_TEST_NODES, false);
    String desc = GradleBundle.message("gradle.test.show.internal.nodes.action.text");
    return new DumbAwareToggleBooleanProperty(text, desc, null, target, SHOW_INTERNAL_TEST_NODES);
  }

  @Override
  public @NotNull SMTRunnerTestTreeView createSMTRunnerTestTreeView() {
    return Registry.is("java.test.enable.tree.live.time") ? new JavaSMTRunnerTestTreeView(this) : new SMTRunnerTestTreeView();
  }
}
