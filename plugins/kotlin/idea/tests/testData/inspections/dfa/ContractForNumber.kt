// WITH_STDLIB
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

fun foo(number: Number) {
    if (myPredicate(number))
        println(number)
}

@OptIn(ExperimentalContracts::class)
fun myPredicate(n: Number): Boolean {
    contract { returns(true) implies (n is Int) }
    return n is Int
}