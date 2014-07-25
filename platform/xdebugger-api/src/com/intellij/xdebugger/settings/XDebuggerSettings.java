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
package com.intellij.xdebugger.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.options.Configurable;
import com.intellij.xdebugger.XDebuggerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

/**
 * Implement this class to provide settings page for debugger. Settings page will be placed under 'Debugger' node in the 'Settings' dialog.
 * An implementation should be registered in plugin.xml:
 * <p>
 * &lt;extensions defaultExtensionNs="com.intellij"&gt;<br>
 * &nbsp;&nbsp;&lt;xdebugger.settings implementation="qualified-class-name"/&gt;<br>
 * &lt;/extensions&gt;
 *
 * @author nik
 */
public abstract class XDebuggerSettings<T> implements PersistentStateComponent<T> {
  public static final ExtensionPointName<XDebuggerSettings> EXTENSION_POINT = ExtensionPointName.create("com.intellij.xdebugger.settings");

  private final String myId;

  protected XDebuggerSettings(final @NotNull @NonNls String id) {
    myId = id;
  }

  protected static <S extends XDebuggerSettings<?>> S getInstance(Class<S> aClass) {
    return XDebuggerUtil.getInstance().getDebuggerSettings(aClass);
  }

  public final String getId() {
    return myId;
  }

  @Nullable
  @Deprecated
  /**
   * @deprecated Please use {@link #createConfigurables(DebuggerSettingsCategory)}
   */
  public Configurable createConfigurable() {
    return null;
  }

  @NotNull
  public Collection<? extends Configurable> createConfigurables(@NotNull DebuggerSettingsCategory category) {
    return Collections.emptyList();
  }

  public void generalApplied(@NotNull DebuggerSettingsCategory category) {
  }

  public boolean isTargetedToProduct(@NotNull Configurable configurable) {
    return false;
  }
}
