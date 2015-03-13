package org.jetbrains.protocolReader

trait StandaloneTypeBinding {
  public fun getJavaType(): BoxableType

  public fun generate()

  /**
   * @return null if not direction-specific
   */
  public fun getDirection(): TypeData.Direction?

  public trait Target {
    public fun resolve(context: ResolveContext): BoxableType

    public trait ResolveContext {
      public fun generateNestedObject(shortName: String, description: String?, properties: List<ProtocolMetaModel.ObjectProperty>?): BoxableType
    }
  }

  public class PredefinedTarget(private val resolvedType: BoxableType) : Target {

    override fun resolve(context: Target.ResolveContext): BoxableType {
      return resolvedType
    }

    default object {

      public val STRING: PredefinedTarget = PredefinedTarget(BoxableType.STRING)
      public val INT: PredefinedTarget = PredefinedTarget(BoxableType.INT)
      public val NUMBER: PredefinedTarget = PredefinedTarget(BoxableType.NUMBER)
      public val MAP: PredefinedTarget = PredefinedTarget(BoxableType.MAP)
    }
  }
}
