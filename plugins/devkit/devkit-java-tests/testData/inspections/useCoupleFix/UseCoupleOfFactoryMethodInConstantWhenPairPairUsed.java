import com.intellij.openapi.util.Pair;

class UseCoupleOfFactoryMethodInConstantWhenPairPairUsed {
  private static final <warning descr="Replace with 'Couple<String>'">Pair<String, String></warning> ANY = <warning descr="Replace with 'Couple.of()'">Pair.pa<caret>ir("a", "b")</warning>;
}
