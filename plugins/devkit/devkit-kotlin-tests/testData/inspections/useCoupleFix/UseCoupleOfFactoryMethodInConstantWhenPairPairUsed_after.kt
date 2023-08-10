import com.intellij.openapi.util.Couple
import com.intellij.openapi.util.Pair

object UseCoupleOfFactoryMethodInConstantWhenPairPairUsed {
  private val ANY: Pair<String, String> = Couple.of("a", "b")
}
