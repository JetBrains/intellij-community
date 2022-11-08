import com.intellij.openapi.util.Pair;

class UseCoupleOfFactoryMethodInVariableWhenPairCreateUsed {
  void any() {
    <warning descr="Replace with 'Couple<String>'">Pair<String, String></warning> any = Pair.<warning descr="Replace with 'Couple.of()'">cr<caret>eate</warning>("a", "b");
  }
}
