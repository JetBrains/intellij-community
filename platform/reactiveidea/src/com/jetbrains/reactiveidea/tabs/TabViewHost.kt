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
package com.jetbrains.reactiveidea.tabs

import com.intellij.find.FindManager
import com.intellij.find.impl.FindManagerImpl
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.usages.UsageView
import com.intellij.usages.UsageViewManager
import com.intellij.usages.impl.ServerUsageView
import com.intellij.util.ui.UIUtil
import com.jetbrains.reactiveidea.EditorHost
import com.jetbrains.reactiveidea.usages.UsagesHost
import com.jetbrains.reactivemodel.*
import com.jetbrains.reactivemodel.models.AbsentModel
import com.jetbrains.reactivemodel.models.MapModel
import com.jetbrains.reactivemodel.models.PrimitiveModel
import com.jetbrains.reactivemodel.util.Lifetime
import gnu.trove.TObjectIntHashMap

class TabViewHost(val project: Project,
                  val reactiveModel: ReactiveModel,
                  val path: Path,
                  val lifetime: Lifetime,
                  init: Initializer) : Host {

  companion object {
    val tabPath = "tabs"
    val activePath = "active"
  }

  var currentIdx = 0

  init {
    val activeSignal = reactiveModel.subscribe(lifetime, path / activePath)
    reaction(true, "editor active state", activeSignal) { m ->
      if (m is PrimitiveModel<*>) {
        val activeIdx = m.value as String
        reactiveModel.transaction { m ->
          val tabs = m.getIn(path / tabPath) as? MapModel
          var model = m
          if (tabs is MapModel) {
            tabs.entrySet().forEach { e ->
              val value = e.value
              if (value is MapModel && value.hmap.isNotEmpty()) {
                model = setEditorActive(model, activeIdx, activeIdx == e.key)
              }
            }
          }
          model
        }
      }
      m
    }

    (FindManager.getInstance(project) as FindManagerImpl).getFindUsagesManager().setListener(object : UsageViewManager.UsageViewStateListener {
      override fun findingUsagesFinished(usageView: UsageView) {
      }

      override fun usageViewCreated(usageView: UsageView) {
        UIUtil.invokeLaterIfNeeded {
          val idx = nextIdx()
          reactiveModel.host(path / tabPath / idx) { path, lifetime, init ->
            val host = UsagesHost(reactiveModel, path, lifetime, usageView as ServerUsageView, init)
            init += {
              setActiveTab(it, idx)
            }
            host
          }
        }
      }
    })
  }

  fun addEditor(editor: TextEditor, file: VirtualFile, active: Boolean = false) {
    val idx = nextIdx()
    reactiveModel.host(path / tabPath / idx) { path, lifetime, init ->
      val host = EditorHost(reactiveModel, path, lifetime, file, editor, init)
      ensureActiveExists(idx, init)

      init += { m ->
        if (active) {
          setActiveTab(m, idx)
        } else m
      }

      host
    }
  }

  private fun ensureActiveExists(editorIdx: String, init: Initializer) {
    init += { m ->
      if (getActive(m) == null) setEditorActive(m, editorIdx)
      else m
    }
  }

  private fun getActive(m: MapModel): String? {
    return (m.getIn(path / activePath) as? PrimitiveModel<*>)?.value as? String
  }

  public fun setActiveTab(m: MapModel, idx: String): MapModel {
    if (m.getIn(path / tabPath / idx) == null) {
      throw AssertionError("Editor $idx not exists")
    }
    return m.putIn(path / activePath, PrimitiveModel(idx))
  }

  private fun nextIdx() = (currentIdx++).toString()

  private fun setEditorActive(m: MapModel, candidate: String, isActive: Boolean = true): MapModel {
    return (path / tabPath / candidate / EditorHost.activePath).putIn(m, PrimitiveModel(isActive))
  }
}
