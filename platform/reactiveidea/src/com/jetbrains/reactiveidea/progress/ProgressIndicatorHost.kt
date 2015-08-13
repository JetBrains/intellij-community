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
package com.jetbrains.reactiveidea.progress

import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.reactivemodel.*
import com.jetbrains.reactivemodel.models.PrimitiveModel
import com.jetbrains.reactivemodel.util.Lifetime

/**
 * Created by Sergey.Tselovalnikov on 8/11/15.
 */
public class ProgressIndicatorHost(val reactiveModel: ReactiveModel,
                                   val path: Path,
                                   val lifetime: Lifetime,
                                   val progress: ReactiveStateProgressIndicator,
                                   init: Initializer) : Host {

  companion object {
    val value = "value"
    val canceled = "canceled"
  }

  override val tags: Array<String>
    get() = arrayOf("progress")

  init {
    lifetime += {
      progress.lifetime.terminate()
    }
    init += { m ->
      reaction(false, "progress reaction", progress.modelSignal) { model ->
        reactiveModel.transaction { it.putIn(path / value, model) }
      }
      reaction(false, "progress cancelation", reactiveModel.subscribe(lifetime, path / value / canceled)) { cancel ->
        cancel as PrimitiveModel<*>
        val isCanceled = cancel.value as Boolean
        if (isCanceled) {
          progress.cancel()
        }
      }
      m.putIn(path / value, progress.modelSignal.value)
    }
  }
}