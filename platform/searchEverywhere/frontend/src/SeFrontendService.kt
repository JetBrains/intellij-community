// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend

import com.intellij.ide.actions.SearchEverywhereManagerFactory
import com.intellij.ide.actions.searcheverywhere.*
import com.intellij.ide.actions.searcheverywhere.PreviewExperiment.isExperimentEnabled
import com.intellij.ide.actions.searcheverywhere.statistics.SearchEverywhereUsageTriggerCollector
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.WindowStateService
import com.intellij.openapi.wm.WindowManager
import com.intellij.platform.searchEverywhere.SeProviderId
import com.intellij.platform.searchEverywhere.SeSession
import com.intellij.platform.searchEverywhere.SeSessionEntity
import com.intellij.platform.searchEverywhere.asRef
import com.intellij.platform.searchEverywhere.frontend.tabs.actions.SeActionsTab
import com.intellij.platform.searchEverywhere.frontend.tabs.all.SeAllTab
import com.intellij.platform.searchEverywhere.frontend.tabs.classes.SeClassesTab
import com.intellij.platform.searchEverywhere.frontend.tabs.files.SeFilesTab
import com.intellij.platform.searchEverywhere.frontend.tabs.symbols.SeSymbolsTab
import com.intellij.platform.searchEverywhere.frontend.tabs.text.SeTextTab
import com.intellij.platform.searchEverywhere.frontend.ui.SePopupContentPane
import com.intellij.platform.searchEverywhere.frontend.ui.SePopupHeaderPane
import com.intellij.platform.searchEverywhere.frontend.vm.SePopupVm
import com.intellij.platform.searchEverywhere.providers.SeLog
import com.intellij.platform.searchEverywhere.providers.SeLog.LIFE_CYCLE
import com.intellij.platform.searchEverywhere.providers.SeProvidersHolder
import com.intellij.platform.searchEverywhere.providers.computeCatchingOrNull
import com.intellij.platform.searchEverywhere.utils.SuspendLazyProperty
import com.intellij.platform.searchEverywhere.utils.initAsync
import com.intellij.platform.util.coroutines.childScope
import com.intellij.platform.util.coroutines.sync.OverflowSemaphore
import com.intellij.ui.ScreenUtil
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.popup.AbstractPopup
import com.intellij.util.ui.EDT
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import fleet.kernel.change
import fleet.kernel.shared
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import java.awt.KeyboardFocusManager
import java.awt.Point
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities

@ApiStatus.Internal
@Service(Service.Level.PROJECT, Service.Level.APP)
class SeFrontendService(val project: Project?, private val coroutineScope: CoroutineScope) : SearchEverywhereManager {
  @Suppress("unused")
  constructor(coroutineScope: CoroutineScope) : this(null, coroutineScope)

  private val popupSemaphore = OverflowSemaphore(1, overflow = BufferOverflow.DROP_LATEST)

  @Volatile
  private var popupInstanceFuture: CompletableFuture<SePopupInstance>? = null
  private val popupInstance: SePopupInstance?
    get() = if (EDT.isCurrentThreadEdt()) popupInstanceFuture?.getNow(null)
    else runCatching { popupInstanceFuture?.get(2, TimeUnit.SECONDS) }.getOrNull()


  @Volatile
  var localProvidersHolder: SeProvidersHolder? = null
    private set

  private val historyList = SearchHistoryList(true)
  private var visibleTabsState: List<SePopupHeaderPane.Tab>? = null

  val removeSessionRef: AtomicBoolean = AtomicBoolean(true)

  override fun show(tabId: String, searchText: String?, initEvent: AnActionEvent) {
    EDT.assertIsEdt()

    val showPopupStartTime = System.currentTimeMillis()

    val tabFactories = SeTabFactory.EP_NAME.extensionList
    val initialTabs = visibleTabsState
                      ?: tabFactories.filterIsInstance<SeEssentialTabFactory>().map { SePopupHeaderPane.Tab(it.name, it.id, it.id) }

    val popupClosedCompletable = CompletableDeferred<Unit>()
    val searchStatePublisher = SeSearchStatePublisher()
    val popupScope = coroutineScope.childScope("SearchEverywhereFrontendService popup scope")
    val (popup, popupContentPane) = createAndShowIdlePopup(popupScope, initialTabs, tabId, searchText, searchStatePublisher) {
      popupInstance?.saveSearchText()
      visibleTabsState = it.visibleTabsInfo
      popupClosedCompletable.complete(Unit)
    }

    val showIdlePopupEndTime = System.currentTimeMillis()
    SeLog.log { "Search Everywhere Idle popup opened in ${showIdlePopupEndTime - showPopupStartTime} ms" }

    val popupFuture = CompletableFuture<SePopupInstance>()
    popupInstanceFuture = popupFuture

    coroutineScope.launch {
      val session = SeSessionEntity.createSession()

      try {
        popupSemaphore.withPermit {
          val providersHolder = SeProvidersHolder.initialize(initEvent, project, session, "Frontend", false)
          localProvidersHolder = providersHolder
          initializeVmAndSetToPopup(popupFuture,
                                    popup,
                                    popupContentPane,
                                    searchStatePublisher,
                                    tabFactories,
                                    tabId,
                                    searchText,
                                    initEvent,
                                    popupScope,
                                    session,
                                    providersHolder.legacyAllTabContributors)

          val showPopupEndTime = System.currentTimeMillis()
          SeLog.log { "Search Everywhere popup opened in ${showPopupEndTime - showPopupStartTime} ms" }

          popupClosedCompletable.await()
        }
      }
      finally {
        popupInstanceFuture = null
        localProvidersHolder?.let { Disposer.dispose(it) }
        localProvidersHolder = null

        withContext(NonCancellable) {
          popupScope.cancel()
          if (removeSessionRef.get()) {
            change {
              shared {
                session.asRef().derefOrNull()?.delete()
              }
            }
          }
        }
      }
    }
  }

