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
package com.jetbrains.reactivemodel.mapping


import com.jetbrains.reactivemodel.Model
import com.jetbrains.reactivemodel.mapping.model.ModelMapper
import com.jetbrains.reactivemodel.util.Primitives
import javassist.ClassPool
import javassist.CtNewMethod
import org.reflections.Reflections
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Kotlin data mapper.
 *
 * Map object to java beans. If custom mapper provided it will be used, else mapper would be generated
 * Custom mappers can be registered manually via #registerMapper method or provided with @Mapping annotation
 */
public object KDM {
  private val mappers = ConcurrentHashMap<Class<*>, Mapper<*, *>>()
  private val typeBindings = ConcurrentHashMap<Class<*>, Class<*>>()
  private val counter = AtomicInteger()
  private val myName = javaClass<KDM>().getName() + ".INSTANCE$"

  init {
    scanAnnotations()
    registerPrimitiveMappers()
    registerCollections()
  }

  private fun scanAnnotations() {
    val reflections = Reflections("com.jetbrains")
    val types = reflections.getTypesAnnotatedWith(javaClass<Mapping>())
    for (type in types) {
      val annotation = type.getAnnotation(javaClass<Mapping>())
      typeBindings.put(annotation.clz, type)
      if (annotation.mapper != javaClass<Mapper<*, *>>()) {
        registerMapper(annotation.clz as Class<Any>, type as Class<Any>, annotation.mapper.newInstance() as Mapper<Any, Any>);
      }
    }

    val customMappers = reflections.getTypesAnnotatedWith(javaClass<BeanMapper>())
    for (mapper in customMappers) {
      val anno = mapper.getAnnotation(javaClass<BeanMapper>())
      registerMapper(anno.from, anno.to as Class<Any>, mapper.newInstance() as Mapper<Any, Any>)
    }
  }

  public fun <F, T>registerMapper(clz: Class<F>, to: Class<T>, mapper: Mapper<in F, T>) {
    mappers[clz] = mapper
    typeBindings.put(clz, to)
  }

  @suppress("UNCHECKED_CAST")
  public fun <T>map(obj: Any?): T {
    if (obj == null) {
      return null
    }
    val clz = obj.javaClass
    var mapper = mappers[clz] as? Mapper<Any, T>
    if (mapper == null) {
      mapper = createMapper(clz) ?: forSuperClass(clz)
      if (mapper == null) {
        throw RuntimeException("Unable to create mapper for " + clz)
      }
      mappers.putIfAbsent(clz, mapper)
      mapper = mappers[clz] as Mapper<Any, T>
    }
    return mapper.map(obj)
  }

  // TODO. Is this method should be here? it is convenient, but isn't logical
  public fun toModel(obj: Any): Model = ModelMapper.map(map<Any>(obj))

  @suppress("UNCHECKED_CAST")
  private fun <T> forSuperClass(clz: Class<Any>): Mapper<Any, T>? {
    var parent = clz.getSuperclass() as Class<Any>
    while (parent != javaClass<Object>()) {
      if (mappers[parent] != null) {
        return mappers[parent] as? Mapper<Any, T>
      }
      parent = parent.getSuperclass() as Class<Any>
    }
    for (obj in clz.getInterfaces()) {
      if (mappers[obj] != null) {
        return mappers[obj] as? Mapper<Any, T>
      }
    }
    return null;
  }

  private fun createMapper<F, T>(clz: Class<F>): Mapper<F, T>? {
    val target = typeBindings.get(clz) ?: return null

    val pool = ClassPool.getDefault()
    val mClz = pool.makeClass("${clz.getSimpleName()}\$KDM_Transformer${counter.incrementAndGet()}")
    mClz.addInterface(pool.get(javaClass<Mapper<*, *>>().getName()))
    val transformBody = createMapBody(clz, target)
    val mapMethod = CtNewMethod.make("public Object map(Object v1) { " +
        "${clz.getName()} v = (${clz.getName()})v1;" + // explicit cast, because javassist does not support bridge methods
        "\n ${transformBody} " +
        "\n}", mClz)
    mClz.addMethod(mapMethod)
    return mClz.toClass().newInstance() as Mapper<F, T>;
  }

