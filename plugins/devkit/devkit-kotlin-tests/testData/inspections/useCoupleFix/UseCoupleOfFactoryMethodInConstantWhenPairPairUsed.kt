import com.intellij.openapi.util.Pair

object UseCoupleOfFactoryMethodInConstantWhenPairPairUsed {
  private val ANY: <warning descr="Replace with 'Couple<String>'">Pair<String, String></warning> = <warning descr="Replace with 'Couple.of()'">Pair.pa<caret>ir("a", "b")</warning>
}
