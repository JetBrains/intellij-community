import com.intellij.openapi.util.Pair;

class UseCoupleOfFactoryMethodInVariableWhenPairPairUsed {
  void any() {
    <warning descr="Replace with 'Couple<String>'">Pair<String, String></warning> any = Pair.<warning descr="Replace with 'Couple.of()'">pa<caret>ir</warning>("a", "b");
  }
}
