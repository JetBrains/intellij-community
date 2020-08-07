// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.junit2.configuration;

import com.intellij.execution.JUnitBundle;
import com.intellij.execution.application.JavaSettingsEditorBase;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.ui.*;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

public class JUnitSettingsEditor extends JavaSettingsEditorBase<JUnitConfiguration> {

  public JUnitSettingsEditor(JUnitConfiguration runConfiguration) {
    super(runConfiguration);
  }

  @Override
  protected @NotNull SettingsEditorFragment<JUnitConfiguration, ModuleClasspathCombo> createClasspathCombo() {
    return CommonJavaFragments.moduleClasspath(null, null, null);
  }

  @Override
  protected void customizeFragments(List<SettingsEditorFragment<JUnitConfiguration, ?>> fragments,
                                    ModuleClasspathCombo classpathCombo,
                                    CommonParameterFragments<JUnitConfiguration> commonParameterFragments) {
    DefaultJreSelector jreSelector = DefaultJreSelector.fromModuleDependencies(classpathCombo, false);
    SettingsEditorFragment<JUnitConfiguration, JrePathEditor> jrePath = CommonJavaFragments.createJrePath(jreSelector);
    fragments.add(jrePath);
    fragments.add(createShortenClasspath(classpathCombo, jrePath, false));

    ConfigurationModuleSelector moduleSelector = new ConfigurationModuleSelector(myProject, classpathCombo);
    fragments.add(new JUnitTestKindFragment(myProject, moduleSelector));

    String group = JUnitBundle.message("test.group");
    Supplier<List<String>> variantsProvider = () -> Arrays.asList(JUnitConfiguration.FORK_NONE, JUnitConfiguration.FORK_KLASS);
    VariantTagFragment<JUnitConfiguration, String> forkMode =
      VariantTagFragment.createFragment("forkMode", JUnitBundle.message("fork.mode.name"), group,
                                        variantsProvider,
                                        configuration -> configuration.getForkMode(),
                                        (configuration, s) -> configuration.setForkMode(s),
                                        configuration -> !JUnitConfiguration.FORK_NONE.equals(configuration.getForkMode()));
    fragments.add(forkMode);
  }
}
