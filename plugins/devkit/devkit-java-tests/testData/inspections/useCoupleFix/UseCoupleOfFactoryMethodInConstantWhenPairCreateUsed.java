import com.intellij.openapi.util.Pair;

class UseCoupleOfFactoryMethodInConstantWhenPairCreateUsed {
  private static final <warning descr="Replace with 'Couple<String>'">Pair<String, String></warning> ANY = <warning descr="Replace with 'Couple.of()'">Pair.cr<caret>eate("a", "b")</warning>;
}
