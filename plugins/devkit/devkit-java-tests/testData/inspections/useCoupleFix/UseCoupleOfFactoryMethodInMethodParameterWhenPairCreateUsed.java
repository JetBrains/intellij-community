import com.intellij.openapi.util.Pair;

class UseCoupleOfFactoryMethodInVariableWhenPairCreateUsed {
  void any() {
    takePair(<warning descr="Replace with 'Couple.of()'">Pair.cr<caret>eate("a", "b")</warning>);
  }

  <A, B> void takePair(Pair<A, B> pair) {
    // do nothing
  }
}
