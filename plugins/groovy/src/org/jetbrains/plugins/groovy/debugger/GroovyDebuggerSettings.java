// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.debugger;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SimpleConfigurable;
import com.intellij.openapi.util.Getter;
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
  public boolean ENABLE_GROOVY_HOTSWAP = true;

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
        return singletonList(new GroovyHotSwapConfigurable(this));
    }
    return Collections.emptyList();
  }

  @Override
  public GroovyDebuggerSettings getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull final GroovyDebuggerSettings state) {
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