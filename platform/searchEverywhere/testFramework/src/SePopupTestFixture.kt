// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.testFramework

import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper
import com.intellij.ide.actions.searcheverywhere.SplitSearchListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.frontend.SeFrontendService
import com.intellij.platform.searchEverywhere.frontend.SePopupInstance
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.ui.EDT
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import kotlin.time.Duration.Companion.minutes

/**
 * Opens the Search Everywhere popup of a project in a test, and searches in it.
 *
 * The popup is the one that [SeFrontendService] shows to a user, with all its tabs and providers.
 * So a test checks how a tab merges the results of its providers, and the order of the result list.
 * The popup loads all the results of a search, as if the user scrolls to the end of the list.
 * Call the functions on the EDT. A wait dispatches the EDT events, so the popup can work.
 * The fixture closes the popup when it goes away.
 */
@ApiStatus.Internal
@TestOnly
class SePopupTestFixture private constructor(
  private val service: SeFrontendService,
  private val popup: SePopupInstance,
) : Disposable {
  /** Whether the selected tab also searches outside the project. */
  val isEverywhere: Boolean get() = popup.isEverywhere()

  /**
   * Searches for [pattern] in the selected tab, and returns the elements of the result list, from the top.
   *
   * A goto item holds its element in a [PSIPresentationBgRendererWrapper.ItemWithPresentation]. The function takes the element out of it.
   * See [SePopupInstance.findElementsForPattern].
   */
  @Suppress("DEPRECATION")
  fun findElements(pattern: String): List<Any> {
    val rawObjects = PlatformTestUtil.waitForFuture(popup.findElementsForPattern(pattern), SEARCH_TIMEOUT.inWholeMilliseconds)
    return rawObjects.map { PSIPresentationBgRendererWrapper.getItem(it) }
  }

  /** Adds [listener] to the search events of the popup. */
  fun addSearchListener(listener: SplitSearchListener) {
    popup.addSplitSearchListener(listener)
  }

  override fun dispose() {
    popup.closePopup()
    PlatformTestUtil.waitWithEventsDispatching("The Search Everywhere popup did not close", { !service.isShown() },
                                               SEARCH_TIMEOUT.inWholeSeconds.toInt())
  }

  companion object {
    private val SEARCH_TIMEOUT = 1.minutes

    /**
     * Opens the Search Everywhere popup of [project] on the tab with [tabId], and waits until the popup is ready.
     *
     * The fixture goes away with [parentDisposable].
     */
    @JvmStatic
    fun open(project: Project, parentDisposable: Disposable, tabId: String): SePopupTestFixture {
      EDT.assertIsEdt()
      val service = SeFrontendService.getInstance(project)
      val dataContext = SimpleDataContext.getProjectContext(project)
      service.show(tabId, null, AnActionEvent.createEvent(dataContext, null, "", ActionUiKind.NONE, null))
      PlatformTestUtil.waitWithEventsDispatching("The Search Everywhere popup did not open",
                                                 { service.getCurrentlyShownPopupInstance() != null },
                                                 SEARCH_TIMEOUT.inWholeSeconds.toInt())

      val popup = service.getCurrentlyShownPopupInstance() as SePopupInstance
      // A popup in a headless test cannot scroll, so the popup would stop at the first rows of a long result list.
      popup.loadAllResults()

      val fixture = SePopupTestFixture(service, popup)
      Disposer.register(parentDisposable, fixture)
      return fixture
    }
  }
}
