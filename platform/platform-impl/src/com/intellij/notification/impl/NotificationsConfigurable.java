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
package com.intellij.notification.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.notification.impl.ui.NotificationsConfigurableUi;
import com.intellij.openapi.options.ConfigurableBase;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 * @author Konstantin Bulenkov
 */
public class NotificationsConfigurable extends ConfigurableBase<NotificationsConfigurableUi, NotificationsConfigurationImpl> {
  @NonNls static final String ID = "reference.settings.ide.settings.notifications";

  public NotificationsConfigurable() {
    super(ID, displayName(), ID);
  }

  @NotNull
  public static @NlsContexts.ConfigurableName String displayName() {
    return IdeBundle.message("notification.configurable.display.name.notifications");
  }

  @NotNull
  @Override
  protected NotificationsConfigurationImpl getSettings() {
    return NotificationsConfigurationImpl.getInstanceImpl();
  }

  @Override
  protected NotificationsConfigurableUi createUi() {
    return new NotificationsConfigurableUi(getSettings());
  }
}
