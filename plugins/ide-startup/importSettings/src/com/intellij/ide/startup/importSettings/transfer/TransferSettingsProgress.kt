// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.transfer

import com.intellij.icons.AllIcons
import com.intellij.ide.startup.importSettings.data.*
import com.intellij.ide.startup.importSettings.jb.JbProductInfo
import com.intellij.ide.startup.importSettings.jb.NameMappings
import com.intellij.ide.startup.importSettings.models.BaseIdeVersion
import com.intellij.ide.startup.importSettings.models.IdeVersion
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.NlsContexts.ProgressDetails
import com.intellij.openapi.util.NlsContexts.ProgressText
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.OptProperty
import com.jetbrains.rd.util.reactive.Property
import com.jetbrains.rd.util.reactive.compose

class TransferSettingsProgress(override val from: DialogImportItem) : ImportFromProduct {

  constructor(sourceIdeVersion: IdeVersion) : this(DialogImportItem(
      TransferSettingsContributor(sourceIdeVersion),
      sourceIdeVersion.transferableId.icon(IconProductSize.LARGE) ?: AllIcons.Actions.Stub
    ))
  constructor(productInfo: JbProductInfo)  : this(DialogImportItem(
    productInfo, NameMappings.getIcon(productInfo.codeName, IconProductSize.LARGE) ?: AllIcons.Actions.Stub
  ))


  override val message = null
  override val progress = TransferSettingsProgressIndicator()

  override val to = DialogImportItem.self()

  fun createProgressIndicatorAdapter(): ProgressIndicator = ProgressIndicatorAdapter(progress)
}

class TransferSettingsProgressIndicator : ImportProgress {

  override val progressMessage = Property<String?>(null)
  override val progress = OptProperty<Int>()
}

private class TransferSettingsContributor(ideVersion: BaseIdeVersion) : SettingsContributor {

  override val id = ideVersion.id
  override val name = ideVersion.name
}

class ProgressIndicatorAdapter(private val backend: TransferSettingsProgressIndicator) : ProgressIndicator {

  @Volatile
  private var cancelled = false

  override fun start() {}
  override fun stop() {}
  override fun isRunning() = true
  override fun cancel() { cancelled = true }
  override fun isCanceled() = cancelled

  private val textProp = Property<@ProgressText String?>(null)
  private val text2Prop = Property<@ProgressText String?>(null)
  init {
    textProp.compose(text2Prop, ::Pair).advise(Lifetime.Eternal) { (t1, t2) ->
      val text = when {
        t1 == null && t2 == null -> null
        t1 == null && t2 != null -> t2
        t1 != null && t2 == null -> t1
        else -> "$t1 / $t2"
      }
      backend.progressMessage.set(text)
    }
  }

  override fun getText(): @ProgressText String? = textProp.value
  override fun setText(text: @ProgressText String?) {
    textProp.value = text
  }


  override fun getText2(): @ProgressDetails String? = text2Prop.value
  override fun setText2(text: @ProgressDetails String?) {
    text2Prop.value = text
  }

  override fun getFraction(): Double {
    val percent = backend.progress.valueOrNull ?: return 0.0
    return percent / 100.0
  }

  override fun setFraction(fraction: Double) {
    val percent = (fraction * 100.0).toInt()
    backend.progress.set(percent)
  }

  override fun pushState() {}
  override fun popState() {}

  private val modalityState = ModalityState.current()
  override fun isModal() = true
  override fun getModalityState() = modalityState

  override fun setModalityProgress(modalityProgress: ProgressIndicator?) {}
  override fun isIndeterminate() = false
  override fun setIndeterminate(indeterminate: Boolean) {}
  override fun checkCanceled() {
    if (cancelled)
      throw ProcessCanceledException()
  }
  override fun isPopupWasShown() = true
  override fun isShowing() = true
}