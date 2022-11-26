import com.intellij.openapi.util.Pair

@Suppress("UNUSED_VARIABLE")
class UseCoupleTypeInVariable {
  fun any() {
    val any: <warning descr="Replace with 'Couple<String>'">Pa<caret>ir<String, String>?</warning> = null
  }
}
