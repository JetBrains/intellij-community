// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.notification.impl.ui.NotificationsConfigurableUi;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.options.BackedByPersistentState;
import com.intellij.openapi.options.ConfigurableBase;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
public final class NotificationsConfigurable extends ConfigurableBase<NotificationsConfigurableUi, NotificationsConfigurationImpl> implements BackedByPersistentState {
  static final @NonNls String ID = "reference.settings.ide.settings.notifications";

  public NotificationsConfigurable() {
    super(ID, displayName(), ID);
  }

  @ApiStatus.Internal
  @Override
  public @NotNull Collection<PersistentStateComponent<?>> getBackingComponents() {
    return List.of(NotificationsConfigurationImpl.getInstanceImpl());
  }

  public static @NotNull @NlsContexts.ConfigurableName String displayName() {
    return IdeBundle.message("notification.configurable.display.name.notifications");
  }

  @Override
  protected @NotNull NotificationsConfigurationImpl getSettings() {
    return NotificationsConfigurationImpl.getInstanceImpl();
  }

  @Override
  protected NotificationsConfigurableUi createUi() {
    return new NotificationsConfigurableUi(getSettings());
  }
}
