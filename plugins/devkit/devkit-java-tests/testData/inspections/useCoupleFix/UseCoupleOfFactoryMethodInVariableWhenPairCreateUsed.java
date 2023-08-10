import com.intellij.openapi.util.Pair;

class UseCoupleOfFactoryMethodInVariableWhenPairCreateUsed {
  void any() {
    <warning descr="Replace with 'Couple<String>'">Pair<String, String></warning> any = <warning descr="Replace with 'Couple.of()'">Pair.cr<caret>eate("a", "b")</warning>;
  }
}
