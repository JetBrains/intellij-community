// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configurations.coverage;

import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.ui.NestedGroupFragment;
import com.intellij.execution.ui.SettingsEditorFragment;
import com.intellij.java.coverage.JavaCoverageBundle;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class CoverageFragment<T extends RunConfigurationBase<?>> extends NestedGroupFragment<T> {

  private final RunConfigurationBase<?> myConfiguration;

  public CoverageFragment(RunConfigurationBase<?> configuration) {
    super("coverage", JavaCoverageBundle.message("coverage.settings"), JavaCoverageBundle.message("coverage.settings.group"), p -> false);
    myConfiguration = configuration;
  }

  @Override
  protected List<SettingsEditorFragment<T, ?>> createChildren() {
    List<SettingsEditorFragment<T, ?>> fragments = new ArrayList<>();
    fragments.add(createFilterEditor("coverage.include", JavaCoverageBundle.message("record.coverage.filters.title"), true,
                                     JavaCoverageBundle.message("coverage.settings.include")));
    fragments.add(createFilterEditor("coverage.exclude", JavaCoverageBundle.message("exclude.coverage.filters.title"), false,
                                     JavaCoverageBundle.message("coverage.settings.exclude")));
    return fragments;
  }

  @NotNull
  private SettingsEditorFragment<T, CoverageClassFilterEditor> createFilterEditor(String id,
                                                                                  @NotNull String message,
                                                                                  boolean included, @NotNull String optionName) {
    JavaCoverageEnabledConfiguration configuration = (JavaCoverageEnabledConfiguration)CoverageEnabledConfiguration.getOrCreate(myConfiguration);
    CoverageClassFilterEditor includedEditor = new CoverageClassFilterEditor(myConfiguration.getProject());
    includedEditor.setBorder(IdeBorderFactory.createTitledBorder(message, false, JBUI.emptyInsets()));
    return new SettingsEditorFragment<>(id, optionName, null, includedEditor,
                                        (p, editor) -> editor.setFilters(CoverageConfigurable.getCoveragePatterns(configuration, included)),
                                        (p, editor) -> setCoveragePatterns(configuration, editor.getFilters(), included),
                                        p -> false);
  }

  private static void setCoveragePatterns(JavaCoverageEnabledConfiguration configuration, ClassFilter[] filters, boolean included) {
    ClassFilter[] patterns = CoverageConfigurable.getCoveragePatterns(configuration, !included);
    configuration.setCoveragePatterns(ArrayUtil.mergeArrays(filters, patterns));
  }
}
