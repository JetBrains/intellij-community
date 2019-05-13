package org.jetbrains.protocolModelGenerator

import org.jetbrains.jsonProtocol.ProtocolMetaModel

internal abstract class CreateStandaloneTypeBindingVisitorBase(private val generator: DomainGenerator, protected val type: ProtocolMetaModel.StandaloneType) : TypeVisitor<StandaloneTypeBinding> {
  override fun visitString(): StandaloneTypeBinding {
    return generator.createTypedefTypeBinding(type, PredefinedTarget.STRING, generator.generator.naming.commonTypedef, null)
  }

  override fun visitInteger() = generator.createTypedefTypeBinding(type, PredefinedTarget.INT, generator.generator.naming.commonTypedef, null)

  override fun visitRef(refName: String) = throw RuntimeException()

  override fun visitBoolean() = throw RuntimeException()

  override fun visitNumber(): StandaloneTypeBinding {
    return generator.createTypedefTypeBinding(type, PredefinedTarget.NUMBER, generator.generator.naming.commonTypedef, null)
  }

  override fun visitMap(): StandaloneTypeBinding {
    return generator.createTypedefTypeBinding(type, PredefinedTarget.MAP, generator.generator.naming.commonTypedef, null)
  }

  override fun visitUnknown(): StandaloneTypeBinding {
    throw RuntimeException()
  }
}
