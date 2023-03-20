import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Pair;

class UseCoupleOfFactoryMethodInVariableWhenPairCreateUsed {
  void any() {
    Pair<String, String> any = Couple.of("a", "b");
  }
}
