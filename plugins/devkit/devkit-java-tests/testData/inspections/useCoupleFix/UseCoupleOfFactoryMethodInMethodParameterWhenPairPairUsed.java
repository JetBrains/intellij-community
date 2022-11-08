import com.intellij.openapi.util.Pair;

class UseCoupleOfFactoryMethodInVariableWhenPairPairUsed {
  void any() {
    takePair(Pair.<warning descr="Replace with 'Couple.of()'">pa<caret>ir</warning>("a", "b"));
  }

  <A, B> void takePair(Pair<A, B> pair) {
    // do nothing
  }
}
