// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.validator.rules.impl

import com.intellij.internal.statistic.utils.PluginInfo
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.extensions.ExtensionPointName
import com.jetbrains.fus.reporting.api.IEventContext
import com.jetbrains.fus.reporting.api.ValidationResultType

open class ExtensionIdValidationRule<T : Any>(
  private val epName: ExtensionPointName<T>,
  private val idGetter: (T) -> String
): CustomValidationRule() {
  override fun getRuleId(): String = "extension." + epName.name

  protected open val extensions: Iterable<T>
    get() = epName.extensionList

  override fun doValidate(data: String, context: IEventContext): ValidationResultType {
    val extension = extensions.find { idGetter.invoke(it) == data } ?: return ValidationResultType.REJECTED
    val info: PluginInfo = getPluginInfo(extension.javaClass)
    return if (info.isSafeToReport()) ValidationResultType.ACCEPTED else ValidationResultType.THIRD_PARTY
  }
}
