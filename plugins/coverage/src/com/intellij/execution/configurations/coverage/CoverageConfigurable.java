// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.execution.configurations.coverage;

import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.java.coverage.JavaCoverageBundle;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Arrays;

/**
 * Base {@link com.intellij.openapi.options.Configurable} for configuring code coverage
 * To obtain a full configurable use
 * <code>
 * SettingsEditorGroup<YourConfiguration> group = new SettingsEditorGroup<YourConfiguration>();
 * group.addEditor(title, yourConfigurable);
 * group.addEditor(title, yourCoverageConfigurable);
 * </code>
 */
public final class CoverageConfigurable extends SettingsEditor<RunConfigurationBase<?>> {
  private final Project myProject;
  private CoverageClassFilterEditor myClassFilterEditor;
  private CoverageClassFilterEditor myExcludeClassFilterEditor;

  public CoverageConfigurable(RunConfigurationBase<?> config) {
    myProject = config.getProject();
  }

  @Override
  protected void resetEditorFrom(@NotNull final RunConfigurationBase<?> runConfiguration) {
    var configuration = (JavaCoverageEnabledConfiguration)CoverageEnabledConfiguration.getOrCreate(runConfiguration);
    myClassFilterEditor.setFilters(getCoveragePatterns(configuration, true));
    myExcludeClassFilterEditor.setFilters(getCoveragePatterns(configuration, false));
  }

  @Override
  protected void applyEditorTo(@NotNull final RunConfigurationBase runConfiguration) {
    var configuration = (JavaCoverageEnabledConfiguration)CoverageEnabledConfiguration.getOrCreate(runConfiguration);
    ClassFilter[] newCoveragePatterns = ArrayUtil.mergeArrays(myClassFilterEditor.getFilters(), myExcludeClassFilterEditor.getFilters());
    ClassFilter[] oldCoveragePatterns = ObjectUtils.chooseNotNull(configuration.getCoveragePatterns(), ClassFilter.EMPTY_ARRAY);
    //apply new order if something else was changed as well
    if (newCoveragePatterns.length != oldCoveragePatterns.length ||
        !ContainerUtil.newHashSet(newCoveragePatterns).equals(ContainerUtil.newHashSet(oldCoveragePatterns))) {
      configuration.setCoveragePatterns(newCoveragePatterns);
    }
  }

  @Override
  @NotNull
  protected JComponent createEditor() {
    JPanel result = new JPanel(new VerticalLayout(UIUtil.DEFAULT_VGAP));

    //noinspection DialogTitleCapitalization
    result.add(new TitledSeparator(JavaCoverageBundle.message("record.coverage.filters.title")));
    myClassFilterEditor = new CoverageClassFilterEditor(myProject);
    result.add(myClassFilterEditor);

    //noinspection DialogTitleCapitalization
    result.add(new TitledSeparator(JavaCoverageBundle.message("exclude.coverage.filters.title")));
    myExcludeClassFilterEditor = new CoverageClassFilterEditor(myProject) {
      @NotNull
      @Override
      protected ClassFilter createFilter(String pattern) {
        ClassFilter filter = super.createFilter(pattern);
        filter.setInclude(false);
        return filter;
      }
    };
    result.add(myExcludeClassFilterEditor);
    return result;
  }

  static ClassFilter @NotNull [] getCoveragePatterns(@NotNull JavaCoverageEnabledConfiguration configuration, boolean include) {
    return Arrays.stream(ObjectUtils.chooseNotNull(configuration.getCoveragePatterns(), ClassFilter.EMPTY_ARRAY))
      .filter(classFilter -> classFilter.INCLUDE == include).toArray(ClassFilter[]::new);
  }
}