  private suspend fun initializeVmAndSetToPopup(
    popupFuture: CompletableFuture<SePopupInstance>,
    popup: JBPopup,
    popupContentPane: SePopupContentPane,
    searchStatePublisher: SeSearchStatePublisher,
    tabFactories: List<SeTabFactory>,
    tabId: String,
    searchText: String?,
    initEvent: AnActionEvent,
    popupScope: CoroutineScope,
    session: SeSession,
    availableLegacyContributors: Map<SeProviderId, SearchEverywhereContributor<Any>>,
  ) {
    val tabInitializationTimeoutMillis: Long = 50
    val orderedTabFactoryIds = tabFactories.map { it.id }

    val tabsOrDeferredTabs = tabFactories.map {
      it.id to initAsync(popupScope) {
        computeCatchingOrNull({ e -> "Error while getting tab from ${it.id} tab factory: ${e.message}" }) {
          it.getTab(popupScope, project, session, initEvent) { action ->
            popupInstance?.registerShortcut?.invoke(action)
          }
        }
      }
    }.map { (loadingTabId, tabLoadingProperty) ->
      popupScope.async {
        withTimeoutOrNull(tabInitializationTimeoutMillis) {
          tabLoadingProperty.getValue()
        } ?: run {
          if ((tabId + MAIN_TABS).contains(loadingTabId)) {
            SeLog.warn("Tab $tabId initialization took too long (> ${tabInitializationTimeoutMillis}ms), waiting it's initialization anyway")
            // If we have to open this tab right after the popup is there, we wait until it gets initialized
            tabLoadingProperty.getValue()
          }
          else {
            SeLog.log(LIFE_CYCLE) { "Tab $tabId initialization took too long (> ${tabInitializationTimeoutMillis}ms), will be initialized later" }
            tabLoadingProperty
          }
        }
      }
    }.awaitAll()

    val tabs = tabsOrDeferredTabs.filterIsInstance<SeTab>().sortedWith { tab1, tab2 ->
      val order1 = orderedTabFactoryIds.indexOf(tab1.id).let { if (it == -1) orderedTabFactoryIds.size + 1 else it }
      val order2 = orderedTabFactoryIds.indexOf(tab2.id).let { if (it == -1) orderedTabFactoryIds.size + 1 else it }

      order1 - order2
    }
    val deferredTabs = tabsOrDeferredTabs.filterIsInstance<SuspendLazyProperty<SeTab?>>()

    val popupVm = SePopupVm(popupScope, session, project, tabs, deferredTabs, searchText, tabId, historyList, availableLegacyContributors, onShowFindToolWindow = {
      popupScope.launch(NonCancellable) {
        removeSessionRef.set(false)
        try {
          it.openInFindWindow(session, initEvent)
        } finally {
          change {
            shared {
              session.asRef().derefOrNull()?.delete()
            }
          }
        }
      }
      popupScope.cancel()
    }, closePopupHandler = {
      popup.cancel()
    })
    popupVm.showTab(tabId)

    popupContentPane.setVm(popupVm)
    popupFuture.complete(SePopupInstance(popupVm, popupContentPane, searchStatePublisher))
  }

  private fun createAndShowIdlePopup(
    popupScope: CoroutineScope,
    initialTabs: List<SePopupHeaderPane.Tab>,
    selectedTabId: String,
    searchText: String?,
    searchStatePublisher: SeSearchStatePublisher,
    onCancel: (SePopupContentPane) -> Unit
  ): Pair<JBPopup, SePopupContentPane> {
    var popup: JBPopup? = null

    val contentPane = SePopupContentPane(project,
                                         resizePopupHandler = { size ->
                                           popup?.let { popup ->
                                             popup.setMinimumSize(popup.content.minimumSize)
                                             popup.size = size
                                           }
                                         },
                                         searchStatePublisher,
                                         popupScope,
                                         initialTabs,
                                         selectedTabId,
                                         searchText,
                                         getStateService().getSize(POPUP_LOCATION_SETTINGS_KEY))

    popup = createPopup(contentPane, project) { onCancel(contentPane) }
    calcPopupPositionAndShow(popup, contentPane)

    return popup to contentPane
  }

