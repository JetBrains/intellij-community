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

import com.intellij.pom.Navigatable
import com.intellij.usages.UsageViewPresentation
import com.intellij.usages.impl.ServerUsageView
import com.intellij.usages.impl.UsageNode
import com.intellij.usages.impl.text
import com.jetbrains.reactivemodel.*
import com.jetbrains.reactivemodel.models.ListModel
import com.jetbrains.reactivemodel.models.MapModel
import com.jetbrains.reactivemodel.models.PrimitiveModel
import com.jetbrains.reactivemodel.util.Lifetime
import com.jetbrains.reactivemodel.util.createMeta
import com.jetbrains.reactivemodel.util.emptyMeta
import java.util.Enumeration
import java.util.HashMap
import javax.swing.tree.DefaultMutableTreeNode

public class UsagesHost(val reactiveModel: ReactiveModel,
                        val path: Path,
                        val lifetime: Lifetime,
                        val usageView: ServerUsageView,
                        init: Initializer) : Host {

  companion object {
    val presentation = "presentation"
    val usages = "usages"
    val name = "name"
    val targets = "targets"
    val tree = "tree"
  }

  override val tags: Array<String>
    get() = arrayOf("usages")

  class UsageHost(val node: DefaultMutableTreeNode) : Host {}

  init {
    init += { m ->
      reaction(true, "tree reaction", usageView.usagesSignal) { usages ->
        reactiveModel.transaction { m ->
          m.putIn(path / tree, convertTree(usageView.root))
        }
      }
      m.putIn(path / presentation, convertPresentation(usageView.getPresentation()))
          .putIn(path / name, PrimitiveModel(usageView.getPresentation().getTabName()))
    }
    lifetime += {
      usageView.lifetime.terminate()
    }
  }

  private fun convertTree(node: DefaultMutableTreeNode): Model {
    val text = node.text(usageView)
    val value = if (text.isNotEmpty()) text else " "
    val meta = if (node is Navigatable) createMeta("host", UsageHost(node)) else emptyMeta()
    val childs = (node.children() as Enumeration<*>).asSequence()
        .map { child ->
          convertTree(child as DefaultMutableTreeNode)
        }.toArrayList()
    return if (childs.isEmpty()) {
      PrimitiveModel(value, meta)
    } else {
      MapModel(hashMapOf(value to ListModel(childs)), meta)
    }
  }

  private fun convertPresentation(presentation: UsageViewPresentation): MapModel {
    return toModel(hashMapOf(
        "tabText" to presentation.getTabText(),
        "scopeText" to presentation.getScopeText(),
        "contextText" to presentation.getContextText(),
        "usagesString" to presentation.getUsagesString(),
        "targetNodeText" to presentation.getTargetsNodeText(),
        "nonCodeUsagesString" to presentation.getNonCodeUsagesString(),
        "codeUsagesString" to presentation.getCodeUsagesString(),
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