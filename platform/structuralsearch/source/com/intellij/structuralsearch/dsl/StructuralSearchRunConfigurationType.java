// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.dsl;

import com.intellij.execution.configuration.ConfigurationFactoryEx;
import com.intellij.execution.configurations.ConfigurationTypeBase;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.ui.LayeredIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Bas Leijdekkers
 */
public class StructuralSearchRunConfigurationType extends ConfigurationTypeBase {

  public static final Icon ICON = new LayeredIcon(AllIcons.Actions.Find, AllIcons.General.Run); // todo fix this

  @NotNull
  public static StructuralSearchRunConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(StructuralSearchRunConfigurationType.class);
  }

  public StructuralSearchRunConfigurationType() {
    super("SSR_DSL_RUN_CONFIGURATION", "Structural Search", "Structural Search", ICON);
    addFactory(new ConfigurationFactoryEx<StructuralSearchRunConfiguration>(this) {
      @NotNull
      @Override
      public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
        return new StructuralSearchRunConfiguration(project, this, "Structural Search");
      }
    });
  }
}
