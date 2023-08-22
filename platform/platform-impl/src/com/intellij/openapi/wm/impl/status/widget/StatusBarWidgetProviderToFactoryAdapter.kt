// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status.widget

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import java.util.*

internal class StatusBarWidgetProviderToFactoryAdapter(
  private val project: Project,
  private val frame: IdeFrame,
  @Suppress("DEPRECATION", "removal") @JvmField val provider: com.intellij.openapi.wm.StatusBarWidgetProvider,
) : StatusBarWidgetFactory {
  private var widgetWasCreated = false
  private var myWidget: StatusBarWidget? = null

  override fun getId(): String {
    return widget?.ID() ?: provider.javaClass.getName()
  }

  @Suppress("HardCodedStringLiteral", "DialogTitleCapitalization")
  override fun getDisplayName(): String {
    val widget = widget ?: return ""
    val result = widget.getPresentation()?.getTooltipText()
    if (!result.isNullOrEmpty()) {
      return result
    }
    return if (ApplicationManager.getApplication().isInternal()) widget.ID() else ""
  }

  override fun isAvailable(project: Project): Boolean {
    @Suppress("removal")
    return provider.isCompatibleWith(frame) && widget != null
  }

  override fun isConfigurable(): Boolean = !getDisplayName().isEmpty()

  override fun createWidget(project: Project) = widget!!

  @Suppress("removal")
  private val widget: StatusBarWidget?
    get() {
      if (!widgetWasCreated) {
        myWidget = provider.getWidget(project)
        widgetWasCreated = true
      }
      return myWidget
    }

  override fun disposeWidget(widget: StatusBarWidget) {
    myWidget = null
    widgetWasCreated = false
    Disposer.dispose(widget)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || javaClass != other.javaClass) return false
    val adapter = other as StatusBarWidgetProviderToFactoryAdapter
    return provider == adapter.provider && project == adapter.project
  }

  override fun hashCode(): Int = Objects.hash(provider, project)
}
