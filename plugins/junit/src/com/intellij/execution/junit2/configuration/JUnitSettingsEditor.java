// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.junit2.configuration;

import com.intellij.execution.application.JavaSettingsEditorBase;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.ui.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;

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
  }
}
