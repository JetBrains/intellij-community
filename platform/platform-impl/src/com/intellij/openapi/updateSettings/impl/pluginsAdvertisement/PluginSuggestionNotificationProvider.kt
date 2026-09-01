// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement

import com.intellij.notification.Notification
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.rethrowControlFlowException
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting

/**
 * Provides the balloon the advertiser raises for a project it has no offer of its own for.
 *
 * The advertiser matches a plugin to an [UnknownFeature] by string equality on the implementation
 * name, so a `dependencySupport` bean cannot express an offer that depends on which version of a
 * dependency the project declares. A provider decides that question itself and builds the whole
 * notification, so it keeps its own notification group, its own actions and its own statistics.
 *
 * The advertiser asks a provider after it decides that it raises no balloon of its own for this
 * project. It asks nobody when `ide.show.plugin.suggestions.on.open` is off, on an untrusted
 * project, or in a headless IDE, because the advertiser does not run on those paths.
 *
 * Implemented by a bundled IDE plugin, in the same way as [PluginSuggestionProvider].
 */
@ApiStatus.Internal
interface PluginSuggestionNotificationProvider {
  /**
   * The balloon this provider offers [project], or null when it offers none.
   *
   * Called on a background thread. It may suspend and it may take a read action. The advertiser
   * publishes the notification on the EDT, and publishes nothing when the provider has already
   * expired it.
   */
  suspend fun createNotification(project: Project): Notification?

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<PluginSuggestionNotificationProvider> =
      ExtensionPointName("com.intellij.pluginSuggestionNotificationProvider")
  }
}

private val LOG = logger<PluginSuggestionNotificationProvider>()

/**
 * Publishes the first balloon a provider offers [project].
 *
 * The first answer wins. The advertiser asks to fill the one balloon it did not raise, so a second
 * balloon here recreates the stacking this extension point prevents.
 *
 * The publish takes the EDT because a provider takes the notification down from there. Off the EDT,
 * `Notifications.Bus.notify` queues the publish through `invokeLater`, and a provider can then
 * expire a notification the notifications model has not seen. That model adds its tool-window row
 * without reading the expiry, so the row outlives the balloon.
 */
@VisibleForTesting
@ApiStatus.Internal
suspend fun showPluginSuggestionNotification(project: Project) {
  val notification = createSuggestionNotification(project) ?: return

  withContext(Dispatchers.EDT) {
    if (!notification.isExpired) {
      notification.notify(project)
    }
  }
}

/**
 * Asks each provider in turn and answers with the first notification offered.
 *
 * A provider that fails is logged under its own class and skipped, so a broken plugin costs its own
 * offer and leaves the advertiser running. `ExtensionProcessingHelper.computeSafeIfAny` holds the
 * same handling for a provider that does not suspend.
 */
private suspend fun createSuggestionNotification(project: Project): Notification? {
  for (provider in PluginSuggestionNotificationProvider.EP_NAME.extensionList) {
    try {
      return provider.createNotification(project) ?: continue
    } catch (e: Throwable) {
      rethrowControlFlowException(e)
      LOG.error("${provider.javaClass.name} failed to create a plugin suggestion", e)
    }
  }
  return null
}
