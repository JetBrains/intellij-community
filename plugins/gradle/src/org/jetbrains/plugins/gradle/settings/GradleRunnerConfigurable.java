/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.settings;

import com.google.common.base.Objects;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleBundle;

import javax.swing.*;

/**
 * @author Vladislav.Soroka
 * @since 11/2/2015
 */
public class GradleRunnerConfigurable extends BaseConfigurable implements SearchableConfigurable {

  private JPanel myMainPanel;
  private ComboBox myPreferredTestRunner;
  private static final TestRunnerItem[] TEST_RUNNER_ITEMS = new TestRunnerItem[]{
    new TestRunnerItem(GradleSystemRunningSettings.PreferredTestRunner.PLATFORM_TEST_RUNNER),
    new TestRunnerItem(GradleSystemRunningSettings.PreferredTestRunner.GRADLE_TEST_RUNNER),
    new TestRunnerItem(GradleSystemRunningSettings.PreferredTestRunner.CHOOSE_PER_TEST)};


  private final Project myProject;

  public GradleRunnerConfigurable(Project project) {
    myProject = project;
  }

  @Nls
  @Override
  public String getDisplayName() {
    return GradleBundle.message("gradle.runner");
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return "reference.settings.project.gradle.running";
  }

  @Override
  public void apply() throws ConfigurationException {
    GradleSystemRunningSettings.getInstance().setPreferredTestRunner(
      ((TestRunnerItem)myPreferredTestRunner.getSelectedItem()).value);
  }

  @Override
  public void reset() {
    GradleSystemRunningSettings settings = GradleSystemRunningSettings.getInstance();
    final TestRunnerItem item = getItem(settings.getPreferredTestRunner());
    myPreferredTestRunner.setSelectedItem(item);
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    return myMainPanel;
  }

  @Override
  public boolean isModified() {
    GradleSystemRunningSettings uiSettings = new GradleSystemRunningSettings();
    final TestRunnerItem selectedItem = (TestRunnerItem)myPreferredTestRunner.getSelectedItem();
    GradleSystemRunningSettings.PreferredTestRunner preferredTestRunner =
      selectedItem == null ? GradleSystemRunningSettings.PreferredTestRunner.CHOOSE_PER_TEST : selectedItem.value;
    uiSettings.setPreferredTestRunner(preferredTestRunner);
    GradleSystemRunningSettings settings = GradleSystemRunningSettings.getInstance();
    return !settings.equals(uiSettings);
  }

  @Override
  public void disposeUIResources() {
  }

  @NotNull
  @Override
  public String getId() {
    return "reference.settings.project.gradle.running";
  }

  @Nullable
  @Override
  public Runnable enableSearch(String option) {
    return null;
  }

  private void createUIComponents() {
    myPreferredTestRunner = new ComboBox(getItems());
  }

  private static TestRunnerItem getItem(GradleSystemRunningSettings.PreferredTestRunner preferredTestRunner) {
    for (TestRunnerItem item : getItems()) {
      if (item.value == preferredTestRunner) return item;
    }
    return null;
  }

  private static TestRunnerItem[] getItems() {
    return TEST_RUNNER_ITEMS;
  }

  static class TestRunnerItem {
    public TestRunnerItem(GradleSystemRunningSettings.PreferredTestRunner value) {
      this.value = value;
    }

    GradleSystemRunningSettings.PreferredTestRunner value;

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof TestRunnerItem)) return false;
      TestRunnerItem item = (TestRunnerItem)o;
      return value == item.value;
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(value);
    }

    @Override
    public String toString() {
      return GradleBundle.message("gradle.preferred_test_runner." + (value == null ? "CHOOSE_PER_TEST" : value.name()));
    }
  }
}
