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
import com.jetbrains.reactivemodel.Path
import com.jetbrains.reactivemodel.ReactiveModel
import com.jetbrains.reactivemodel.models.MapModel
import com.jetbrains.reactivemodel.putIn
import com.jetbrains.reactivemodel.util.Lifetime
import java.util.*

/**
 * Base host. initalize model with metadata
 */
public open class MetaHost(val lifetime: Lifetime,
                           public val reactiveModel: ReactiveModel,
                           public val path: Path) {

  protected fun initModel(initFunc: ((MapModel) -> MapModel) = { m -> m }) {
    val hostMeta = PersistentHashMap.create(buildMeta())
    reactiveModel.transaction { m ->
      initFunc(path.putIn(m, MapModel(metadata = hostMeta)))
    }
  }


  protected open fun buildMeta(): HashMap<String, Any> {
    val map = HashMap<String, Any>()
    map["host"] = this
    map["lifetime"] = lifetime
    return map
  }
}