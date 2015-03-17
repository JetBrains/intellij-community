package org.jetbrains.protocolReader

/**
 * Keeps track of all referenced types.
 * A type may be used and resolved (generated or hard-coded).
 */
class TypeMap {
  private val map = THashMap<Pair<String, String>, TypeData>()
  private var domainGeneratorMap: Map<String, DomainGenerator>? = null
  private val typesToGenerate = ArrayList<StandaloneTypeBinding>()

  fun setDomainGeneratorMap(domainGeneratorMap: Map<String, DomainGenerator>) {
    this.domainGeneratorMap = domainGeneratorMap
  }

  fun resolve(domainName: String, typeName: String, direction: TypeData.Direction): BoxableType? {
    val domainGenerator = domainGeneratorMap!!.get(domainName)
    if (domainGenerator == null) {
      throw RuntimeException("Failed to find domain generator: " + domainName)
    }
    return getTypeData(domainName, typeName).get(direction).resolve(this, domainGenerator)
  }

  fun addTypeToGenerate(binding: StandaloneTypeBinding) {
    typesToGenerate.add(binding)
  }

  throws(javaClass<IOException>())
  public fun generateRequestedTypes() {
    // Size may grow during iteration.
    //noinspection ForLoopReplaceableByForEach
    for (i in typesToGenerate.indices) {
      typesToGenerate.get(i).generate()
    }

    for (typeData in map.values()) {
      typeData.checkComplete()
    }
  }

  fun getTypeData(domainName: String, typeName: String): TypeData {
    val key = Pair.create<String, String>(domainName, typeName)
    var result: TypeData? = map.get(key)
    if (result == null) {
      result = TypeData(typeName)
      map.put(key, result)
    }
    return result!!
  }
}
