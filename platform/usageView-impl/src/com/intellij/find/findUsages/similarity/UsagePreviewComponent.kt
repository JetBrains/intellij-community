// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.findUsages.similarity

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Iconable
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.usageView.UsageInfo
import com.intellij.usages.UsageView
import com.intellij.usages.similarity.statistics.SimilarUsagesCollector
import com.intellij.util.IconUtil
import com.intellij.util.PsiNavigateUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.FlowLayout
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.JPanel

internal class UsagePreviewComponent private constructor(
  usageView: UsageView,
  private val usageInfo: UsageInfo,
  renderingData: SnippetRenderingData,
  parent: Disposable,
) : JBPanel<JBPanel<*>>(), Disposable, UiDataProvider {
  var header: JPanel
  private var mySnippetComponent: UsageCodeSnippetComponent
  private val myUsageView: UsageView

  init {
    layout = VerticalLayout(0)
    myUsageView = usageView
    header = createHeaderWithLocationLink(usageInfo)
    add(header)
    mySnippetComponent = UsageCodeSnippetComponent(renderingData)
    add(mySnippetComponent)
    if (!Disposer.tryRegister(parent, this)) {
      Disposer.dispose(parent)
    }
  }

  override fun uiDataSnapshot(sink: DataSink) {
    sink.lazy(CommonDataKeys.NAVIGATABLE_ARRAY) {
      val file = usageInfo.virtualFile ?: return@lazy emptyArray()
      val navigationOffset = usageInfo.navigationOffset
      val openFileDescriptor = OpenFileDescriptor(usageInfo.project, file, navigationOffset)
      arrayOf(openFileDescriptor)
    }
  }

  fun renderCluster(usageInfo: UsageInfo, renderingData: SnippetRenderingData) {
    removeAll()
    header = createHeaderWithLocationLink(usageInfo)
    Disposer.dispose(mySnippetComponent)
    mySnippetComponent = UsageCodeSnippetComponent(renderingData)
    add(header)
    add(mySnippetComponent)
  }

  private fun createHeaderWithLocationLink(usageInfo: UsageInfo): JPanel {
    val locationLinkComponent = createNavigationLink(this, myUsageView, usageInfo)
    val header = JPanel()
    header.background = UIUtil.getTextFieldBackground()
    header.layout = FlowLayout(FlowLayout.LEFT)
    if (locationLinkComponent != null) {
      header.add(locationLinkComponent)
    }
    header.border = JBUI.Borders.customLineTop(JBColor(Gray.xCD, Gray.x51))
    return header
  }

  override fun dispose() {
    Disposer.dispose(mySnippetComponent)
  }

  companion object {
    @JvmStatic
    fun create(usageView: UsageView,
               info: UsageInfo,
               renderingData: SnippetRenderingData,
               parent: Disposable): UsagePreviewComponent {
      return UsagePreviewComponent(usageView, info, renderingData, parent)
    }

    fun createNavigationLink(
      sourceComponent: JComponent,
      usageView: UsageView,
      usageInfo: UsageInfo
    ): ActionLink? {
      val file = usageInfo.virtualFile ?: return null
      val actionLink = ActionLink(file.name, object : AbstractAction() {
        override fun actionPerformed(e: ActionEvent) {
          SimilarUsagesCollector.logNavigateToUsageClicked(usageInfo.project, sourceComponent.javaClass, usageView)
          PsiNavigateUtil.navigate(usageInfo.element)
        }
      })
      actionLink.background = UIUtil.TRANSPARENT_COLOR
      actionLink.isOpaque = false
      actionLink.icon = IconUtil.getIcon(file, Iconable.ICON_FLAG_READ_STATUS, usageInfo.project)
      return actionLink
    }
  }
}