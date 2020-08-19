// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.junit2.configuration;

import com.intellij.execution.JUnitBundle;
import com.intellij.execution.application.JavaSettingsEditorBase;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.ui.*;
import com.intellij.rt.execution.junit.RepeatCount;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Supplier;

import static com.intellij.execution.junit.JUnitConfiguration.FORK_NONE;

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

    ConfigurationModuleSelector moduleSelector = new ConfigurationModuleSelector(getProject(), classpathCombo);
    JUnitTestKindFragment testKind = new JUnitTestKindFragment(getProject(), moduleSelector);
    fragments.add(testKind);

    String group = JUnitBundle.message("test.group");
    VariantTagFragment<JUnitConfiguration, String> repeat =
      VariantTagFragment.createFragment("repeat", JUnitBundle.message("repeat.name"), group,
                                        () -> RepeatCount.REPEAT_TYPES,
                                        configuration -> configuration.getRepeatMode(),
                                        (configuration, mode) -> configuration.setRepeatMode(mode),
                                        configuration -> !RepeatCount.ONCE.equals(configuration.getRepeatMode()));
    fragments.add(repeat);

    Supplier<String[]> variantsProvider = () -> JUnitConfigurable.getForkModel(testKind.getTestKind(), repeat.getSelectedVariant());
    VariantTagFragment<JUnitConfiguration, String> forkMode =
      VariantTagFragment.createFragment("forkMode", JUnitBundle.message("fork.mode.name"), group, variantsProvider,
                                        configuration -> configuration.getForkMode(),
                                        (configuration, s) -> configuration.setForkMode(s),
                                        configuration -> !FORK_NONE.equals(configuration.getForkMode()));
    fragments.add(forkMode);

    testKind.addSettingsEditorListener(
      editor -> forkMode.setSelectedVariant(JUnitConfigurable.updateForkMethod(testKind.getTestKind(), forkMode.getSelectedVariant())));
  }
}
