package org.jetbrains.protocolModelGenerator

import gnu.trove.THashMap
import org.jetbrains.jsonProtocol.ItemDescriptor
import org.jetbrains.protocolReader.TextOutput
import java.util.ArrayList

abstract class ClassScope(val generator: DomainGenerator, val classContextNamespace: NamePath) {
  private val additionalMemberTexts = ArrayList<TextOutConsumer>(2)

  fun addMember(out: TextOutConsumer) {
    additionalMemberTexts.add(out)
  }

  fun writeAdditionalMembers(out: TextOutput) {
    if (additionalMemberTexts.isEmpty()) {
      return
    }

    out.newLine()
    for (deferredWriter in additionalMemberTexts) {
      deferredWriter.append(out)
    }
  }

  abstract val typeDirection: TypeData.Direction
}

fun ItemDescriptor.Named.getName() = shortName() ?: name()