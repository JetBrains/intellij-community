/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.debugger.filters;

import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.util.registry.Registry;
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
        file = StoragePathMacros.APP_CONFIG + "/groovy_debug.xml"
    )}
)
public class GroovyDebuggerSettings extends XDebuggerSettings<GroovyDebuggerSettings> {

  public Boolean DEBUG_DISABLE_SPECIFIC_GROOVY_METHODS = true;
  public boolean ENABLE_GROOVY_HOTSWAP = Registry.is("enable.groovy.hotswap");

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
