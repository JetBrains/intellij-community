/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
  private final Collection<String> myMacros;

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
