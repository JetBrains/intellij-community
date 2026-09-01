// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.advertiser

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginAdvertiserServiceImpl
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginSuggestionNotificationProvider
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.getPluginSuggestionNotificationGroup
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.showPluginSuggestionNotification
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.ProjectRule
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PluginSuggestionNotificationProviderTest {
  companion object {
    @JvmField
    @ClassRule
    val projectRule: ProjectRule = ProjectRule()
  }

  @JvmField
  @Rule
  val disposableRule: DisposableRule = DisposableRule()

  private val published = mutableListOf<Notification>()

  /** The thread each notification was published on. */
  private val publishedOnEdt = mutableListOf<Boolean>()

  @Before
  fun subscribe() {
    projectRule.project.messageBus.connect(disposableRule.disposable).subscribe(Notifications.TOPIC, object : Notifications {
      override fun notify(notification: Notification) {
        if (notification.displayId != DISPLAY_ID) return
        published.add(notification)
        publishedOnEdt.add(ApplicationManager.getApplication().isDispatchThread)
      }
    })
  }

  /**
   * The advertiser reaches the providers from `run`, which is the wiring the rest of the class
   * takes for granted.
   *
   * Empty unknown features leave the advertiser with nothing of its own to say: `fetchFeatures`
   * loops zero times, the empty plugin set skips the Marketplace lookup, and `notifyUser` takes the
   * branch that raises no balloon. So the case needs no network.
   */
  @Test
  fun theAdvertiserAsksWhenItHasNoOfferOfItsOwn() = runBlocking {
    register("from the advertiser")
    val scope = childScope("plugin advertiser service")
    try {
      PluginAdvertiserServiceImpl(projectRule.project, scope).run(
        customPlugins = emptyList(),
        unknownFeatures = emptyList(),
        includeIgnored = false,
      )
      // `run` launches into the service scope and returns, so the case waits for what it started.
      scope.coroutineContext.job.children.toList().joinAll()
    }
    finally {
      scope.cancel()
    }

    assertEquals("from the advertiser", published.single().content)
  }

  /**
   * The property an IDE that registers no provider rests on: the advertiser behaves as it did
   * before the extension point existed.
   */
  @Test
  fun nothingIsPublishedWithoutProviders() {
    runBlocking { showPluginSuggestionNotification(projectRule.project) }

    assertTrue(published.isEmpty(), "a notification was published with no provider registered")
  }

  /** The advertiser asks to fill the one balloon it did not raise, so a second answer is not read. */
  @Test
  fun theFirstAnswerWins() {
    val asked = mutableListOf<String>()
    register("first") { asked.add("first") }
    register("second") { asked.add("second") }

    runBlocking { showPluginSuggestionNotification(projectRule.project) }

    assertEquals(listOf("first"), asked)
    assertEquals(1, published.size)
    assertEquals("first", published.single().content)
  }

  /** A provider that offers nothing lets the next one answer. */
  @Test
  fun aProviderThatOffersNothingIsSkipped() {
    PluginSuggestionNotificationProvider.EP_NAME.point.registerExtension(object : PluginSuggestionNotificationProvider {
      override suspend fun createNotification(project: Project): Notification? = null
    }, disposableRule.disposable)
    register("second")

    runBlocking { showPluginSuggestionNotification(projectRule.project) }

    assertEquals("second", published.single().content)
  }

  /**
   * A provider that fails costs its own offer. The advertiser runs inside a coroutine of the
   * project scope, and an exception let out of the extension list would take that scope down.
   */
  @Test
  fun aProviderThatFailsIsSkipped() {
    PluginSuggestionNotificationProvider.EP_NAME.point.registerExtension(object : PluginSuggestionNotificationProvider {
      override suspend fun createNotification(project: Project): Notification = throw UnsupportedOperationException("broken provider")
    }, disposableRule.disposable)
    register("second")

    LoggedErrorProcessor.executeWith<Throwable>(object : LoggedErrorProcessor() {
      override fun processError(category: String, message: String, details: Array<String>, t: Throwable?): Set<Action> = setOf(Action.LOG)
    }) {
      runBlocking { showPluginSuggestionNotification(projectRule.project) }
    }

    assertEquals("second", published.single().content)
  }

  /** A provider takes its own notification down, and the advertiser publishes no dead balloon. */
  @Test
  fun anExpiredNotificationIsNotPublished() {
    PluginSuggestionNotificationProvider.EP_NAME.point.registerExtension(object : PluginSuggestionNotificationProvider {
      override suspend fun createNotification(project: Project): Notification = notification("expired").also { it.expire() }
    }, disposableRule.disposable)

    runBlocking { showPluginSuggestionNotification(projectRule.project) }

    assertTrue(published.isEmpty(), "an expired notification reached the notifications model")
  }

  /**
   * The publish takes the EDT, which is where a provider takes its notification down. Off the EDT
   * the publish is queued through `invokeLater`, and the Notifications tool window then keeps a row
   * for a balloon that was expired before it was drawn.
   */
  @Test
  fun theNotificationIsPublishedOnTheEdt() {
    register("on the EDT")

    val done = ApplicationManager.getApplication().executeOnPooledThread {
      runBlocking { showPluginSuggestionNotification(projectRule.project) }
    }
    done.get()

    assertEquals(listOf(true), publishedOnEdt)
  }

  /** The provider keeps its own actions, which is what the returned notification carries. */
  @Test
  fun theProviderKeepsItsActions() {
    register("with an action")

    runBlocking { showPluginSuggestionNotification(projectRule.project) }

    assertFalse(published.single().actions.isEmpty(), "the provider's action was dropped")
  }

  private fun register(content: String, onAsked: () -> Unit = {}) {
    PluginSuggestionNotificationProvider.EP_NAME.point.registerExtension(object : PluginSuggestionNotificationProvider {
      override suspend fun createNotification(project: Project): Notification {
        onAsked()
        return notification(content)
      }
    }, disposableRule.disposable)
  }

  private fun notification(content: String): Notification =
    getPluginSuggestionNotificationGroup()
      .createNotification(content, NotificationType.INFORMATION)
      .setDisplayId(DISPLAY_ID)
      .addAction(NotificationAction.createSimple("action") {})
}

private const val DISPLAY_ID: String = "plugin.suggestion.notification.provider.test"
