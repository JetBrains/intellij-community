import com.intellij.openapi.util.Pair;

import static com.intellij.openapi.util.Pair.create;

class UseCoupleOfFactoryMethodInVariableWhenPairCreateUsed {
  void any() {
    takePair(<warning descr="Replace with 'Couple.of()'">cr<caret>eate("a", "b")</warning>);
  }

  <A, B> void takePair(Pair<A, B> pair) {
    // do nothing
  }
}
