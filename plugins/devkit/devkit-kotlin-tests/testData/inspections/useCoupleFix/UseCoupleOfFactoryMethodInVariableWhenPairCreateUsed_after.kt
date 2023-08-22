import com.intellij.openapi.util.Couple
import com.intellij.openapi.util.Pair

@Suppress("UNUSED_VARIABLE")
class UseCoupleOfFactoryMethodInVariableWhenPairCreateUsed {
  fun any() {
    val any: Pair<String, String> = Couple.of("a", "b")
  }
}
