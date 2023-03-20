import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.Pair.create

class UseCoupleOfFactoryMethodInVariableWhenPairCreateUsed {
  fun any() {
    takePair(<warning descr="Replace with 'Couple.of()'">cr<caret>eate("a", "b")</warning>)
  }

  @Suppress("UNUSED_PARAMETER")
  fun <A, B> takePair(pair: Pair<A, B>?) {
    // do nothing
  }
}
