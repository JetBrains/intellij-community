import com.intellij.openapi.util.Pair;

class UseCoupleOfFactoryMethodInVariableWhenPairCreateUsed {
  void any() {
    takePair(Pair.<warning descr="Replace with 'Couple.of()'">cr<caret>eate</warning>("a", "b"));
  }

  <A, B> void takePair(Pair<A, B> pair) {
    // do nothing
  }
}
