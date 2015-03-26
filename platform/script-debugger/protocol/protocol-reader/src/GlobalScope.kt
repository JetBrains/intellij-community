package org.jetbrains.protocolReader

import gnu.trove.THashMap
import gnu.trove.THashSet

import java.util.ArrayList

fun GlobalScope(typeWriters: Collection<TypeWriter<*>>, basePackages: Collection<Map<Class<*>, String>>): GlobalScope {
  return GlobalScope(State(typeWriters, basePackages))
}

open class GlobalScope(val state: State) {
  public fun getTypeImplReference(typeWriter: TypeWriter<*>): String {
    return state.getTypeImplReference(typeWriter)
  }

  public fun requireFactoryGenerationAndGetName(typeWriter: TypeWriter<*>): String {
    return state.requireFactoryGenerationAndGetName(typeWriter)
  }

  public fun getTypeImplShortName(typeWriter: TypeWriter<*>): String {
    return state.getTypeImplShortName(typeWriter)
  }

  public fun newFileScope(output: StringBuilder): FileScope {
    return FileScope(this, output)
  }

  public fun getTypeFactories(): List<TypeWriter<*>> {
    return state.typesWithFactoriesList
  }
}

private class State(typeWriters: Collection<TypeWriter<*>>, private val basePackages: Collection<Map<Class<*>, String>>) {
  private var typeToName: Map<TypeWriter<*>, String>
  private val typesWithFactories = THashSet<TypeWriter<*>>()
  val typesWithFactoriesList = ArrayList<TypeWriter<*>>();

  init {
    var uniqueCode = 0
    val result = THashMap<TypeWriter<*>, String>(typeWriters.size())
    for (handler in typeWriters) {
      val conflict = result.put(handler, TYPE_NAME_PREFIX + Integer.toString(uniqueCode++))
      if (conflict != null) {
        throw RuntimeException()
      }
    }
    typeToName = result
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

  public fun requireFactoryGenerationAndGetName(typeWriter: TypeWriter<*>): String {
    val name = getTypeImplShortName(typeWriter)
    if (typesWithFactories.add(typeWriter)) {
      typesWithFactoriesList.add(typeWriter)
    }
    return name
  }

  fun getTypeImplShortName(typeWriter: TypeWriter<*>): String {
    return typeToName.get(typeWriter)!!
  }
}
