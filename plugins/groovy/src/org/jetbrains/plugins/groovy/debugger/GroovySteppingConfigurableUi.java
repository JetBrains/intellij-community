package org.jetbrains.plugins.groovy.debugger;

import com.intellij.openapi.options.ConfigurableUi;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

class GroovySteppingConfigurableUi implements ConfigurableUi<GroovyDebuggerSettings> {
  private JCheckBox ignoreGroovyMethods;
  private JPanel rootPanel;

  @Override
  public void reset(@NotNull GroovyDebuggerSettings settings) {
    Boolean flag = settings.DEBUG_DISABLE_SPECIFIC_GROOVY_METHODS;
    ignoreGroovyMethods.setSelected(flag == null || flag.booleanValue());
  }

  @Override
  public boolean isModified(@NotNull GroovyDebuggerSettings settings) {
    return settings.DEBUG_DISABLE_SPECIFIC_GROOVY_METHODS.booleanValue() != ignoreGroovyMethods.isSelected();
  }

  @Override
  public void apply(@NotNull GroovyDebuggerSettings settings) {
    settings.DEBUG_DISABLE_SPECIFIC_GROOVY_METHODS = ignoreGroovyMethods.isSelected();
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return rootPanel;
  }
}