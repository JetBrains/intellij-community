// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.components.JBCheckBox;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleBundle;

import javax.swing.*;
import java.util.Objects;

/**
 * @author Vladislav.Soroka
 */
public class GradleRunnerConfigurable implements Configurable {
  private JPanel myMainPanel;
  private JBCheckBox myGradleAwareMakeCheckBox;
  private ComboBox myPreferredTestRunner;
  private static final TestRunnerItem[] TEST_RUNNER_ITEMS = new TestRunnerItem[]{
    new TestRunnerItem(GradleSystemRunningSettings.PreferredTestRunner.PLATFORM_TEST_RUNNER),
    new TestRunnerItem(GradleSystemRunningSettings.PreferredTestRunner.GRADLE_TEST_RUNNER),
    new TestRunnerItem(GradleSystemRunningSettings.PreferredTestRunner.CHOOSE_PER_TEST)};

  @Nls
  @Override
  public String getDisplayName() {
    return GradleBundle.message("gradle.runner");
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return "reference.settingsdialog.project.gradle";
  }

  @Override
  public void apply() throws ConfigurationException {
    boolean gradleMakeEnabled = myGradleAwareMakeCheckBox.isSelected();
    GradleSystemRunningSettings settings = GradleSystemRunningSettings.getInstance();
    settings.setDelegatedBuildEnabledByDefault(gradleMakeEnabled);
    GradleSystemRunningSettings.PreferredTestRunner preferredTestRunner = getSelectedRunner();
    settings.setDefaultTestRunner(preferredTestRunner);
  }

  @Override
  public void reset() {
    GradleSystemRunningSettings settings = GradleSystemRunningSettings.getInstance();
    final TestRunnerItem item = getItem(settings.getDefaultTestRunner());
    myPreferredTestRunner.setSelectedItem(item);
    boolean gradleMakeEnabled = settings.isDelegatedBuildEnabledByDefault();
    enableGradleMake(gradleMakeEnabled);
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    return myMainPanel;
  }

  @Override
  public boolean isModified() {
    GradleSystemRunningSettings uiSettings = new GradleSystemRunningSettings();
    GradleSystemRunningSettings.PreferredTestRunner preferredTestRunner = getSelectedRunner();
    uiSettings.setDefaultTestRunner(preferredTestRunner);
    uiSettings.setDelegatedBuildEnabledByDefault(myGradleAwareMakeCheckBox.isSelected());
    GradleSystemRunningSettings settings = GradleSystemRunningSettings.getInstance();
    return !settings.equals(uiSettings);
  }

  private void createUIComponents() {
    myGradleAwareMakeCheckBox = new JBCheckBox(GradleBundle.message("gradle.settings.text.use.gradle.aware.make"));
    myGradleAwareMakeCheckBox.addActionListener(e -> enableGradleMake(myGradleAwareMakeCheckBox.isSelected()));
    myPreferredTestRunner = new ComboBox<>(getItems());
  }

  private void enableGradleMake(boolean enable) {
    myGradleAwareMakeCheckBox.setSelected(enable);
  }

  private GradleSystemRunningSettings.PreferredTestRunner getSelectedRunner() {
    final TestRunnerItem selectedItem = (TestRunnerItem)myPreferredTestRunner.getSelectedItem();
    return selectedItem == null ? GradleSystemRunningSettings.PreferredTestRunner.CHOOSE_PER_TEST : selectedItem.value;
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
    TestRunnerItem(GradleSystemRunningSettings.PreferredTestRunner value) {
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
      return Objects.hash(value);
    }

    @Override
    public String toString() {
      return GradleBundle.message("gradle.preferred_test_runner." + (value == null ? "CHOOSE_PER_TEST" : value.name()));
    }
  }
}
