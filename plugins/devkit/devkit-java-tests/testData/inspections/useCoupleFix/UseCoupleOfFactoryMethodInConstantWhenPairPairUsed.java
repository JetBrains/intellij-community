import com.intellij.openapi.util.Pair;

class UseCoupleOfFactoryMethodInConstantWhenPairPairUsed {
  private static final <warning descr="Replace with 'Couple<String>'">Pair<String, String></warning> ANY = Pair.<warning descr="Replace with 'Couple.of()'">pa<caret>ir</warning>("a", "b");
}
