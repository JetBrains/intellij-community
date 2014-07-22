package org.jetbrains.plugins.groovy.debugger;

import com.intellij.openapi.options.ConfigurableBase;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;

class GroovySteppingConfigurable extends ConfigurableBase<GroovySteppingConfigurableUi, GroovyDebuggerSettings> {
  @Override
  protected GroovyDebuggerSettings getSettings() {
    return GroovyDebuggerSettings.getInstance();
  }

  @Override
  protected GroovySteppingConfigurableUi createUi() {
    return new GroovySteppingConfigurableUi();
  }

  @NotNull
  @Override
  public String getId() {
    return "debugger.stepping.groovy";
  }

  @Nls
  @Override
  public String getDisplayName() {
    return GroovyBundle.message("groovy.debug.caption");
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return "reference.idesettings.debugger.groovy";
  }
}