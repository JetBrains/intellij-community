import com.intellij.openapi.util.Couple
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.Pair.create

class UseCoupleOfFactoryMethodInVariableWhenPairCreateUsed {
  fun any() {
    takePair(Couple.of("a", "b"))
  }

  @Suppress("UNUSED_PARAMETER")
  fun <A, B> takePair(pair: Pair<A, B>?) {
    // do nothing
  }
}
