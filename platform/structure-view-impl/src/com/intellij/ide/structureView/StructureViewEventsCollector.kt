// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView

import com.intellij.ide.structureView.impl.StructureViewComposite.StructureViewDescriptor
import com.intellij.ide.structureView.logical.StructureViewTab
import com.intellij.ide.structureView.logical.impl.LogicalStructureViewModel
import com.intellij.ide.structureView.logical.impl.LogicalStructureViewTreeElement
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object StructureViewEventsCollector: CounterUsagesCollector() {
  private val GROUP = EventLogGroup("structure.view", 1)
  override fun getGroup(): EventLogGroup = GROUP

  private val TAB = EventFields.Enum<StructureViewTab>("tab")
  private val MODEL_CLASS = EventFields.Class("model_class")


  private val BUILD_STRUCTURE = GROUP.registerEvent(
    "toolwindow.shown",
    TAB, MODEL_CLASS,
    "Toolwindow is opened, first time or after changing a file"
  )
  private val TAB_SELECTED = GROUP.registerEvent(
    "tab.selected",
    TAB, MODEL_CLASS,
    "User selected another tab"
  )
  private val NAVIGATE = GROUP.registerEvent(
    "navigate",
    MODEL_CLASS,
    "Navigate to psiElement"
  )
  private val CUSTOM_CLICK_HANDLED = GROUP.registerEvent(
    "custom.click.handled",
    MODEL_CLASS,
    "Click event was handled by custom handler"
  )

  fun logBuildStructure(viewDescriptor: StructureViewDescriptor) {
    val tab = viewDescriptor.title?.let { StructureViewTab.ofTitle(it) } ?: return
    ReadAction
      .nonBlocking<Class<*>?> {
        getModelClass(viewDescriptor)
      }
      .finishOnUiThread(ModalityState.any()) {
        BUILD_STRUCTURE.log(tab, it)
      }.submit(AppExecutorUtil.getAppScheduledExecutorService())
  }

  fun logTabSelected(viewDescriptor: StructureViewDescriptor) {
    val tab = viewDescriptor.title?.let { StructureViewTab.ofTitle(it) } ?: return
    ReadAction
      .nonBlocking<Class<*>?> {
        getModelClass(viewDescriptor)
      }
      .finishOnUiThread(ModalityState.any()) {
        TAB_SELECTED.log(tab, it)
      }.submit(AppExecutorUtil.getAppScheduledExecutorService())
  }

  fun logNavigate(modelClass: Class<*>) {
    NAVIGATE.log(modelClass)
  }

  fun logCustomClickHandled(modelClass: Class<*>) {
    CUSTOM_CLICK_HANDLED.log(modelClass)
  }

  private fun getModelClass(viewDescriptor: StructureViewDescriptor): Class<*>? {
    var model: Any = viewDescriptor.structureModel
    if (model is LogicalStructureViewModel) {
      val root = model.root
      if (root is LogicalStructureViewTreeElement<*>) {
        var assembledModel = root.getLogicalAssembledModel()
        val children = assembledModel.getChildren()
        if (children.size == 1)
          return children[0].model?.javaClass
        else
          return assembledModel.model?.javaClass
      }
      else {
        return root::class.java
      }
    }
    return null
  }

}