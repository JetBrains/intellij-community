// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.protocolReader

internal fun GlobalScope(typeWriters: Collection<TypeWriter<*>?>, basePackages: Collection<Map<Class<*>, String>>) = GlobalScope(State(typeWriters, basePackages))

internal open class GlobalScope(val state: State) {
  fun getTypeImplReference(typeWriter: TypeWriter<*>) = state.getTypeImplReference(typeWriter)

  fun requireFactoryGenerationAndGetName(typeWriter: TypeWriter<*>) = state.requireFactoryGenerationAndGetName(typeWriter)

  fun getTypeImplShortName(typeWriter: TypeWriter<*>) = state.getTypeImplShortName(typeWriter)

  fun newFileScope(output: StringBuilder) = FileScope(this, output)

  fun getTypeFactories() = state.typesWithFactoriesList
}

internal class State(typeWriters: Collection<TypeWriter<*>?>, private val basePackages: Collection<Map<Class<*>, String>>) {
  private var typeToName: Map<TypeWriter<*>?, String>
  private val typesWithFactories = HashSet<TypeWriter<*>>()
  val typesWithFactoriesList = ArrayList<TypeWriter<*>>()

  init {
    typeToName = buildMap<TypeWriter<*>?, String> {
      typeWriters
        .groupBy { it!!.typeClass.simpleName }
        .forEach { (name, writers) ->
          writers.forEachIndexed { index, handler ->
            val handlerName = if (index == 0) {
              name
            }
            else {
              "$name$index"
            }
            put(handler, handlerName)
          }
        }
    }
  }

  fun getTypeImplReference(typeWriter: TypeWriter<*>): String {
    val localName = typeToName.get(typeWriter)
    if (localName != null) {
      return localName
    }

    for (base in basePackages) {
      val result = base.get(typeWriter.typeClass)
      if (result != null) {
        return result
      }
    }

    throw RuntimeException()
  }

  fun requireFactoryGenerationAndGetName(typeWriter: TypeWriter<*>): String {
    val name = getTypeImplShortName(typeWriter)
    if (typesWithFactories.add(typeWriter)) {
      typesWithFactoriesList.add(typeWriter)
    }
    return name
  }

  fun getTypeImplShortName(typeWriter: TypeWriter<*>) = typeToName.get(typeWriter)!!
}
