// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen

import com.intellij.ide.RecentProject
import com.intellij.ide.RecentProjectListActionProvider
import com.intellij.ide.RecentProjectProvider
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.wm.StartPagePromoter
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.ui.ComponentUtil
import com.intellij.util.ui.update.UiNotifyConnector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities

@TestApplication
// the error indicator of the notification toolbar reads the property, which the IDE launcher sets
@SystemProperty("idea.fatal.error.notification", "disabled")
internal class ProjectsTabFirstShowTest {
  @TestDisposable
  private lateinit var disposable: Disposable

  @Test
  fun `the promotion is built and shown at the first show of the tab`(): Unit = timeoutRunBlocking {
    val promoter = RecordingStartPagePromoter()
    maskExtensions(StartPagePromoter.START_PAGE_PROMOTER_EP, listOf(promoter))
    withContext(Dispatchers.EDT) {
      val component = ProjectsTab(disposable).associatedComponent
      assertThat(promoter.requestCount).isZero()

      markAsShownFirstTime(component)
      val promotion = promoter.promotions.single()
      assertThat(SwingUtilities.isDescendingFrom(promotion, component)).isTrue()
      assertThat(promotion.isVisible).isTrue()

      UiNotifyConnector.forceNotifyIsShown(component)
      assertThat(promoter.promotions).hasSize(1)
    }
  }

  @Test
  fun `the empty tab asks the recent project providers at its first show`() {
    val provider = CountingRecentProjectProvider()
    maskExtensions(ExtensionPointName<RecentProjectProvider>("com.intellij.recentProjectsProvider"), listOf(provider))
    maskExtensions(StartPagePromoter.START_PAGE_PROMOTER_EP, emptyList())
    // with a local recent project, the tab builds the recent project tree, and the tree asks the providers itself
    assumeTrue(RecentProjectListActionProvider.getInstance().countLocalProjects() == 0, "the test application has recent projects")
    timeoutRunBlocking {
      withContext(Dispatchers.EDT) {
        val component = ProjectsTab(disposable).associatedComponent
        assertThat(provider.requestCount).isZero()

        markAsShownFirstTime(component)
        assertThat(provider.requestCount).isPositive()
      }
    }
  }

  private fun <T : Any> maskExtensions(pointName: ExtensionPointName<T>, extensions: List<T>) {
    ExtensionTestUtil.maskExtensions(pointName, extensions, disposable, fireEvents = false)
  }
}

/**
 * A headless component never shows, so the tab is marked as showing, and its listeners are notified as Swing would notify them.
 */
private fun markAsShownFirstTime(component: JComponent) {
  ComponentUtil.forceMarkAsShowing(component, true)
  UiNotifyConnector.forceNotifyIsShown(component)
}

private class RecordingStartPagePromoter : StartPagePromoter {
  var requestCount: Int = 0
  val promotions: MutableList<JComponent> = ArrayList()

  override fun canCreatePromo(isEmptyState: Boolean): Boolean {
    requestCount++
    return true
  }

  override fun getPromotion(isEmptyState: Boolean): JComponent = JPanel().also { promotions.add(it) }
}

private class CountingRecentProjectProvider : RecentProjectProvider {
  var requestCount: Int = 0

  override val providerId: String
    get() = "ProjectsTabFirstShowTest"

  override fun getRecentProjects(): List<RecentProject> {
    requestCount++
    return emptyList()
  }
}
