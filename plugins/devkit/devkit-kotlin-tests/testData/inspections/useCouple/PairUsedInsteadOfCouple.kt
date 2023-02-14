import com.intellij.openapi.util.Couple
import com.intellij.openapi.util.Pair

internal class PairUsedInsteadOfCouple {

  companion object {
    private val STRING_PAIR_TYPE: <warning descr="Replace with 'Couple<String>'">Pair<String, String>?</warning> = null
    private val INTEGER_PAIR_TYPE: <warning descr="Replace with 'Couple<Integer>'">Pair<Int, Int>?</warning> = null
    private val STRING_PAIR_CREATE = <warning descr="Replace with 'Couple.of()'">Pair.create("a", "b")</warning>
    private val INTEGER_PAIR = <warning descr="Replace with 'Couple.of()'">Pair.create(1, 2)</warning>
    private val STRING_PAIR = <warning descr="Replace with 'Couple.of()'">Pair.pair("a", "b")</warning>
    private val INTEGER_PAIR_CREATE = <warning descr="Replace with 'Couple.of()'">Pair.pair(1, 2)</warning>
    private val PAIR_TYPE_AND_COUPLE_VALUE: <warning descr="Replace with 'Couple<Integer>'">Pair<Int, Int></warning> = Couple.of(1, 2)
    private val STRING_TO_INTEGER_PAIR_CREATE = Pair.create("a", 2) // correct
    private val STRING_TO_INTEGER_PAIR = Pair.pair("a", 2) // correct
    private val STRING_COUPLE_CONSTANT = Couple.of("a", "b") // correct
    private val INTEGER_COUPLE_CONSTANT = Couple.of(1, 2) // correct
  }

  @Suppress("UNUSED_VARIABLE")
  fun any() {
    // variables:
    val pair1: <warning descr="Replace with 'Couple<String>'">Pair<String, String>?</warning> = null
    val pair2: <warning descr="Replace with 'Couple<Integer>'">Pair<Int, Int>?</warning> = null
    val pair3 = <warning descr="Replace with 'Couple.of()'">Pair.create("a", "b")</warning>
    val pair4 = <warning descr="Replace with 'Couple.of()'">Pair.create(1, 2)</warning>
    val pair5 = <warning descr="Replace with 'Couple.of()'">Pair.create(1, 2)</warning>
    val pair6 = <warning descr="Replace with 'Couple.of()'">Pair.pair("a", "b")</warning>
    val pair7 = <warning descr="Replace with 'Couple.of()'">Pair.pair(1, 2)</warning>
    val pair8 = <warning descr="Replace with 'Couple.of()'">Pair.pair(1, 2)</warning>
    val pair9: <warning descr="Replace with 'Couple<Integer>'">Pair<Int, Int></warning> = Couple.of(1, 2)
    val pair10 = Pair.create("a", 2) // correct
    val pair11 = Pair.pair("a", 2) // correct
    val couple1 = Couple.of("a", "b") // correct
    val couple2 = Couple.of(1, 2) // correct

    // parameters:
    takePair(<warning descr="Replace with 'Couple.of()'">Pair.create("a", "b")</warning>)
    takePair(<warning descr="Replace with 'Couple.of()'">Pair.create(1, 2)</warning>)
    takePair(<warning descr="Replace with 'Couple.of()'">Pair.create(1, 2)</warning>)
    takePair(<warning descr="Replace with 'Couple.of()'">Pair.pair("a", "b")</warning>)
    takePair(<warning descr="Replace with 'Couple.of()'">Pair.pair(1, 2)</warning>)
    takePair(<warning descr="Replace with 'Couple.of()'">Pair.pair(1, 2)</warning>)
    takePair(Pair.create("a", 2)) // correct
    takePair(Couple.of("a", "b")) // correct
    takePair(Couple.of(1, 2)) // correct
  }

  @Suppress("UNUSED_PARAMETER")
  private fun <A, B> takePair(pair: Pair<A, B>?) {
    // do nothing
  }
}