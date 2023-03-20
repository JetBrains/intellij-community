import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Pair;

class UseCoupleOfFactoryMethodInConstantWhenPairCreateUsed {
  private static final Pair<String, String> ANY = Couple.of("a", "b");
}
