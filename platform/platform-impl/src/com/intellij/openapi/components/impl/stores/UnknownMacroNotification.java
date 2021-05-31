// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components.impl.stores;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author spleaner
 */
public class UnknownMacroNotification extends Notification {
  private final Collection<String> myMacros;

  public UnknownMacroNotification(@NotNull String groupId,
                                  @NotNull @NlsContexts.NotificationTitle String title,
                                  @NotNull @NlsContexts.NotificationContent String content,
                                  @NotNull NotificationType type,
                                  @Nullable NotificationListener listener,
                                  @NotNull Collection<String> macros) {
    super(groupId, title, content, type);
    if (listener != null) {
      setListener(listener);
    }

    myMacros = macros;
  }

  public Collection<String> getMacros() {
    return myMacros;
  }
}
