// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.protocolModelGenerator

import java.util.*

/**
 * Keeps track of all referenced types.
 * A type may be used and resolved (generated or hard-coded).
 */
internal class TypeMap {
  private val map = HashMap<Pair<String, String>, TypeData>()

  var domainGeneratorMap: Map<String, DomainGenerator>? = null

  private val typesToGenerate = ArrayDeque<StandaloneTypeBinding>()

  fun resolve(domainName: String, typeName: String, direction: TypeData.Direction): BoxableType? {
    val domainGenerator = domainGeneratorMap!!.get(domainName)
    if (domainGenerator == null) {
      val qName = "$domainName.$typeName"
      if (qName == "IO.StreamHandle" ||
          qName == "Security.SecurityState" ||
          qName == "Security.CertificateId" ||
          qName == "Emulation.ScreenOrientation" ||
          qName == "Security.MixedContentType"
      ) {
        return BoxableType.ANY_STRING // ignore
      }
      throw RuntimeException("Failed to find domain generator: $domainName for type $typeName")
    }
    return direction.get(getTypeData(domainName, typeName)).resolve(this, domainGenerator)
  }

  fun addTypeToGenerate(binding: StandaloneTypeBinding) {
    typesToGenerate.offer(binding)
  }

  fun generateRequestedTypes() {
    // size may grow during iteration
    val createdTypes = HashSet<CharSequence>()
    while (typesToGenerate.isNotEmpty()) {
      val binding = typesToGenerate.poll()
      if (createdTypes.add(binding.getJavaType().fullText)) {
        binding.generate()
      }
    }
  }

  fun getTypeData(domainName: String, typeName: String) = map.getOrPut(Pair(domainName, typeName)) { TypeData(typeName) }
}