  private fun createPopup(panel: SePopupContentPane, project: Project?, onCancel: () -> Unit): JBPopup {
    val popup = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, panel.preferableFocusedComponent)
      .setProject(project)
      .setModalContext(false)
      .setNormalWindowLevel(StartupUiUtil.isWaylandToolkit())
      .setCancelOnWindowDeactivation(!StartupUiUtil.isWaylandToolkit())
      .setCancelOnClickOutside(true)
      .setRequestFocus(true)
      .setCancelKeyEnabled(false)
      .setResizable(true)
      .setMovable(true)
      .setDimensionServiceKey(project, POPUP_LOCATION_SETTINGS_KEY, true)
      .setLocateWithinScreenBounds(false)
      .setCancelCallback {
        onCancel()
        SearchEverywhereUsageTriggerCollector.DIALOG_CLOSED.log(project, true)
        true
      }
      .createPopup()

    popup.size = panel.preferredSize
    popup.setMinimumSize(panel.getMinimumSize(true))

    (popup as? AbstractPopup)?.let { popup ->
      popup.addResizeListener({
        if (panel.isCompactViewMode) {
          panel.popupExtendedSize = Dimension(popup.size.width,
                                              panel.popupExtendedSize?.height ?: panel.getExpandedSize().height)
        }
        else panel.popupExtendedSize = popup.size
      }, popup)
    }

    Disposer.register(popup) {
      getStateService().putSize(POPUP_LOCATION_SETTINGS_KEY, panel.popupExtendedSize)
      Disposer.dispose(panel)
    }

    return popup
  }

  private fun calcPopupPositionAndShow(popup: JBPopup, panel: SePopupContentPane) {
    val savedLocation: Point? = getStateService().getLocation(POPUP_LOCATION_SETTINGS_KEY)

    // for first show and short mode popup should be shifted to the top screen half
    if (savedLocation == null && panel.isCompactViewMode) {
      val window = if (project != null)
        WindowManager.getInstance().suggestParentWindow(project)
      else
        KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow
      val parent = UIUtil.findUltimateParent(window)

      if (parent != null) {
        val popupSize = popup.size

        val screenPoint = Point((parent.size.width - popupSize.width) / 2, parent.getHeight() / 4 - popupSize.height / 2)
        SwingUtilities.convertPointToScreen(screenPoint, parent)

        val screenRectangle = ScreenUtil.getScreenRectangle(screenPoint)
        val insets = panel.getInsets()
        val bottomEdge: Int = screenPoint.y + panel.getExpandedSize().height + insets.bottom + insets.top
        val shift = bottomEdge - screenRectangle.maxY.toInt()
        if (shift > 0) {
          screenPoint.y = Integer.max(screenPoint.y - shift, screenRectangle.y)
        }

        val showPoint = RelativePoint(screenPoint)
        popup.show(showPoint)
        return
      }
    }

    if (project != null) {
      popup.showCenteredInCurrentWindow(project)
    }
    else {
      popup.showInFocusCenter()
    }
  }

  private fun getStateService(): WindowStateService {
    return if (project != null) WindowStateService.getInstance(project) else WindowStateService.getInstance()
  }

  override fun isShown(): Boolean = popupInstance != null

  @Deprecated("Deprecated in the interface")
  override fun getCurrentlyShownUI(): SearchEverywhereUI {
    throw UnsupportedOperationException("The method is deprecated. Please use getCurrentlyShownPopupInstance() instead.")
  }

  override fun getCurrentlyShownPopupInstance(): SearchEverywherePopupInstance? = popupInstance

  override fun getSelectedTabID(): String = popupInstance?.getSelectedTabID() ?: ""

  override fun setSelectedTabID(tabID: String) {
    popupInstance?.setSelectedTabID(tabID)
  }

  override fun toggleEverywhereFilter() {
    popupInstance?.toggleEverywhereFilter()
  }

  override fun isEverywhere(): Boolean = popupInstance?.isEverywhere() ?: false

  @ApiStatus.Internal
  override fun isSplit(): Boolean = true

  @ApiStatus.Internal
  override fun isPreviewEnabled(): Boolean {
    return isExperimentEnabled
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project?): SeFrontendService = project?.getService(SeFrontendService::class.java)
                                                            ?: service<SeFrontendService>()

    @JvmStatic
    val isEnabled: Boolean get() = SearchEverywhereFeature.isSplit

    internal const val POPUP_LOCATION_SETTINGS_KEY: String = "search.everywhere.popup"
    private val MAIN_TABS = setOf(SeAllTab.ID, SeFilesTab.ID, SeClassesTab.ID, SeSymbolsTab.ID, SeActionsTab.ID, SeTextTab.ID)
  }
}

@ApiStatus.Internal
class RemDevFriendlySearchEverywhereManager : SearchEverywhereManagerFactory {
  override fun isAvailable(): Boolean = SeFrontendService.isEnabled
  override fun getManager(project: Project?): SearchEverywhereManager = SeFrontendService.getInstance(project)
}