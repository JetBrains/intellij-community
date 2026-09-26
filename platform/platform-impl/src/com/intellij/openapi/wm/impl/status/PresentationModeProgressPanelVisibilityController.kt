// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(FlowPreview::class)

package com.intellij.openapi.wm.impl.status

import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.UI
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.ClientProperty
import com.intellij.ui.ComponentUtil
import com.intellij.util.cancelOnDispose
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import java.awt.Component
import kotlin.time.Duration.Companion.milliseconds

@Service(Service.Level.APP)
internal class PresentationModeProgressPanelVisibilityController(private val coroutineScope: CoroutineScope) {
  companion object {
    @JvmStatic fun getInstance(): PresentationModeProgressPanelVisibilityController = service()
  }

  fun attachTo(progress: InfoAndProgressPanel.MyProgressComponent, component: Component) {
    component.isVisible = false // To avoid initial flickering when it should be initially invisible (most cases, actually).
    // Sadly, can't use launchOnShow here because the whole purpose of this thing is to show and hide the component.
    val componentScope = coroutineScope.childScope("PresentationModeProgressPanel visibility controller", Dispatchers.UI)
    componentScope.coroutineContext.job.cancelOnDispose(progress)
    componentScope.launch(CoroutineName("ComponentHandler")) {
      ComponentHandler(progress, component).handle()
    }
  }
  
  private class ComponentHandler(
    private val progress: InfoAndProgressPanel.MyProgressComponent,
    private val component: Component
  ) {
    suspend fun handle() {
      coroutineScope {
        val ui = UISettings.getInstance()
        val visibilityDataFlow = MutableStateFlow(VisibilityData(
          showInPresentationMode = progress.showInPresentationMode.value,
          presentationMode = ui.presentationMode,
          showStatusBar = ui.showStatusBar,
          showProgressWithoutStatusBar = Registry.`is`("ide.show.progress.without.status.bar"),
        ))
        launch(CoroutineName("UI settings listener")) {
          ApplicationManager.getApplication().messageBus.connect(this)
            .subscribe(topic = UISettingsListener.TOPIC, handler = UISettingsListener { ui ->
              visibilityDataFlow.update { previousValue ->
                previousValue.copy(
                  presentationMode = ui.presentationMode,
                  showStatusBar = ui.showStatusBar,
                )
              }
            })
          awaitCancellation() // keep the listener alive
        }
        launch(CoroutineName("Registry listener")) {
          RegistryManager.getInstance().get("ide.show.progress.without.status.bar").addListener(
            listener = object : RegistryValueListener {
              override fun afterValueChanged(value: RegistryValue) {
                visibilityDataFlow.update { previousValue ->
                  previousValue.copy(
                    showProgressWithoutStatusBar = value.asBoolean()
                  )
                }
              }
            },
            coroutineScope = this
          )
          awaitCancellation() // keep the listener alive
        }
        launch(CoroutineName("showInPresentationMode listener")) {
          progress.showInPresentationMode.collect { showInPresentationMode ->
            visibilityDataFlow.update { previousValue ->
              previousValue.copy(
                showInPresentationMode = showInPresentationMode,
              )
            }
          }
        }
        launch(CoroutineName("visibility applier")) {
          visibilityDataFlow.debounce(100.milliseconds).collect { data ->
            updateVisibility(data.isVisible)
          }
        }
      }
    }

    private fun updateVisibility(isVisible: Boolean) {
      // We need to hide the entire balloon, so it doesn't get in the way, intercept mouse clicks, etc.
      // But balloons have special layout logic, so we need some extra steps to revalidate everything.
      val balloonComponent = ComponentUtil.findParentByCondition(component) { c ->
        ClientProperty.get(c, Balloon.KEY) is Balloon
      } ?: return
      val balloon = ClientProperty.get(balloonComponent, Balloon.KEY) as Balloon
      component.isVisible = isVisible // We only update this because we initially make it invisible for flicker protection.
      balloonComponent.isVisible = isVisible // Cover the entire thing (no invisible transparent panel, no click stealing).
      component.revalidate() // This is the one that will actually update its preferred size.
      balloon.revalidate() // This will actually (re)position and (re)size the balloon itself.
    }

    private data class VisibilityData(
      val showInPresentationMode: Boolean,
      val presentationMode: Boolean,
      val showStatusBar: Boolean,
      val showProgressWithoutStatusBar: Boolean,
    ) {
      val isVisible: Boolean
        get() {
          return showInPresentationMode && (presentationMode || !showStatusBar && showProgressWithoutStatusBar)
        }
    }
  }
}
