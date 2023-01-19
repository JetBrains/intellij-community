import com.intellij.openapi.util.Pair

class UseCoupleOfFactoryMethodInVariableWhenPairCreateUsed {
  fun any() {
    takePair(<warning descr="Replace with 'Couple.of()'">Pair.cr<caret>eate("a", "b")</warning>)
  }

  @Suppress("UNUSED_PARAMETER")
  fun <A, B> takePair(pair: Pair<A, B>?) {
    // do nothing
  }
}
