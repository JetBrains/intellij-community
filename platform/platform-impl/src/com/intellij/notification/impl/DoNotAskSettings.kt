// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification.impl

import com.intellij.ide.util.BasePropertyService
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.SimplePersistentStateComponent
import org.jetbrains.annotations.Nls

private const val DO_NOT_ASK_KEY_PREFIX = "Notification.DoNotAsk-"
private const val DO_NOT_ASK_DISPLAY_KEY_PREFIX = "Notification.DisplayName-DoNotAsk-"

internal class DoNotAskState : BaseState() {
  var idToDisplayName: MutableMap<String, String> by map()
}

internal abstract class DoNotAskSettings : SimplePersistentStateComponent<DoNotAskState>(DoNotAskState()) {

  @Synchronized
  fun isDoNotAsk(notificationId: String): Boolean = notificationId in state.idToDisplayName

  @Synchronized
  fun markDoNotAsk(notificationId: String, @Nls displayName: String?) {
    state.idToDisplayName[notificationId] = displayName ?: notificationId

    saveSettingsForRemDev()
  }

  @Synchronized
  fun getDoNotAskNotifications(): Map<String, String> = LinkedHashMap(state.idToDisplayName)

  @Synchronized
  open fun clearDoNotAsk(notificationId: String) {
    state.idToDisplayName.remove(notificationId)

    saveSettingsForRemDev()
  }

  abstract fun saveSettingsForRemDev()

  fun migrateFromPropertiesComponent(manager: PropertiesComponent) {
    if (manager !is BasePropertyService) return

    val idsToMigrate = ArrayList<String>()

    manager.forEachPrimitiveValue { key, _ ->
      if (key.startsWith(DO_NOT_ASK_KEY_PREFIX)) {
        val notificationId = key.substring(DO_NOT_ASK_KEY_PREFIX.length)
        idsToMigrate.add(notificationId)
      }
    }

    for (id in idsToMigrate) {
      val displayName = manager.getValue(DO_NOT_ASK_DISPLAY_KEY_PREFIX + id, id)

      state.idToDisplayName[id] = displayName

      manager.unsetValue(DO_NOT_ASK_KEY_PREFIX + id)
      manager.unsetValue(DO_NOT_ASK_DISPLAY_KEY_PREFIX + id)
    }
  }
}
