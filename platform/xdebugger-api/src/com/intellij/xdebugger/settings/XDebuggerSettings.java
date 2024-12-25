// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.settings;

import com.intellij.openapi.components.PersistentStateComponent;
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
 */
public abstract class XDebuggerSettings<T> implements PersistentStateComponent<T> {
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

  /**
   * @deprecated Please use {@link #createConfigurables(DebuggerSettingsCategory)}
   */
  @Deprecated(forRemoval = true)
  public @Nullable Configurable createConfigurable() {
    return null;
  }

  public @NotNull Collection<? extends Configurable> createConfigurables(@NotNull DebuggerSettingsCategory category) {
    return Collections.emptyList();
  }

  public void generalApplied(@NotNull DebuggerSettingsCategory category) {
  }

  public boolean isTargetedToProduct(@NotNull Configurable configurable) {
    return false;
  }
}
