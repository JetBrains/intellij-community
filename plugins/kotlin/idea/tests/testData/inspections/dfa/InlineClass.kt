// WITH_STDLIB
import kotlin.random.Random

@JvmInline
value class Ic(val x: Int)

fun main() {
    val ic = Ic(1)
    val a: Any = ic
    val b: Any = ic
    println(a === b)
    println(<warning descr="Condition 'a is Ic' is always true">a is Ic</warning>)
    val c = if (Random.nextBoolean()) a else b
    println(<warning descr="Condition 'c is Ic' is always true">c is Ic</warning>)
    println(<warning descr="Condition 'c === a || c === b' is always true">c === a || <warning descr="Condition 'c === b' is always true when reached">c === b</warning></warning>)
    println(a == b)
    println(<warning descr="Condition 'c == a || c == b' is always true">c == a || <warning descr="Condition 'c == b' is always true when reached">c == b</warning></warning>)
    val a1: Any = ic <warning descr="[USELESS_CAST] No cast needed">as Any</warning>
    val b1: Any = ic <warning descr="[USELESS_CAST] No cast needed">as Any</warning>
    println(a1 === b1)
}