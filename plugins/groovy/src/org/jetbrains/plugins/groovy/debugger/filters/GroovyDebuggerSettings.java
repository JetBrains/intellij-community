package org.jetbrains.plugins.groovy.debugger.filters;

import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.options.Configurable;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.xdebugger.settings.XDebuggerSettings;
import org.jetbrains.annotations.NotNull;

/**
 * @author ilyas
 */
@State(
    name = "GroovyDebuggerSettings",
    storages = {
    @Storage(
        id = "groovy_debugger",
        file = "$APP_CONFIG$/groovy_debug.xml"
    )}
)
public class GroovyDebuggerSettings extends XDebuggerSettings<GroovyDebuggerSettings> {

  public Boolean DEBUG_DISABLE_SPECIFIC_GROOVY_METHODS = true;

  public GroovyDebuggerSettings() {
    super("groovy_debugger");
  }

  @NotNull
  public Configurable createConfigurable() {
    return new GroovyDebuggerSettingsConfigurable(this);
  }

  public GroovyDebuggerSettings getState() {
    return this;
  }

  public void loadState(final GroovyDebuggerSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public static GroovyDebuggerSettings getInstance() {
    return getInstance(GroovyDebuggerSettings.class);
  }

}
