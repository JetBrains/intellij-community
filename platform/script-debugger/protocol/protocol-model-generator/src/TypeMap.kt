package org.jetbrains.protocolModelGenerator

import gnu.trove.THashMap
import java.util.ArrayList

/**
 * Keeps track of all referenced types.
 * A type may be used and resolved (generated or hard-coded).
 */
class TypeMap {
  private val map = THashMap<Pair<String, String>, TypeData>()

  var domainGeneratorMap: Map<String, DomainGenerator>? = null

  private val typesToGenerate = ArrayList<StandaloneTypeBinding>()

  fun resolve(domainName: String, typeName: String, direction: TypeData.Direction): BoxableType? {
    val domainGenerator = domainGeneratorMap!!.get(domainName) ?: throw RuntimeException("Failed to find domain generator: " + domainName)
    return direction.get(getTypeData(domainName, typeName)).resolve(this, domainGenerator)
  }

  fun addTypeToGenerate(binding: StandaloneTypeBinding) {
    typesToGenerate.add(binding)
  }

  fun generateRequestedTypes() {
    // size may grow during iteration
    var list = typesToGenerate.toTypedArray()
    typesToGenerate.clear()
    while (true) {
      for (binding in list) {
        binding.generate()
      }

      if (typesToGenerate.isEmpty()) {
        break
      }
      else {
        list = typesToGenerate.toTypedArray()
        typesToGenerate.clear()
      }
    }
  }

  fun getTypeData(domainName: String, typeName: String): TypeData {
    val key = Pair(domainName, typeName)
    var result: TypeData? = map.get(key)
    if (result == null) {
      result = TypeData(typeName)
      map.put(key, result)
    }
    return result
  }
}
