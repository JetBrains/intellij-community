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
package com.jetbrains.reactivemodel

import com.github.krukow.clj_lang.PersistentHashMap
import com.jetbrains.reactivemodel.models.ListModel
import com.jetbrains.reactivemodel.models.MapModel
import com.jetbrains.reactivemodel.models.PrimitiveModel
import com.jetbrains.reactivemodel.util.Lifetime
import com.jetbrains.reactivemodel.util.LifetimeDefinition

interface Host {
  val tags: Array<String>
    get() = arrayOf()
}

public class Initializer(var model: MapModel) {
  var expired: Boolean = false

  public fun plusAssign(f: (MapModel) -> MapModel) {
    if (expired) {
      throw AssertionError("Initializer expired")
    }
    model = f(model)
  }
}

public fun ReactiveModel.host<U : Host>(path: Path, h: (Path, Lifetime, Initializer) -> U): U {
  val toStartTransaction = currentInit == null

  if (toStartTransaction) {
    currentInit = Initializer(root);
  }

  var parent = path;
  var parentLifetime: Lifetime? = null
  while (parent != Path()) {
    parent = parent.dropLast(1)
    parentLifetime = (currentInit!!.model.getIn(parent)?.meta?.valAt("lifetime") as? Lifetime)
    if (parentLifetime != null) {
      break
    }
  }
  if (parentLifetime == null) {
    throw RuntimeException("Shouldn't happen!")
  }
  val lifetimeDefinition = Lifetime.create(parentLifetime)

  currentInit!! += {
    it.putIn(path, MapModel(emptyMap(),
        PersistentHashMap.create(hashMapOf(
            "lifetime" to lifetimeDefinition.lifetime))))
  }

  val aHost = h(path, lifetimeDefinition.lifetime, currentInit!!)

  currentInit!! += {
    it.putIn(path,
        (it.getIn(path) as MapModel)
            .assocMeta("host", aHost)
            .assoc(tagsField, ListModel(aHost.tags.map { PrimitiveModel(it) })))
  }

  if (toStartTransaction) {
    transaction { m ->
      val result = currentInit!!.model
      currentInit!!.expired = true
      currentInit = null;
      result
    }
  }
  return aHost
}
