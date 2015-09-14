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
package com.jetbrains.reactiveidea.layout

import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.reactiveidea.EditorHost
import com.jetbrains.reactiveidea.ProjectHost
import com.jetbrains.reactivemodel.*
import com.jetbrains.reactivemodel.models.ListModel
import com.jetbrains.reactivemodel.models.MapModel
import com.jetbrains.reactivemodel.models.PrimitiveModel
import com.jetbrains.reactivemodel.util.Lifetime
import com.jetbrains.reactivemodel.util.host

/**
 * Editor pool
 */
class EditorPoolHost(val reactiveModel: ReactiveModel,
                     val path: Path,
                     val lifetime: Lifetime,
                     init: Initializer,
                     val counterPath: Path) : Host {
  companion object {
    fun getInModel(model: MapModel): EditorPoolHost = editorsTag.getIn(model).first().meta.host<EditorPoolHost>()
  }

  override val tags: Array<String>
    get() = arrayOf("editors")

  fun addEditor(editor: TextEditor, file: VirtualFile, active: Boolean = true, forSplitter: Boolean = false) {
    val idx = nextIdx()
    reactiveModel.host(path / "values" / idx) { path, lifetime, init ->
      val host = EditorHost(reactiveModel, path, lifetime, file, editor, init, forSplitter)
      if (active) {
        init += { ComponentHost.getInModel(it).setActive(it, path) }
      }
      init += { it.putIn(counterPath, PrimitiveModel(idx.toInt() + 1)) }
      host
    }
  }

  // todo. we should rewrite transactions to avoid getting current model
  private fun nextIdx(): String = (ReactiveModel.current()!!.root.getIn(counterPath) as PrimitiveModel<*>).value.toString()
}
