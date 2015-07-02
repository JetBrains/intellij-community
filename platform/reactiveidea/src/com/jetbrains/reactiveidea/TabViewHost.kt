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

import com.github.krukow.clj_lang.PersistentHashMap
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.reactivemodel.Path
import com.jetbrains.reactivemodel.ReactiveModel
import com.jetbrains.reactivemodel.getIn
import com.jetbrains.reactivemodel.models.MapModel
import com.jetbrains.reactivemodel.models.PrimitiveModel
import com.jetbrains.reactivemodel.putIn
import com.jetbrains.reactivemodel.util.Lifetime
import gnu.trove.TObjectIntHashMap
import java.util.*

class TabViewHost(lifetime: Lifetime, reactiveModel: ReactiveModel, path: Path) : MetaHost(lifetime, reactiveModel, path) {
  companion object {
    val editorsPath = "editors"
  }

  init {
    initModel()
  }

  val fileToEditorIdx = TObjectIntHashMap<VirtualFile>()
  var currentIdx = 0

  fun addEditor(editor: Editor, file: VirtualFile) {
    EditorHost(Lifetime.create(lifetime).lifetime, reactiveModel, path / editorsPath / currentIdx.toString(), file.getName(), editor, true)
    fileToEditorIdx.put(file, currentIdx++)
  }

  fun removeEditor(file: VirtualFile) {
    if (fileToEditorIdx.containsKey(file)) {
      val res = fileToEditorIdx.remove(file)
      reactiveModel.transaction { m ->
        val editor = getEditorHost(res, m)
        editor.lifetime.terminate()
        if (isActive(m, res)) {
          val candidate = getActiveEditorCandidate(m, res)
          if (candidate != null) {
            return@transaction setActive(m, candidate)
          }
        }
        m
      }
    }
  }

  private fun getEditorHost(idx: Int, m: MapModel): EditorHost {
    return (path / editorsPath / idx.toString()).getIn(m)!!.meta.valAt("host") as EditorHost
  }

  private fun isActive(m: MapModel, idx: Int): Boolean {
    return ((path / editorsPath / idx.toString() / EditorHost.activePath).getIn(m) as PrimitiveModel<*>).value == true
  }

  private fun setActive(m: MapModel, candidate: String): MapModel {
    return (path / editorsPath / candidate / EditorHost.activePath).putIn(m, PrimitiveModel(true))
  }

  /**
   * Get first convinient editor for set active if current active removed
   */
  private fun getActiveEditorCandidate(m: MapModel, removed: Int): String? {
    return ((path / editorsPath).getIn(m) as MapModel).keySet()
        .firstOrNull { Integer.valueOf(it) != removed }
  }
}
