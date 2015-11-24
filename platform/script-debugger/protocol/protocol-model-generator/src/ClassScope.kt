package org.jetbrains.protocolModelGenerator

import com.intellij.util.SmartList
import org.jetbrains.jsonProtocol.ItemDescriptor
import org.jetbrains.protocolReader.TextOutput

internal abstract class ClassScope(val generator: DomainGenerator, val classContextNamespace: NamePath) {
  private val additionalMemberTexts = SmartList<(out: TextOutput) -> Unit>()

  fun addMember(appender: (out: TextOutput) -> Unit) {
    additionalMemberTexts.add(appender)
  }

  fun writeAdditionalMembers(out: TextOutput) {
    if (additionalMemberTexts.isEmpty()) {
      return
    }

    out.newLine()
    for (deferredWriter in additionalMemberTexts) {
      deferredWriter(out)
    }
  }

  abstract val typeDirection: TypeData.Direction
}

fun ItemDescriptor.Named.getName() = shortName ?: name()