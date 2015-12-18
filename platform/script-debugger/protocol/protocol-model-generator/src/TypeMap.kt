package org.jetbrains.protocolModelGenerator

import gnu.trove.THashMap
import java.util.*

/**
 * Keeps track of all referenced types.
 * A type may be used and resolved (generated or hard-coded).
 */
internal class TypeMap {
  private val map = THashMap<Pair<String, String>, TypeData>()

  var domainGeneratorMap: Map<String, DomainGenerator>? = null

  private val typesToGenerate = ArrayList<StandaloneTypeBinding>()

  fun resolve(domainName: String, typeName: String, direction: TypeData.Direction): BoxableType? {
    val domainGenerator = domainGeneratorMap!!.get(domainName)
    if (domainGenerator == null) {
      val qName = "$domainName.$typeName";
      if (qName == "IO.StreamHandle" || qName == "Security.SecurityState") return BoxableType.ANY_STRING // ignore
      throw RuntimeException("Failed to find domain generator: $domainName for type $typeName")
    }
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

  fun getTypeData(domainName: String, typeName: String) = map.getOrPut(Pair(domainName, typeName)) { TypeData(typeName) }
}
