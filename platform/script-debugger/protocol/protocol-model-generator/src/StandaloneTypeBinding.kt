package org.jetbrains.protocolModelGenerator

import org.jetbrains.jsonProtocol.ProtocolMetaModel

internal interface StandaloneTypeBinding {
  fun getJavaType(): BoxableType

  fun generate()

  /**
   * @return null if not direction-specific
   */
  fun getDirection(): TypeData.Direction?
}

internal interface Target {
  fun resolve(context: ResolveContext): BoxableType

  interface ResolveContext {
    fun generateNestedObject(shortName: String, description: String?, properties: List<ProtocolMetaModel.ObjectProperty>?): BoxableType
  }
}

internal class PredefinedTarget(private val resolvedType: BoxableType) : Target {
  override fun resolve(context: Target.ResolveContext) = resolvedType

  companion object {
    val STRING = PredefinedTarget(BoxableType.STRING)
    val INT = PredefinedTarget(BoxableType.INT)
    val NUMBER = PredefinedTarget(BoxableType.NUMBER)
    val MAP = PredefinedTarget(BoxableType.MAP)
  }
}