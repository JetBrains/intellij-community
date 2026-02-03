// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification.impl;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableProvider;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class NotificationsConfigurableProvider extends ConfigurableProvider {
  @Override
  public boolean canCreateConfigurable() {
    return NotificationsConfigurationImpl.getInstanceImpl().getAllSettings().length > 0;
  }

  @Override
  public Configurable createConfigurable() {
    return new NotificationsConfigurable();
  }
}
