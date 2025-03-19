// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.rdct

import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.events.PrimitiveEventField
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class ItemsList(override val name: String, private val knownValuesList: List<String>) : PrimitiveEventField<String?>() {
  override fun addData(fuData: FeatureUsageData, value: String?) {
    val data =
      if (value.isNullOrEmpty()) "unknown"
      else if (knownValuesList.contains(value)) value
      else "unknown"

    fuData.addData(name, data)
  }

  override val validationRule: List<String> =
    listOf("{enum:|unknown|${knownValuesList.joinToString("|")}}")
}

@Internal
@JvmField
val HostProductCode: ItemsList = ItemsList("parentProductCode", listOf("IU", "RM", "WS", "PS", "PY", "DS", "OC", "CL", "DB", "RD", "GO"))

