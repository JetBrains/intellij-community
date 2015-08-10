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

import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.TextEditorLocation
import com.intellij.pom.Navigatable
import com.intellij.ui.SimpleTextAttributes
import com.intellij.usageView.UsageTreeColors
import com.intellij.usageView.UsageTreeColorsScheme
import com.intellij.usages.TextChunk
import com.intellij.usages.UsageViewPresentation
import com.intellij.usages.impl.*
import com.jetbrains.reactiveidea.mapping.UsageViewPresentationBean
import com.jetbrains.reactivemodel.*
import com.jetbrains.reactivemodel.mapping.KDM
import com.jetbrains.reactivemodel.mapping.model.ModelBean
import com.jetbrains.reactivemodel.models.ListModel
import com.jetbrains.reactivemodel.models.MapModel
import com.jetbrains.reactivemodel.models.PrimitiveModel
import com.jetbrains.reactivemodel.util.Lifetime
import com.jetbrains.reactivemodel.util.createMeta
import com.jetbrains.reactivemodel.util.emptyMeta
import java.awt.Color
import java.awt.Font
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

    val ourColorsScheme = UsageTreeColorsScheme.getInstance().getScheme();
    val ourNumberOfUsagesAttribute = SimpleTextAttributes.fromTextAttributes(ourColorsScheme.getAttributes(UsageTreeColors.NUMBER_OF_USAGES));
  }

  override val tags: Array<String>
    get() = arrayOf("usages")

  class UsageHost(val node: DefaultMutableTreeNode) : Host {}

  init {
    init += { m ->
      reaction(false, "tree reaction", usageView.usagesSignal) { usages ->
        reactiveModel.transaction { model ->
          model.putIn(path / tree, MapModel(hashMapOf("0" to convertTree(usageView.root), tagsField to tagsModel("tree"))))
        }
      }
      m.putIn(path / presentation, KDM.map<ModelBean>(usageView.getPresentation()).toModel())
          .putIn(path / name, PrimitiveModel(usageView.getPresentation().getTabName()))
    }

    lifetime += {
      usageView.lifetime.terminate()
    }
  }

  private fun convertTree(node: DefaultMutableTreeNode): MapModel {
    val value = run {
      val text = node.text(usageView)
      if (text.isNotEmpty()) text else " "
    }
    val meta = if (node.getUserObject() is Navigatable) createMeta("host", UsageHost(node)) else emptyMeta()
    val children = (node.children() as Enumeration<*>).asSequence()
        .mapIndexed { i, child ->
          i.toString() to convertTree(child as DefaultMutableTreeNode)
        }.toMap({ it.first }, { it.second })

    val res = hashMapOf(
        "text" to PrimitiveModel(value),
        "children" to MapModel(children))
    if (node is UsageNode) {
      res[tagsField] = tagsModel("usage")
      val chunks = node.getUsage().getPresentation().getText()
      res["chunks"] = KDM.toModel(arrayListOf(*chunks))

    } else if (node is GroupNode) {
      res[tagsField] = tagsModel("usage-group")
      res["count"] = PrimitiveModel(node.getRecursiveUsageCount())
      if (node.isRoot()) {
        val textAttr = UsageViewTreeCellRenderer.patchAttrs(node, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES).toTextAttributes()
        res["attr"] = KDM.toModel(textAttr)
      }
      val usNumAttr = UsageViewTreeCellRenderer.patchAttrs(node, ourNumberOfUsagesAttribute).toTextAttributes()
      res["usage-num-attr"] = KDM.toModel(usNumAttr)
    } else if (node is UsageTargetNode) {
      res[tagsField] = tagsModel("usage-target")
    }
    return MapModel(res, meta)
  }
}