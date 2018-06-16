// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification.impl;

import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationsConfiguration;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.messages.MessageBus;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;

/**
 * @author spleaner
 */
@State(
  name = "NotificationConfiguration",
  storages = @Storage("notifications.xml")
)
public class NotificationsConfigurationImpl extends AbstractNotificationsConfigurationImpl implements Disposable, ApplicationComponent {

  private final MessageBus myMessageBus;

  public NotificationsConfigurationImpl(@NotNull MessageBus bus) {
    myMessageBus = bus;
  }

  public static NotificationsConfigurationImpl getInstanceImpl() {
    return (NotificationsConfigurationImpl)getNotificationsConfiguration();
  }

  public synchronized boolean hasToolWindowCapability(@NotNull String groupId) {
    return getToolWindowId(groupId) != null || myToolWindowCapable.containsKey(groupId);
  }

  @Nullable
  public String getToolWindowId(@NotNull String groupId) {
    NotificationGroup group = NotificationGroup.findRegisteredGroup(groupId);
    return group == null ? null : group.getToolWindowId();
  }

  public synchronized NotificationSettings[] getAllSettings() {
    Collection<NotificationSettings> settings = new THashSet<>(myIdToSettingsMap.values());
    for (NotificationGroup group : NotificationGroup.getAllRegisteredGroups()) {
      if (group.getDisplayId().startsWith(LIGHTWEIGHT_PREFIX)) continue;
      settings.add(getSettings(group.getDisplayId()));
    }
    NotificationSettings[] result = settings.toArray(new NotificationSettings[0]);
    Arrays.sort(result, NOTIFICATION_SETTINGS_COMPARATOR);
    return result;
  }

  public static void remove(String... toRemove) {
    getInstanceImpl().doRemove(toRemove);
  }

  @Override
  public void initComponent() {
    myMessageBus.connect(this).subscribe(TOPIC, this);
  }

  @Override
  public synchronized void dispose() {
    myIdToSettingsMap.clear();
  }

  @Override
  public void register(@NotNull
                       final String groupDisplayName, @NotNull
                       final NotificationDisplayType displayType) {
    register(groupDisplayName, displayType, true);
  }

  @Override
  public void register(@NotNull String groupDisplayName,
                       @NotNull NotificationDisplayType displayType,
                       boolean shouldLog) {
    register(groupDisplayName, displayType, shouldLog, false);
  }

  @Override
  public void register(@NotNull String groupDisplayName,
                       @NotNull NotificationDisplayType displayType,
                       boolean shouldLog,
                       boolean shouldReadAloud) {
    if (!isRegistered(groupDisplayName)) {
      // register a new group and remember these settings as default
      new NotificationGroup(groupDisplayName, displayType, shouldLog);
      // and decide whether to save them explicitly (in case of non-default shouldReadAloud)
      changeSettings(groupDisplayName, displayType, shouldLog, shouldReadAloud);
    }
    else if (displayType == NotificationDisplayType.TOOL_WINDOW && !hasToolWindowCapability(groupDisplayName)) {
      // the first time with tool window capability
      changeSettings(getSettings(groupDisplayName).withDisplayType(NotificationDisplayType.TOOL_WINDOW));
      myToolWindowCapable.put(groupDisplayName, null);
    }
  }

  @NotNull
  public static NotificationSettings getSettings(@NotNull String groupId) {
    NotificationsConfigurationImpl impl = getInstanceImpl();
    NotificationSettings settings = impl.getSettingsNullable(groupId);
    return settings == null ? getDefaultSettings(groupId) : settings;
  }

  public synchronized boolean isRegistered(@NotNull
                                           final String id) {
    return myIdToSettingsMap.containsKey(id) || NotificationGroup.findRegisteredGroup(id) != null;
  }


}
