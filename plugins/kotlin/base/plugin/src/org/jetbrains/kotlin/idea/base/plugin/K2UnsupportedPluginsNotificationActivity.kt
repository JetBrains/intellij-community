// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.plugin

import com.intellij.ide.ApplicationActivity
import com.intellij.ide.BrowserUtil
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.util.NlsSafe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class K2UnsupportedPluginsNotificationActivity : ApplicationActivity {
    override suspend fun execute() {
        val incompatiblePlugins = getPluginsDependingOnKotlinPluginInK2ModeAndIncompatibleWithIt()
        val propertiesComponent = serviceAsync<PropertiesComponent>()

        val needToShowFor = incompatiblePlugins.filterNot {
            propertiesComponent.getBoolean(getNotificationShownKey(it), /* defaultValue = */ false)
        }
        if (needToShowFor.isEmpty()) return

        showNotification(needToShowFor)

        needToShowFor.forEach {
            propertiesComponent.setValue(getNotificationShownKey(it), /* value = */ true)
        }
    }

    private suspend fun showNotification(incompatiblePlugins: List<IdeaPluginDescriptorImpl>) {
        withContext(Dispatchers.EDT + ModalityState.nonModal().asContextElement()) {
            @NlsSafe val pluginsList = incompatiblePlugins.joinToString(separator = "<br/>") { it.name }

            NotificationGroupManager.getInstance().getNotificationGroup("Plugins Incompatible With Kotlin Plugin in K2 Mode")
                .createNotification(
                    KotlinBasePluginBundle.message("plugins.incompatible.with.k2.title"),
                    pluginsList,
                    NotificationType.WARNING
                )
                .addAction(NotificationAction.create(KotlinBasePluginBundle.message("action.about.k2.mode.text")) {
                    BrowserUtil.browse("https://blog.jetbrains.com/idea/2024/08/meet-the-renovated-kotlin-support-k2-mode/")
                })
                .addAction(NotificationAction.create(KotlinBasePluginBundle.message("action.show.incompatible.plugins.text")) {
                    PluginManagerConfigurable.showPluginConfigurable(/* project = */ null, incompatiblePlugins.map { it.pluginId })
                })
                .notify(/* project = */ null)
        }
    }

    private fun getNotificationShownKey(plugin: IdeaPluginDescriptorImpl): String =
        "kotlin.k2.incompatible.plugin.notification.shown.for.${plugin.pluginId.idString}"
}

