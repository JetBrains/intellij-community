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
package com.jetbrains.reactiveidea

import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.impl.GroupByTypeComparator
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.util.treeView.AbstractTreeStructure
import com.jetbrains.reactivemodel.Model
import com.jetbrains.reactivemodel.Path
import com.jetbrains.reactivemodel.ReactiveModel
import com.jetbrains.reactivemodel.models.MapModel
import com.jetbrains.reactivemodel.models.PrimitiveModel
import com.jetbrains.reactivemodel.putIn
import com.jetbrains.reactivemodel.signals.reaction
import com.jetbrains.reactivemodel.util.Lifetime
import java.util.HashMap


public class ProjectViewHost(val projectView: ProjectView?, val lifetime: Lifetime, val reactiveModel: ReactiveModel, val path: Path,
                             val treeStructure: AbstractTreeStructure, val id: String) {
  val tags = id
  val comp = GroupByTypeComparator(projectView, id)

  init {
    reactiveModel.transaction { m ->
      val root = treeStructure.getRootElement();
      val descriptor = treeStructure.createDescriptor(root, null) as AbstractTreeNode<*>
      val current = path / tags
      val rootNode = createNode(descriptor, current, 0)
      current.putIn(m, MapModel(mapOf(root.toString() to rootNode)))
    }
  }

  private fun createNode(descriptor: AbstractTreeNode<*>, path: Path, index: Int): Model {
    descriptor.update();
    val map = HashMap<String, Model>()
    val state = if (descriptor.getChildren().isEmpty()) "leaf" else "closed"
    map.put("state", PrimitiveModel(state))
    map.put("order", PrimitiveModel(index))
    map.put("childs", MapModel())
    val stateSignal = reactiveModel.subscribe(lifetime, path / descriptor.toString())

    reaction(true, "update state of project tree node", stateSignal) { state ->
      if (state != null) {
        state as MapModel
        if ((state["state"] as PrimitiveModel<*>).value == "open" && (state["childs"] as MapModel).isEmpty()) {

          reactiveModel.transaction { m ->
            // need open
            descriptor.update();
            val children = HashMap<String, Model>()
            val nodesPath = path / descriptor.toString() / "childs"
            treeStructure.getChildElements(descriptor)
                .map { child -> treeStructure.createDescriptor(child, null) as AbstractTreeNode<*> }
                .sortBy(comp)
                .forEachIndexed { idx, descr ->
                  descr.update()
                  val name = descr.toString()
                  children[name] = createNode(descr, nodesPath, idx)
                }
            nodesPath.putIn(m, MapModel(children))
          }
        }
      }
    }
    return MapModel(map)
  }
}
