import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Couple;

class PairUsedInsteadOfCouple {

  private static final <warning descr="Replace with 'Couple<String>'">Pair<String, String></warning> STRING_PAIR_TYPE = null;
  private static final <warning descr="Replace with 'Couple<Integer>'">Pair<Integer, Integer></warning> INTEGER_PAIR_TYPE = null;
  private static final <warning descr="Replace with 'Couple<String>'">Pair<String, String></warning> STRING_PAIR_CREATE = Pair.<warning descr="Replace with 'Couple.of()'">create</warning>("a", "b");
  private static final <warning descr="Replace with 'Couple<Integer>'">Pair<Integer, Integer></warning> INTEGER_PAIR = Pair.<warning descr="Replace with 'Couple.of()'">create</warning>(1, 2);
  private static final <warning descr="Replace with 'Couple<String>'">Pair<String, String></warning> STRING_PAIR = Pair.<warning descr="Replace with 'Couple.of()'">pair</warning>("a", "b");
  private static final <warning descr="Replace with 'Couple<Integer>'">Pair<Integer, Integer></warning> INTEGER_PAIR_CREATE = Pair.<warning descr="Replace with 'Couple.of()'">pair</warning>(1, 2);
  private static final <warning descr="Replace with 'Couple<Integer>'">Pair<Integer, Integer></warning> PAIR_TYPE_AND_COUPLE_VALUE = Couple.of(1, 2);

  private static final Pair<String, Integer> STRING_TO_INTEGER_PAIR_CREATE = Pair.create("a", 2); // correct
  private static final Pair<String, Integer> STRING_TO_INTEGER_PAIR = Pair.pair("a", 2); // correct
  private static final Couple<String> STRING_COUPLE_CONSTANT = Couple.of("a", "b"); // correct
  private static final Couple<Integer> INTEGER_COUPLE_CONSTANT = Couple.of(1, 2); // correct

  void any() {
    // variables:
    <warning descr="Replace with 'Couple<String>'">Pair<String, String></warning> pair1 = null;
    <warning descr="Replace with 'Couple<Integer>'">Pair<Integer, Integer></warning> pair2 = null;
    <warning descr="Replace with 'Couple<String>'">Pair<String, String></warning> pair3 = Pair.<warning descr="Replace with 'Couple.of()'">create</warning>("a", "b");
    <warning descr="Replace with 'Couple<Integer>'">Pair<Integer, Integer></warning> pair4 = Pair.<warning descr="Replace with 'Couple.of()'">create</warning>(1, 2);
    <warning descr="Replace with 'Couple<String>'">Pair<String, String></warning> pair5 = Pair.<warning descr="Replace with 'Couple.of()'">pair</warning>("a", "b");
    <warning descr="Replace with 'Couple<Integer>'">Pair<Integer, Integer></warning> pair6 = Pair.<warning descr="Replace with 'Couple.of()'">pair</warning>(1, 2);
    <warning descr="Replace with 'Couple<Integer>'">Pair<Integer, Integer></warning> pair7 = Couple.of(1, 2);

    Pair<String, Integer> pair8 = Pair.create("a", 2); // correct
    Pair<String, Integer> pair9 = Pair.pair("a", 2); // correct
    Couple<String> couple1 = Couple.of("a", "b"); // correct
    Couple<Integer> couple2 = Couple.of(1, 2); // correct

    // parameters:
    takePair(Pair.<warning descr="Replace with 'Couple.of()'">create</warning>("a", "b"));
    takePair(Pair.<warning descr="Replace with 'Couple.of()'">create</warning>(1, 2));
    takePair(Couple.of(1, 2));

    takePair(Pair.create("a", 2)); // correct
    takePair(Couple.of("a", "b")); // correct
    takePair(Couple.of(1, 2)); // correct
  }

  <A, B> void takePair(Pair<A, B> pair) {
    // do nothing
  }
}
