// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("TargetPopup")

package com.intellij.ui.list

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.ui.popup.IPopupChooserBuilder
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.util.RoundedCellRenderer
import com.intellij.openapi.util.NlsContexts.PopupTitle
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Predicate
import javax.swing.ListCellRenderer

@RequiresEdt
fun <T> createTargetPopup(
  @PopupTitle title: String,
  items: List<T>,
  presentations: List<TargetPresentation>,
  processor: Consumer<in T>
): JBPopup {
  return createTargetPopup(
    title = title,
    items = items.zip(presentations),
    presentationProvider = { it.second },
    processor = { processor.accept(it.first) }
  )
}

@Internal
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
  require(items.size > 1) {
    "Attempted to build a target popup with ${items.size} elements"
  }
  return buildTargetPopupWithMultiSelect(items, presentationProvider, Predicate { processor.accept(it); return@Predicate false })
}

@RequiresEdt
fun <T> buildTargetPopupWithMultiSelect(
  items: List<T>,
  presentationProvider: Function<in T, out TargetPresentation>,
  predicate: Predicate<in T>
): IPopupChooserBuilder<T> {
  return JBPopupFactory.getInstance()
    .createPopupChooserBuilder(items)
    .setRenderer(RoundedCellRenderer(createTargetPresentationRenderer(presentationProvider)))
    .setFont(EditorUtil.getEditorFont())
    .withHintUpdateSupply()
    .setNamerForFiltering { item: T ->
      presentationProvider.apply(item).speedSearchText()
    }.setItemsChosenCallback { set -> set.all { predicate.test(it) } }
}

@Deprecated("Use GotoTargetRendererNew instead")
fun <T> createTargetPresentationRenderer(presentationProvider: Function<in T, out TargetPresentation>): ListCellRenderer<T> {
  return if (UISettings.getInstance().showIconInQuickNavigation) {
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
