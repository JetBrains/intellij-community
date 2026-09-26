// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.junit.JUnitParameterCollector.Parameter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.ArrayUtilRt;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public final class JUnitSingleParameter {
  private static final Logger LOG = Logger.getInstance(JUnitSingleParameter.class);

  private final @NotNull RunnerAndConfigurationSettings mySettings;
  private final @NotNull JUnitConfiguration myConfiguration;
  private final @NotNull Project myProject;

  public JUnitSingleParameter(@NotNull RunnerAndConfigurationSettings settings) {
    mySettings = settings;
    myConfiguration = (JUnitConfiguration)settings.getConfiguration();
    myProject = myConfiguration.getProject();
  }

  public void updateConfiguration(@NotNull Parameter parameter) {
    // the name is read first: the generated name of a uniqueId configuration is the raw uniqueId
    @NlsSafe String name = myConfiguration.getName() + "." + parameter.displayName();
    myConfiguration.beUniqueIdConfiguration(ArrayUtilRt.toStringArray(parameter.ids()));

    JUnitConfiguration configuration = null;
    String[] chosen = ArrayUtilRt.toStringArray(parameter.ids());
    for (RunnerAndConfigurationSettings settings : RunManager.getInstance(myProject)
      .getConfigurationSettingsList(myConfiguration.getType())) {
      if (settings != mySettings &&
          settings.getConfiguration() instanceof JUnitConfiguration jUnitConfiguration &&
          JUnitConfiguration.TEST_UNIQUE_ID.equals(jUnitConfiguration.getPersistentData().TEST_OBJECT) &&
          Arrays.equals(chosen, jUnitConfiguration.getPersistentData().getUniqueIds())) {
        configuration = jUnitConfiguration;
        break;
      }
    }
    if (configuration != null) {
      Element state = new Element("configuration");
      configuration.writeExternal(state);
      try {
        myConfiguration.readExternal(state);
      }
      catch (InvalidDataException e) {
        LOG.warn("Cannot run the parameter with the settings of '" + configuration.getName() + "'", e);
      }
    }
    myConfiguration.setName(name);
  }
}
