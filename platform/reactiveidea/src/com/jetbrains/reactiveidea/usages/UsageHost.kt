/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.reactiveidea.usages

import com.intellij.usages.Usage
import com.intellij.usages.UsagePresentation
import com.intellij.usages.UsageView
import com.intellij.usages.UsageViewPresentation
import com.jetbrains.reactivemodel.*
import com.jetbrains.reactivemodel.models.ListModel
import com.jetbrains.reactivemodel.models.MapModel
import com.jetbrains.reactivemodel.models.PrimitiveModel
import com.jetbrains.reactivemodel.util.Lifetime
import java.util.HashMap

public class UsageHost(val reactiveModel: ReactiveModel,
                       val path: Path,
                       val lifetime: Lifetime,
                       val usageView: UsageView,
                       init: Initializer) : Host {

  companion object {
    val presentation = "presentation"
    val usages = "usages"
  }

  override val tags: Array<String>
    get() = arrayOf("usages")

  init {
    init += { m ->
      m.putIn(path / presentation, convertPresentation(usageView.getPresentation()))
          .putIn(path / usages, convertUsages(usageView.getUsages()))
    }
  }

  private fun convertUsages(usages: Set<Usage>): ListModel {
    return ListModel(usages.map {
      convertUsage(it)
    }.toArrayList())
  }

  private fun convertUsage(usage: Usage): Model {
    return MapModel(hashMapOf(
        "valid" to PrimitiveModel(usage.isValid()),
        "readOnly" to PrimitiveModel(usage.isReadOnly()),
        "presentation" to convertUsagePresentation(usage.getPresentation())
    ))
  }

  private fun convertUsagePresentation(presentation: UsagePresentation): Model {
    return toModel(hashMapOf(
        "plainText" to presentation.getPlainText(),
        "tooltipText" to presentation.getTooltipText()
    ))
  }

  private fun convertPresentation(presentation: UsageViewPresentation): MapModel {
    return toModel(hashMapOf(
        "tabText" to presentation.getTabText(),
        "scopeText" to presentation.getScopeText(),
        "contextText" to presentation.getContextText(),
        "usagesString" to presentation.getUsagesString(),
        "targetNodeText" to presentation.getTargetsNodeText(),
        "nonCodeUsagesString" to presentation.getNonCodeUsagesString(),
        "usagesInGeneratedCodeString" to presentation.getUsagesInGeneratedCodeString(),
        "showReadOnlyStatusAsRed" to presentation.isShowReadOnlyStatusAsRed(),
        "showCancelButton" to presentation.isShowCancelButton(),
        "openInNewTab" to presentation.isOpenInNewTab(),
        "codeUsages" to presentation.isCodeUsages(),
        "usageTypeFilteringAvailable" to presentation.isUsageTypeFilteringAvailable(),
        "usagesWord" to presentation.getUsagesWord(),
        "tabName" to presentation.getTabName(),
        "toolwindowTitle" to presentation.getToolwindowTitle(),
        "dynamicCodeUsagesString" to presentation.getDynamicCodeUsagesString(),
        "mergeDupLinesAvailable" to presentation.isMergeDupLinesAvailable()
    ))
  }

  private fun toModel(hmap: HashMap<String, Any>): MapModel {
    val map = HashMap<String, Model>()
    hmap.filter { it.value != null }
        .mapValuesTo(map) { e ->
          PrimitiveModel(e.value)
        }
    return MapModel(map)
  }
}