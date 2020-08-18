// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configurations.coverage;

import com.intellij.coverage.CoverageRunner;
import com.intellij.coverage.JavaCoverageEngine;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.ui.NestedGroupFragment;
import com.intellij.execution.ui.SettingsEditorFragment;
import com.intellij.java.coverage.JavaCoverageBundle;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class CoverageFragment<T extends RunConfigurationBase<?>> extends NestedGroupFragment<T> {

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
    fragments.add(createFilterEditor("coverage.include", JavaCoverageBundle.message("record.coverage.filters.title"), true,
                                     JavaCoverageBundle.message("coverage.settings.include")));
    fragments.add(createFilterEditor("coverage.exclude", JavaCoverageBundle.message("exclude.coverage.filters.title"), false,
                                     JavaCoverageBundle.message("coverage.settings.exclude")));

    JavaCoverageEnabledConfiguration configuration = getConfiguration();
    fragments.add(createRunnerFragment());
    fragments.add(SettingsEditorFragment.createTag("coverage.tracing", JavaCoverageBundle.message("coverage.settings.tracing"), null,
                                                   t -> !configuration.isSampling(),
                                                   (t, value) -> configuration.setSampling(!value)));
    fragments.add(SettingsEditorFragment.createTag("coverage.test.folders", JavaCoverageBundle.message("coverage.settings.test.folders"), null,
                                                   t -> configuration.isTrackTestFolders(),
                                                   (t, value) -> configuration.setTrackTestFolders(value)));
    return fragments;
  }

  @NotNull
  private SettingsEditorFragment<T, CoverageClassFilterEditor> createFilterEditor(String id,
                                                                                  @NotNull String message,
                                                                                  boolean included, @NotNull String optionName) {
    JavaCoverageEnabledConfiguration configuration = getConfiguration();
    CoverageClassFilterEditor filterEditor = new CoverageClassFilterEditor(myConfiguration.getProject());
    filterEditor.setBorder(IdeBorderFactory.createTitledBorder(message, false, JBUI.emptyInsets()));
    filterEditor.setupEasyFocusTraversing();
    return new SettingsEditorFragment<>(id, optionName, null, filterEditor,
                                        (p, editor) -> editor.setFilters(CoverageConfigurable.getCoveragePatterns(configuration, included)),
                                        (p, editor) -> setCoveragePatterns(configuration, isSelected() && filterEditor.isVisible() ? editor.getFilters() : ClassFilter.EMPTY_ARRAY, included),
                                        p -> false);
  }

  @NotNull
  private JavaCoverageEnabledConfiguration getConfiguration() {
    return (JavaCoverageEnabledConfiguration)CoverageEnabledConfiguration.getOrCreate(myConfiguration);
  }

  private static void setCoveragePatterns(JavaCoverageEnabledConfiguration configuration, ClassFilter[] filters, boolean included) {
    ClassFilter[] patterns = CoverageConfigurable.getCoveragePatterns(configuration, !included);
    configuration.setCoveragePatterns(ArrayUtil.mergeArrays(filters, patterns));
  }

  private SettingsEditorFragment<T, ?> createRunnerFragment() {
    final DefaultComboBoxModel<CoverageRunner> model = new DefaultComboBoxModel<>();
    ComboBox<CoverageRunner> comboBox = new ComboBox<>(model);

    final JavaCoverageEnabledConfiguration configuration = getConfiguration();
    final JavaCoverageEngine provider = JavaCoverageEngine.getInstance();
    for (CoverageRunner runner : CoverageRunner.EP_NAME.getExtensionList()) {
      if (runner.acceptsCoverageEngine(provider)) {
        model.addElement(runner);
      }
    }
    comboBox.setRenderer(SimpleListCellRenderer.create("", CoverageRunner::getPresentableName));
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(comboBox, BorderLayout.WEST);
    LabeledComponent<?> component = LabeledComponent.create(panel, JavaCoverageBundle.message("run.configuration.choose.coverage.runner"), BorderLayout.WEST);
    return new SettingsEditorFragment<>("coverage.runner", JavaCoverageBundle.message("coverage.settings.runner"), null, component,
                                        (t, c) -> comboBox.setItem(configuration.getCoverageRunner()),
                                        (t, c) -> configuration.setCoverageRunner(isSelected() && component.isVisible() ? comboBox.getItem() : model.getElementAt(0)),
                                        t -> false);
  }
}
