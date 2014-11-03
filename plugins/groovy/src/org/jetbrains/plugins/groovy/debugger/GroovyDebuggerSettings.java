/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.debugger;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SimpleConfigurable;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.xdebugger.settings.DebuggerSettingsCategory;
import com.intellij.xdebugger.settings.XDebuggerSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;

import java.util.Collection;
import java.util.Collections;

import static java.util.Collections.singletonList;

public class GroovyDebuggerSettings extends XDebuggerSettings<GroovyDebuggerSettings> implements Getter<GroovyDebuggerSettings> {
  public Boolean DEBUG_DISABLE_SPECIFIC_GROOVY_METHODS = true;
  public boolean ENABLE_GROOVY_HOTSWAP = Registry.is("enable.groovy.hotswap");

  public GroovyDebuggerSettings() {
    super("groovy_debugger");
  }

  @NotNull
  @SuppressWarnings("EnumSwitchStatementWhichMissesCases")
  @Override
  public Collection<? extends Configurable> createConfigurables(@NotNull DebuggerSettingsCategory category) {
    switch (category) {
      case STEPPING:
        return singletonList(SimpleConfigurable.create("reference.idesettings.debugger.groovy", GroovyBundle.message("groovy.debug.caption"),
                                                       "reference.idesettings.debugger.groovy", GroovySteppingConfigurableUi.class, this));
      case HOTSWAP:
        return singletonList(SimpleConfigurable.create("reference.idesettings.debugger.groovy", GroovyBundle.message("groovy.debug.caption"),
                                                       "reference.idesettings.debugger.groovy", GroovyHotSwapConfigurableUi.class, this));
    }
    return Collections.emptyList();
  }

  @Override
  public GroovyDebuggerSettings getState() {
    return this;
  }

  @Override
  public void loadState(final GroovyDebuggerSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public static GroovyDebuggerSettings getInstance() {
    return getInstance(GroovyDebuggerSettings.class);
  }

  @Override
  public GroovyDebuggerSettings get() {
    return this;
  }
}