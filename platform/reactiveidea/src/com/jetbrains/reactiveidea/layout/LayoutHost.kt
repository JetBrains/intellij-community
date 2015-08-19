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

import com.intellij.openapi.project.Project
import com.jetbrains.reactivemodel.*
import com.jetbrains.reactivemodel.models.PrimitiveModel
import com.jetbrains.reactivemodel.util.Lifetime

class LayoutHost(val project: Project,
                 val reactiveModel: ReactiveModel,
                 val path: Path,
                 val lifetime: Lifetime,
                 init: Initializer) : Host {

  companion object {
    val counter = "counter"
    val editors = "editors"
    val usages = "usages"
  }

  init {
    init += {
      it.putIn(path / counter, PrimitiveModel(0))
    }
    reactiveModel.host(path / editors) { hostpath, lifetime, initializer ->
      EditorPoolHost(reactiveModel, hostpath, lifetime, initializer, path / counter)
    }
    reactiveModel.host(path / usages) { hostpath, lifetime, initializer ->
      UsagesPoolHost(project, reactiveModel, hostpath, lifetime, initializer, path / counter)
    }
  }
}

