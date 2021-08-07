// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("TargetPopup")

package com.intellij.ui.list

import com.intellij.ide.ui.UISettings
import com.intellij.navigation.TargetPresentation
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.ui.popup.IPopupChooserBuilder
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.NlsContexts.PopupTitle
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.util.function.Consumer
import java.util.function.Function
import javax.swing.ListCellRenderer

@RequiresEdt
fun <T> createTargetPopup(
  @PopupTitle title: String,
  items: List<T>,
  presentationProvider: Function<in T, out TargetPresentation>,
  processor: Consumer<in T>
): JBPopup {
  return buildTargetPopup(items, presentationProvider, processor)
    .setTitle(title)
    .createPopup()
}

@RequiresEdt
fun <T> buildTargetPopup(
  items: List<T>,
  presentationProvider: Function<in T, out TargetPresentation>,
  processor: Consumer<in T>
): IPopupChooserBuilder<T> {
  require(items.isNotEmpty()) {
    "Attempted to show a navigation popup with zero elements"
  }
  return JBPopupFactory.getInstance()
    .createPopupChooserBuilder(items)
    .setRenderer(createTargetPresentationRenderer(presentationProvider))
    .setFont(EditorUtil.getEditorFont())
    .withHintUpdateSupply()
    .setNamerForFiltering { item: T ->
      presentationProvider.apply(item).speedSearchText()
    }.setItemChosenCallback(processor::accept)
}

fun <T> createTargetPresentationRenderer(presentationProvider: Function<in T, out TargetPresentation>): ListCellRenderer<T> {
  return if (UISettings.instance.showIconInQuickNavigation) {
    TargetPresentationRenderer(presentationProvider)
  }
  else {
    TargetPresentationMainRenderer(presentationProvider)
  }
}

private fun TargetPresentation.speedSearchText(): String {
  val presentableText = presentableText
  val containerText = containerText
  return if (containerText == null) presentableText else "$presentableText $containerText"
}
