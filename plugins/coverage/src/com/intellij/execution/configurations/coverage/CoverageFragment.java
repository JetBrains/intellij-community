// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.configurations.coverage;

import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.ui.NestedGroupFragment;
import com.intellij.execution.ui.SettingsEditorFragment;
import com.intellij.java.coverage.JavaCoverageBundle;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.JBInsets;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class CoverageFragment<T extends RunConfigurationBase<?>> extends NestedGroupFragment<T> {

  private final RunConfigurationBase<?> myConfiguration;

  public CoverageFragment(RunConfigurationBase<?> configuration) {
    super("coverage", JavaCoverageBundle.message("coverage.settings"), JavaCoverageBundle.message("coverage.settings.group"), p -> false);
    myConfiguration = configuration;
  }

  @Override
  public String getChildrenGroupName() {
    return JavaCoverageBundle.message("coverage.settings.menu");
  }

  @Override
  protected List<SettingsEditorFragment<T, ?>> createChildren() {
    List<SettingsEditorFragment<T, ?>> fragments = new ArrayList<>();

    SettingsEditorFragment<T, CoverageClassFilterEditor> include =
      createFilterEditor("coverage.include", JavaCoverageBundle.message("record.coverage.filters.title"), true,
                         JavaCoverageBundle.message("coverage.settings.include"));
    include.setActionHint(JavaCoverageBundle.message("show.coverage.data.only.in.specified.classes.and.packages"));
    fragments.add(include);

    SettingsEditorFragment<T, CoverageClassFilterEditor> exclude =
      createFilterEditor("coverage.exclude", JavaCoverageBundle.message("exclude.coverage.filters.title"), false,
                         JavaCoverageBundle.message("coverage.settings.exclude"));
    exclude.setActionHint(JavaCoverageBundle.message("do.not.show.coverage.data.in.specified.classes.and.packages"));
    fragments.add(exclude);

    return fragments;
  }

  @NotNull
  private SettingsEditorFragment<T, CoverageClassFilterEditor> createFilterEditor(String id,
                                                                                  @NotNull @Nls String message,
                                                                                  boolean included, @NotNull @Nls String optionName) {
    CoverageClassFilterEditor filterEditor = new CoverageClassFilterEditor(myConfiguration.getProject());
    filterEditor.setBorder(IdeBorderFactory.createTitledBorder(message, false, JBInsets.emptyInsets()));
    filterEditor.setupEasyFocusTraversing();
    return new SettingsEditorFragment<>(id, optionName, null, filterEditor,
                                        (p, editor) -> editor.setFilters(CoverageConfigurable.getCoveragePatterns(getConfiguration(p), included)),
                                        (p, editor) -> setCoveragePatterns(getConfiguration(p), isSelected() && filterEditor.isVisible()
                                                                                                ? editor.getFilters()
                                                                                                : ClassFilter.EMPTY_ARRAY, included),
                                        p -> CoverageConfigurable.getCoveragePatterns(getConfiguration(p), included).length > 0);
  }

  @NotNull
  private static JavaCoverageEnabledConfiguration getConfiguration(RunConfigurationBase<?> configuration) {
    return (JavaCoverageEnabledConfiguration)CoverageEnabledConfiguration.getOrCreate(configuration);
  }

  private static void setCoveragePatterns(JavaCoverageEnabledConfiguration configuration, ClassFilter[] filters, boolean included) {
    ClassFilter[] patterns = CoverageConfigurable.getCoveragePatterns(configuration, !included);
    for (ClassFilter filter : filters) {
      filter.INCLUDE = included;
    }
    configuration.setCoveragePatterns(ArrayUtil.mergeArrays(filters, patterns));
  }
}
