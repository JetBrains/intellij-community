package org.jetbrains.protocolModelGenerator

import org.jetbrains.jsonProtocol.ProtocolMetaModel

interface StandaloneTypeBinding {
  public fun getJavaType(): BoxableType

  public fun generate()

  /**
   * @return null if not direction-specific
   */
  public fun getDirection(): TypeData.Direction?
}

interface Target {
  public fun resolve(context: ResolveContext): BoxableType

  public interface ResolveContext {
    public fun generateNestedObject(shortName: String, description: String?, properties: List<ProtocolMetaModel.ObjectProperty>?): BoxableType
  }
}

class PredefinedTarget(private val resolvedType: BoxableType) : Target {
  override fun resolve(context: Target.ResolveContext): BoxableType {
    return resolvedType
  }

  companion object {
    public val STRING: PredefinedTarget = PredefinedTarget(BoxableType.STRING)
    public val INT: PredefinedTarget = PredefinedTarget(BoxableType.INT)
    public val NUMBER: PredefinedTarget = PredefinedTarget(BoxableType.NUMBER)
    public val MAP: PredefinedTarget = PredefinedTarget(BoxableType.MAP)
  }
}