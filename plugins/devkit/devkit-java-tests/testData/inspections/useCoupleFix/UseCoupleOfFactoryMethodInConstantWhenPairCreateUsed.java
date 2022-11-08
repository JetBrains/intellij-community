import com.intellij.openapi.util.Pair;

class UseCoupleOfFactoryMethodInConstantWhenPairCreateUsed {
  private static final <warning descr="Replace with 'Couple<String>'">Pair<String, String></warning> ANY = Pair.<warning descr="Replace with 'Couple.of()'">cr<caret>eate</warning>("a", "b");
}
