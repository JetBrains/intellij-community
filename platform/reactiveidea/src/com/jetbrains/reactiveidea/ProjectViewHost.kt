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
import com.intellij.ide.projectView.impl.AbstractProjectViewPSIPane
import com.intellij.ide.projectView.impl.GroupByTypeComparator
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.util.treeView.AbstractTreeStructure
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.jetbrains.reactivemodel.Model
import com.jetbrains.reactivemodel.Path
import com.jetbrains.reactivemodel.ReactiveModel
import com.jetbrains.reactivemodel.models.MapModel
import com.jetbrains.reactivemodel.models.PrimitiveModel
import com.jetbrains.reactivemodel.putIn
import com.jetbrains.reactivemodel.signals.reaction
import com.jetbrains.reactivemodel.util.Lifetime
import java.util.HashMap


public class ProjectViewHost(val project: Project, val projectView: ProjectView?, val lifetime: Lifetime,
                             val reactiveModel: ReactiveModel, val path: Path,
                             val treeStructure: AbstractTreeStructure, val viewPane: AbstractProjectViewPSIPane) {

  class PsiDirNode(val path: Path, val descriptor: AbstractTreeNode<*>, val index: Int)

  val paneId = viewPane.getId()
  val comp = GroupByTypeComparator(projectView, paneId)
  val openDirs = HashMap<SmartPsiElementPointer<PsiDirectory>, PsiDirNode>()
  val ptrManager = SmartPointerManager.getInstance(project)


  init {
    reactiveModel.transaction { m ->
      val root = treeStructure.getRootElement();
      val descriptor = treeStructure.createDescriptor(root, null) as AbstractTreeNode<*>
      val current = path / paneId
      val rootNode = createNode(descriptor, current, 0)
      current.putIn(m, MapModel(mapOf(root.toString() to rootNode)))
    }

    val psiTreeChangeListener = object : PsiTreeChangeAdapter() {
      override fun childAdded(event: PsiTreeChangeEvent) {
        handle(event)
      }

      override fun childReplaced(event: PsiTreeChangeEvent) {
        handle(event)
      }

      override fun childMoved(event: PsiTreeChangeEvent) {
        handle(event)
      }

      override fun childRemoved(event: PsiTreeChangeEvent) {
        handle(event)
      }

      override fun propertyChanged(event: PsiTreeChangeEvent) {
        handle(event)
      }

      fun handle(event: PsiTreeChangeEvent) {
        var parent = event.getParent()
        if(parent !is PsiDirectory) {
          parent = event.getFile()?.getParent()
        }
        if (parent is PsiDirectory) {
          updateDir(parent)
        }
      }

      fun updateDir(dir: PsiDirectory) {
        val dirNode = openDirs[ptrManager.createSmartPsiElementPointer(dir)]
        if (dirNode != null) {
          updateChilds(dirNode.descriptor, dirNode.path, dirNode.index)
        }
      }
    }
    PsiManager.getInstance(project).addPsiTreeChangeListener(psiTreeChangeListener)
  }

  private fun createNode(descriptor: AbstractTreeNode<*>, path: Path, index: Int): Model {
    descriptor.update();
    val map = HashMap<String, Model>()
    val value = descriptor.getValue()
    val state = if (value is PsiFileSystemItem && value.isDirectory() || descriptor.getChildren().isNotEmpty()) "closed" else "leaf"
    map.put("state", PrimitiveModel(state))
    map.put("order", PrimitiveModel(index))
    map.put("childs", MapModel())
    val stateSignal = reactiveModel.subscribe(lifetime, path / descriptor.toString())

    reaction(true, "update state of project tree node", stateSignal) { state ->
      if (state != null) {
        state as MapModel
        val openState = (state["state"] as PrimitiveModel<*>).value
        if (openState == "open" && (state["childs"] as MapModel).isEmpty()) {
          updateChilds(descriptor, path, index)
        } else if (openState == "closed") {
          val descrValue = descriptor.getValue()
          if (descrValue is PsiDirectory) {
              openDirs.remove(ptrManager.createSmartPsiElementPointer(descrValue))
            }
        }
      }
    }
    return MapModel(map)
  }

  private fun updateChilds(descriptor: AbstractTreeNode<*>, path: Path, index: Int) {
    reactiveModel.transaction { m ->
      descriptor.update();
      if (descriptor.getValue() is PsiDirectory) {
        val ptr = ptrManager.createSmartPsiElementPointer(descriptor.getValue() as PsiDirectory)
        openDirs[ptr] = PsiDirNode(path, descriptor, index);
      }
      val children = HashMap<String, Model>()
      val nodesPath = path / descriptor.toString() / "childs"
      treeStructure.getChildElements(descriptor)
          .map { child -> treeStructure.createDescriptor(child, null) as AbstractTreeNode<*> }
          .filter { descr -> descr.update(); true }
          .sortBy(comp)
          .forEachIndexed { idx, descr ->
            var name = descr.toString()
            children[name] = createNode(descr, nodesPath, idx)
          }
      nodesPath.putIn(m, MapModel(children))
    }
  }
}
