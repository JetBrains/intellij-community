// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.notification.impl.ui.NotificationsConfigurableUi;
import com.intellij.openapi.options.ConfigurableBase;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
public final class NotificationsConfigurable extends ConfigurableBase<NotificationsConfigurableUi, NotificationsConfigurationImpl> {
  static final @NonNls String ID = "reference.settings.ide.settings.notifications";

  public NotificationsConfigurable() {
    super(ID, displayName(), ID);
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
