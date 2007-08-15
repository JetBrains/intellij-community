package com.intellij.execution.junit2.ui.properties;

import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.util.config.Storage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class JUnitConsoleProperties extends TestConsoleProperties {
  @NonNls private static final String GROUP_NAME = "JUnitSupport.";

  private final JUnitConfiguration myConfiguration;

  public JUnitConsoleProperties(@NotNull JUnitConfiguration configuration) {
    this(configuration, new Storage.PropertiesComponentStorage(GROUP_NAME, PropertiesComponent.getInstance()));
  }

  public JUnitConsoleProperties(@NotNull JUnitConfiguration configuration, final Storage storage) {
    super(storage, configuration.getProject());
    myConfiguration = configuration;
  }

  public JUnitConfiguration getConfiguration() { return myConfiguration; }

}
