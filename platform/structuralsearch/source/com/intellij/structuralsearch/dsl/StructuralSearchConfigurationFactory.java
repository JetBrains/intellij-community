// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.dsl;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class StructuralSearchConfigurationFactory extends ConfigurationFactory {

  public StructuralSearchConfigurationFactory(StructuralSearchRunConfigurationType type) {
    super(type);
  }

  @NotNull
  @Override
  public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
    return new StructuralSearchRunConfiguration(project, this, "Structural Search");
  }
}
