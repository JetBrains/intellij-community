// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification.impl;

import com.intellij.ide.ui.search.SearchableOptionContributor;
import com.intellij.ide.ui.search.SearchableOptionProcessor;
import org.jetbrains.annotations.NotNull;

final class NotificationSearchableOptionContributor extends SearchableOptionContributor {
  @Override
  public void processOptions(@NotNull SearchableOptionProcessor processor) {
    for (NotificationSettings settings : NotificationsConfigurationImpl.getInstanceImpl().getAllSettings()) {
      processor.addOptions(settings.getGroupId(), null, settings.getGroupId() + " notifications",
              NotificationsConfigurable.ID, NotificationsConfigurable.displayName(), true);
    }
  }
}
