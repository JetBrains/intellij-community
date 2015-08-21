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
import com.jetbrains.reactivemodel.models.PrimitiveModel
import com.jetbrains.reactivemodel.util.Lifetime

class UsagesPoolHost(val project: Project,
                     val reactiveModel: ReactiveModel,
                     val path: Path,
                     val lifetime: Lifetime,
                     init: Initializer,
                     val counterPath: Path) : Host {

  init {
    val usagesManager = (FindManager.getInstance(project) as FindManagerImpl).getFindUsagesManager()
    usagesManager.setListener(object : UsageViewManager.UsageViewStateListener {
      override fun findingUsagesFinished(usageView: UsageView) {
      }

      override fun usageViewCreated(usageView: UsageView) {
        UIUtil.invokeLaterIfNeeded {
          addUsageView(usageView)
        }
      }
    })
  }

  private fun addUsageView(usageView: UsageView) {
    val idx = nextIdx()
    val usagePath = path / "values" / idx
    reactiveModel.host(usagePath) { path, lifetime, init ->
      val host = UsagesHost(reactiveModel, path, lifetime, usageView as ServerUsageView, init)
      init += {
        it.putIn(counterPath, PrimitiveModel(idx.toInt() + 1))
            .putIn(path / "active", PrimitiveModel(true))
      }
      host
    }
  }

  private fun nextIdx(): String = (ReactiveModel.current()!!.root.getIn(counterPath) as PrimitiveModel<*>).value.toString()
}