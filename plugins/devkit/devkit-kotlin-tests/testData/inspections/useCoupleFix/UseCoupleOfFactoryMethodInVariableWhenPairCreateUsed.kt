import com.intellij.openapi.util.Pair

@Suppress("UNUSED_VARIABLE")
class UseCoupleOfFactoryMethodInVariableWhenPairCreateUsed {
  fun any() {
    val any: <warning descr="Replace with 'Couple<String>'">Pair<String, String></warning> = <warning descr="Replace with 'Couple.of()'">Pair.cr<caret>eate("a", "b")</warning>
  }
}
