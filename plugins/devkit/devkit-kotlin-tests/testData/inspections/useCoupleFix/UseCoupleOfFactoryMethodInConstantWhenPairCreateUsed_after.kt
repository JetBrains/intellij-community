import com.intellij.openapi.util.Couple
import com.intellij.openapi.util.Pair

object UseCoupleOfFactoryMethodInConstantWhenPairCreateUsed {
  private val ANY: Pair<String, String> = Couple.of("a", "b")
}