  private fun <F> createMapBody(clz: Class<F>, target: Class<*>): String {
    val targetName = target.getName()

    val fields = target.getDeclaredFields().filter {
      it.getAnnotation(javaClass<Ignore>()) == null && !it.isSynthetic()
    }
    val getterMap = fields.toMap({ it }) { getGetterName(clz, it) }

    val constructors = target.getConstructors()

    val hasDefaultConstructor = constructors.any { it.getParameterCount() == 0 }
    if (hasDefaultConstructor) {
      val sb = StringBuilder("$targetName t = new $targetName();")
      for (field in fields) {
        val getterName = getterMap[field]
        val name = field.getName()
        val castType = field.getType().getName()

        if (Modifier.isPublic(field.getModifiers())) {
          sb.append("t.$name = ($castType)$myName.map(v.$getterName);")
        } else {
          val setterName = getSetterName(target, name)
          sb.append("t.$setterName(($castType)$myName.map(v.$getterName));\n")
        }
      }
      return sb.append("return t;\n").toString()
    }

    val hasFieldConstructor = constructors.any {
      if (it.getParameterCount() != fields.size()) {
        false
      } else {
        val types = it.getParameterTypes()
        fields.zip(types).all { it: Pair<Field, Class<*>> ->
          it.first.getType() == it.second || it.first.getType() == typeBindings[it.second]
        }
      }
    }

    if (hasFieldConstructor) {
      return "return new $targetName(${getterMap.entrySet().joinToString {
        "(${it.key.getType().getName()})${myName}.map(v.${it.value})"
      }});\n"
    }

    throw RuntimeException("Class ${target.getName()} should have default or parameter constructor")
  }

  private fun getSetterName(clz: Class<*>, name: String): Any {
    val method = clz.getMethods().firstOrNull() {
      it.getName() == "set${name.capitalize()}" && it.getParameterCount() == 1
    } ?: throw RuntimeException("No setter method presents for field $name in class ${clz.getName()}")
    return method.getName()
  }

  private fun <F> getGetterName(clz: Class<F>, f: Field): Any {
    val name = f.getAnnotation(javaClass<Original>())?.name ?: f.getName()

    val fromField = clz.getFields().firstOrNull { it.getName() == name }
    if (fromField != null && Modifier.isPublic(fromField.getModifiers())) {
      return name;
    }
    try {
      if (f.getType() == javaClass<Boolean>()) {
        val isMethod = clz.getMethods().firstOrNull {
          it.getName() == "is${name.capitalize()}" && it.getParameterCount() == 0;
        }
        if (isMethod != null) {
          return isMethod.getName() + "()"
        }
      }
      val method = clz.getMethod("get${name.capitalize()}")
      return method.getName() + "()";
    } catch(e: NoSuchMethodException) {
      throw RuntimeException("No getter method presents for field $name in class ${clz.getName()}")
    }
  }

  private fun registerPrimitiveMappers() {
    val pMapper = object : Mapper<Any, Any> {
      override fun map(obj: Any) = obj
    }
    for (primClz in Primitives.TYPES) {
      val clz = primClz as Class<Any>
      registerMapper(clz, clz, pMapper)
    }
  }

  private fun registerCollections() {
    val listClz = javaClass<List<*>>()
    registerMapper(listClz, listClz, object : Mapper<List<Any?>, List<Any?>> {
      override fun map(obj: List<Any?>): List<Any?> {
        return obj.map { KDM.map<Any?>(it) }.toArrayList()
      }
    })
  }

  // primitive methods

  public fun map(obj: Boolean): Boolean = obj
  public fun map(obj: Byte): Byte = obj
  public fun map(obj: Char): Char = obj
  public fun map(obj: Double): Double = obj
  public fun map(obj: Float): Float = obj
  public fun map(obj: Int): Int = obj
  public fun map(obj: Long): Long = obj
  public fun map(obj: Short): Short = obj
}