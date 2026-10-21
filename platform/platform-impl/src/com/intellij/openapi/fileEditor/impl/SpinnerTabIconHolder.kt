// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.DeferredIconImpl
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.TabInfoIconHolder
import com.intellij.util.ui.EmptyIcon
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import javax.swing.Icon
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
internal class SpinnerTabIconHolder(
  composite: EditorComposite,
  private val owner: TabInfo,
  startupIcon: Icon? = null,
  waitForEditors: Boolean = true,
) : TabInfoIconHolder {
  private val delayFromRegistry = Registry.intValue("editor.loading.spinner.delay.ms", 0).milliseconds
  private val isStaticSpinner = Registry.`is`("editor.loading.spinner.static")

  private var icon: Icon?

  init {
    val template = AllIcons.FileTypes.Text
    icon = startupIcon ?: EmptyIcon.create(template.iconWidth, template.iconHeight)
  }

  private val currentIcon = MutableStateFlow(icon)

  init {
    composite.coroutineScope.launch(CoroutineName("EditorComposite(file=${composite.file.name}.iconLoading") + ModalityState.any()
      .asContextElement()) out@{
      if (!waitForEditors) {
        startIconProcessing()
        return@out
      }
      val loadingSpinnerWaiting = launch {
        if (startupIcon == null && !isStaticSpinner) {
          delay(delayFromRegistry)
        }
      }
      val iconProcessingWaiting = launch { composite.waitForAvailableWithoutTriggeringInit() }
      select {
        loadingSpinnerWaiting.onJoin {
          setLoadingSpinner(startupIcon)
        }
        iconProcessingWaiting.onJoin {
          loadingSpinnerWaiting.cancel()
        }
      }
      iconProcessingWaiting.join() // One more join to wait for loading if the spinner has been selected
      startIconProcessing()
    }
  }

  private suspend fun setIconImmediately(icon: Icon?) {
    val oldIcon = this@SpinnerTabIconHolder.icon
    withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      if (oldIcon != icon) {
        this@SpinnerTabIconHolder.icon = icon
        owner.changeSupport.firePropertyChange(TabInfo.ICON, oldIcon, icon)
      }
    }
  }

  private suspend fun setLoadingSpinner(placeholder: Icon?) {
    val iconToSet = when {
        placeholder != null -> placeholder
        isStaticSpinner -> AllIcons.Ide.GrayDot
        else -> AnimatedIcon.Default.INSTANCE
    }
    setIconImmediately(iconToSet)
  }

  private suspend fun startIconProcessing() {
    currentIcon.collectLatest {
      if (it is DeferredIconImpl<*>) {
        it.awaitEvaluation()
      }
      setIconImmediately(it)
    }
  }

  override fun setIcon(icon: Icon?) {
    currentIcon.value = icon
  }

  override fun getIcon(): Icon? {
    return icon
  }
}
