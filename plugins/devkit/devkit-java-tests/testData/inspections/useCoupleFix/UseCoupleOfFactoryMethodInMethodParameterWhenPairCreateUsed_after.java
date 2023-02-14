import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Pair;

class UseCoupleOfFactoryMethodInVariableWhenPairCreateUsed {
  void any() {
    takePair(Couple.of("a", "b"));
  }

  <A, B> void takePair(Pair<A, B> pair) {
    // do nothing
  }
}
