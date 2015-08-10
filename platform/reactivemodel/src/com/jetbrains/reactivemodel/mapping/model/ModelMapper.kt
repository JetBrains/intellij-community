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
package com.jetbrains.reactivemodel.mapping.model

import com.jetbrains.reactivemodel.Model
import com.jetbrains.reactivemodel.models.ListModel
import com.jetbrains.reactivemodel.models.MapModel
import com.jetbrains.reactivemodel.models.PrimitiveModel
import com.jetbrains.reactivemodel.util.Primitives
import java.lang.reflect.Modifier
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.javaGetter
import kotlin.reflect.jvm.kotlin

/**
 * Map java beans to Model recursively
 */
public object ModelMapper {
  public fun map(obj: Any): Model {
    if (obj.javaClass in Primitives.TYPES) {
      return PrimitiveModel(obj)
    }
    if (obj is List<*>) {
      return ListModel(obj.map { map(it as Any) })
    }
    // TODO. Switch to some more performant here
    return MapModel(obj.javaClass.kotlin.properties.toMap({ it.name }) { prop ->
      when {
        prop.javaGetter != null -> {
          prop.javaGetter!!.invoke(obj)
        }
        obj.javaClass.getMethods().any { it.getName() == "get${prop.name.capitalize()}" } -> {
          obj.javaClass.getMethod("get${prop.name.capitalize()}").invoke(obj)
        }
        Modifier.isPublic(prop.javaField!!.getModifiers()) -> {
          prop.javaField!!.get(obj)
        }
        else -> throw RuntimeException("No getter presents for ${prop.name} in class ${obj.javaClass}")
      }
    }.filterValues { it != null }.mapValues { ModelMapper.map(it.value) })
  }

  public fun map(obj: Boolean): Model = PrimitiveModel(obj)
  public fun map(obj: Byte): Model = PrimitiveModel(obj)
  public fun map(obj: Char): Model = PrimitiveModel(obj)
  public fun map(obj: Double): Model = PrimitiveModel(obj)
  public fun map(obj: Float): Model = PrimitiveModel(obj)
  public fun map(obj: Int): Model = PrimitiveModel(obj)
  public fun map(obj: Long): Model = PrimitiveModel(obj)
  public fun map(obj: Short): Model = PrimitiveModel(obj)
}