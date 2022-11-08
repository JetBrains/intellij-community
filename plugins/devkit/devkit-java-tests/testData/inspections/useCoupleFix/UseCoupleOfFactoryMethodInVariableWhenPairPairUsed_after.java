import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Pair;

class UseCoupleOfFactoryMethodInVariableWhenPairPairUsed {
  void any() {
    Pair<String, String> any = Couple.of("a", "b");
  }
}
