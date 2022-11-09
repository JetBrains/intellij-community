import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Couple;

import static com.intellij.openapi.util.Pair.create;
import static com.intellij.openapi.util.Pair.pair;

class PairUsedInsteadOfCouple {

  private static final <warning descr="Replace with 'Couple<String>'">Pair<String, String></warning> STRING_PAIR_TYPE = null;
  private static final <warning descr="Replace with 'Couple<Integer>'">Pair<Integer, Integer></warning> INTEGER_PAIR_TYPE = null;
  private static final <warning descr="Replace with 'Couple<String>'">Pair<String, String></warning> STRING_PAIR_CREATE = <warning descr="Replace with 'Couple.of()'">Pair.create("a", "b")</warning>;
  private static final <warning descr="Replace with 'Couple<Integer>'">Pair<Integer, Integer></warning> INTEGER_PAIR = <warning descr="Replace with 'Couple.of()'">Pair.create(1, 2)</warning>;
  private static final <warning descr="Replace with 'Couple<String>'">Pair<String, String></warning> STRING_PAIR = <warning descr="Replace with 'Couple.of()'">Pair.pair("a", "b")</warning>;
  private static final <warning descr="Replace with 'Couple<Integer>'">Pair<Integer, Integer></warning> INTEGER_PAIR_CREATE = <warning descr="Replace with 'Couple.of()'">Pair.pair(1, 2)</warning>;
  private static final <warning descr="Replace with 'Couple<Integer>'">Pair<Integer, Integer></warning> PAIR_TYPE_AND_COUPLE_VALUE = Couple.of(1, 2);

  private static final Pair<String, Integer> STRING_TO_INTEGER_PAIR_CREATE = Pair.create("a", 2); // correct
  private static final Pair<String, Integer> STRING_TO_INTEGER_PAIR = Pair.pair("a", 2); // correct
  private static final Couple<String> STRING_COUPLE_CONSTANT = Couple.of("a", "b"); // correct
  private static final Couple<Integer> INTEGER_COUPLE_CONSTANT = Couple.of(1, 2); // correct

  void any() {
    // variables:
    <warning descr="Replace with 'Couple<String>'">Pair<String, String></warning> pair1 = null;
    <warning descr="Replace with 'Couple<Integer>'">Pair<Integer, Integer></warning> pair2 = null;
    <warning descr="Replace with 'Couple<String>'">Pair<String, String></warning> pair3 = <warning descr="Replace with 'Couple.of()'">Pair.create("a", "b")</warning>;
    <warning descr="Replace with 'Couple<Integer>'">Pair<Integer, Integer></warning> pair4 = <warning descr="Replace with 'Couple.of()'">Pair.create(1, 2)</warning>;
    <warning descr="Replace with 'Couple<Integer>'">Pair<Integer, Integer></warning> pair5 = <warning descr="Replace with 'Couple.of()'">create(1, 2)</warning>;
    <warning descr="Replace with 'Couple<String>'">Pair<String, String></warning> pair6 = <warning descr="Replace with 'Couple.of()'">Pair.pair("a", "b")</warning>;
    <warning descr="Replace with 'Couple<Integer>'">Pair<Integer, Integer></warning> pair7 = <warning descr="Replace with 'Couple.of()'">Pair.pair(1, 2)</warning>;
    <warning descr="Replace with 'Couple<Integer>'">Pair<Integer, Integer></warning> pair8 = <warning descr="Replace with 'Couple.of()'">pair(1, 2)</warning>;
    <warning descr="Replace with 'Couple<Integer>'">Pair<Integer, Integer></warning> pair9 = Couple.of(1, 2);

    Pair<String, Integer> pair10 = Pair.create("a", 2); // correct
    Pair<String, Integer> pair11 = Pair.pair("a", 2); // correct
    Couple<String> couple1 = Couple.of("a", "b"); // correct
    Couple<Integer> couple2 = Couple.of(1, 2); // correct

    // parameters:
    takePair(<warning descr="Replace with 'Couple.of()'">Pair.create("a", "b")</warning>);
    takePair(<warning descr="Replace with 'Couple.of()'">Pair.create(1, 2)</warning>);
    takePair(<warning descr="Replace with 'Couple.of()'">create(1, 2)</warning>);
    takePair(<warning descr="Replace with 'Couple.of()'">Pair.pair("a", "b")</warning>);
    takePair(<warning descr="Replace with 'Couple.of()'">Pair.pair(1, 2)</warning>);
    takePair(<warning descr="Replace with 'Couple.of()'">pair(1, 2)</warning>);

    takePair(Pair.create("a", 2)); // correct
    takePair(Couple.of("a", "b")); // correct
    takePair(Couple.of(1, 2)); // correct
  }

  <A, B> void takePair(Pair<A, B> pair) {
    // do nothing
  }
}
