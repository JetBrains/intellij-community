import com.intellij.openapi.util.Pair

object UseCoupleOfFactoryMethodInConstantWhenPairCreateUsed {
  private val ANY: <warning descr="Replace with 'Couple<String>'">Pair<String, String></warning> = <warning descr="Replace with 'Couple.of()'">Pair.cr<caret>eate("a", "b")</warning>
}
