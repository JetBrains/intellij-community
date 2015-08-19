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

import com.github.krukow.clj_lang.PersistentHashMap
import com.jetbrains.reactivemodel.Model
import com.jetbrains.reactivemodel.mapping
import com.jetbrains.reactivemodel.mapping.Mapper
import com.jetbrains.reactivemodel.models.ListModel
import com.jetbrains.reactivemodel.models.MapModel
import com.jetbrains.reactivemodel.models.PrimitiveModel
import com.jetbrains.reactivemodel.util.Primitives
import javassist.ClassPool
import javassist.CtNewMethod
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.platform.platformStatic
import kotlin.reflect.KClass
import kotlin.reflect.KMemberProperty
import kotlin.reflect.jvm.java
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.javaGetter
import kotlin.reflect.jvm.kotlin

/**
 * Map java beans to Model recursively
 */
public object ModelMapper {
  private val mappers = ConcurrentHashMap<Class<*>, Mapper<Any, Model>>()
  private val counter = AtomicInteger()

  @platformStatic public fun <T : Any>map(obj: T): Model {
    if (obj.javaClass in Primitives.TYPES) {
      return PrimitiveModel(obj)
    }
    if (obj is List<*>) {
      return ListModel(obj.map { map(it as Any) })
    }
    val clz = obj.javaClass
    var mapper = mappers[clz]
    if (mapper == null) {
      mapper = createMapper(obj.javaClass)
      mappers.putIfAbsent(clz, mapper)
      mapper = mappers[clz]
    }
    return mapper.map(obj)
  }

  private fun <T : Any> createMapper(clz: Class<T>): Mapper<Any, Model> {
    val pool = ClassPool.getDefault()
    val mClz = pool.makeClass("ModelMapper_Transformer${counter.incrementAndGet()}")
    mClz.addInterface(pool.get(Mapper::class.java.getName()))
    val transformBody = createMapBody(clz)
    println(transformBody)
    val value = "public Object map(Object v1) { " +
        "${clz.getName()} v = (${clz.getName()})v1;\n" + // explicit cast, because javassist does not support bridge methods
        "java.util.HashMap map = new java.util.HashMap();\n" +
        "${transformBody}\n" +
        "return new ${MapModel::class.java.getName()}(map, ${PersistentHashMap::class.java.getName()}.EMPTY);\n" +
        "}\n"
    println(value)
    val mapMethod = CtNewMethod.make(value, mClz)
    mClz.addMethod(mapMethod)
    return mClz.toClass().newInstance() as Mapper<Any, Model>;
  }

  private fun <T> createMapBody(clz: Class<T>): String {
    return clz.getDeclaredFields().filter { !it.isSynthetic() }.map {
      val sb = StringBuilder()
      val varName = "var${counter.incrementAndGet()}"
      sb.append("${it.getType().getName()} $varName = v.")
      val getterName = "get${it.getName().capitalize()}"
      if (clz.getMethods().any { it.getName() == getterName && it.getParameterCount() == 0 }) {
        sb.append("$getterName()")
      } else if (Modifier.isPublic(it.getModifiers())) {
        sb.append("${it.getName()}")
      } else {
        throw RuntimeException("No getter presents for field ${it.getName()}");
      }
      sb.append(";\n")
      if (it.getType() !in Primitives.JAVA_PRIMITIVES) sb.append("if ($varName != null)\n")
      sb.append("map.put(\"${it.getName()}\", ${ModelMapper::class.java.getName()}.map($varName));\n");
      sb.toString()
    }.join("")
  }

  @platformStatic public fun map(obj: Boolean): Model = PrimitiveModel(obj)
  @platformStatic public fun map(obj: Byte): Model = PrimitiveModel(obj)
  @platformStatic public fun map(obj: Char): Model = PrimitiveModel(obj)
  @platformStatic public fun map(obj: Double): Model = PrimitiveModel(obj)
  @platformStatic public fun map(obj: Float): Model = PrimitiveModel(obj)
  @platformStatic public fun map(obj: Int): Model = PrimitiveModel(obj)
  @platformStatic public fun map(obj: Long): Model = PrimitiveModel(obj)
  @platformStatic public fun map(obj: Short): Model = PrimitiveModel(obj)
}