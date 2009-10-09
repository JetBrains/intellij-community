package com.intellij.openapi.components.impl.stores;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author spleaner
 */
public class UnknownMacroNotification extends Notification {
  private Collection<String> myMacros;

  public UnknownMacroNotification(@NotNull String groupId,
                                  @NotNull String title,
                                  @NotNull String content,
                                  @NotNull NotificationType type,
                                  @Nullable NotificationListener listener,
                                  @NotNull Collection<String> macros) {
    super(groupId, title, content, type, listener);

    myMacros = macros;
  }

  public Collection<String> getMacros() {
    return myMacros;
  }
}
