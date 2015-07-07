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

import com.github.krukow.clj_lang.IPersistentMap
import com.github.krukow.clj_lang.PersistentHashMap
import com.jetbrains.reactivemodel.Path
import com.jetbrains.reactivemodel.ReactiveModel
import com.jetbrains.reactivemodel.getIn
import com.jetbrains.reactivemodel.models.MapModel
import com.jetbrains.reactivemodel.putIn
import com.jetbrains.reactivemodel.util.Lifetime
import com.jetbrains.reactivemodel.util.emptyMeta
import com.jetbrains.reactivemodel.util.get
import java.util.*

/**
 * Base host. Initialize model with metadata
 */
public open class MetaHost(public val reactiveModel: ReactiveModel,
                           public val path: Path) {

  private var life: Lifetime? = null;

  protected fun initModel(initFunc: ((MapModel) -> MapModel) = { m -> m }) {
    var parent = path;
    var parentLifetime: Lifetime? = null
    while (parent != Path()) {
      parent = parent.dropLast(1)
      parentLifetime = parent.getIn(reactiveModel.root)?.meta?.valAt("lifetime") as? Lifetime
      if (parentLifetime != null) {
        break
      }
    }
    if (parentLifetime == null) {
      throw RuntimeException("Shouldn't happens!")
    }
    life = Lifetime.create(parentLifetime).lifetime

    reactiveModel.transaction { m ->

      val hostMeta = PersistentHashMap.create(buildMeta())
      initFunc(path.putIn(m, MapModel(meta = hostMeta)))
    }
  }

  protected open fun buildMeta(): HashMap<String, Any> {
    val map = HashMap<String, Any>()
    map["host"] = this
    map["lifetime"] = lifetime
    return map
  }

  val lifetime: Lifetime
    get() = life as Lifetime;
}