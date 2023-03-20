import com.intellij.openapi.util.Pair

@Suppress("UNUSED_PARAMETER")
class UseCoupleTypeInMethodParameter {
  fun takePair(pair: <warning descr="Replace with 'Couple<String>'">Pa<caret>ir<String, String></warning>) {
    // do nothing
  }
}
