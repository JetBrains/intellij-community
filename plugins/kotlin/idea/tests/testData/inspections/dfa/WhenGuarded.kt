// WITH_STDLIB
// COMPILER_ARGUMENTS: -Xwhen-guards
interface A
interface B : A
class C : B

fun triviallyFalseGuard(n: Int): Int = when (n) {
    <warning descr="'when' branch is never reachable">-5</warning> if false -> 1
    else -> 0
}

fun whenNumberIfMutuallyExclusive(n: Int): Int = when (n) {
    1 -> 1
    n if n < 10 -> 0
    <warning descr="'when' branch is never reachable">n</warning> if <warning descr="Condition 'n < 5' is always false">n < 5</warning> -> 2
    <warning descr="'when' branch is never reachable">n</warning> if <warning descr="Condition 'n < 0' is always false">n < 0</warning> -> 2
    else -> 0
}

fun whenNumberIfNotMutuallyExclusive(n: Int): Int = when (n) {
    1 -> 1
    n if n < 10 -> 0
    n if <warning descr="Condition 'n > 5' is always true">n > 5</warning> -> 2
    <warning descr="'when' branch is never reachable">n</warning> if n > 2 -> 3
    else -> 0
}

fun whenWithIfBool(bool: Boolean): Int {
    return when (bool) {
            <warning descr="'when' branch is never reachable">bool</warning> if false -> 1
        false -> 0 // not simplifiable
        else -> -1
    }
}

fun whenIsIfTriviallyFalseGuard(a: A): Int = when (a) {
    <warning descr="'when' branch is never reachable">is B</warning> if false -> 1
    else -> 0
}

fun whenIsNegatedIfTriviallyFalseGuard(a: A): Int = when (a) {
    <warning descr="'when' branch is never reachable">!is B</warning> if false -> 1
    else -> 0
}

fun whenInRangeIfTriviallyFalseGuard(n: Int): Int = when (n) {
    <warning descr="'when' branch is never reachable">in 1..10</warning> if false -> 1
    else -> 0
}

fun f(a: A) {
    when (a) {
        is B if a !is C -> {}
        else -> {
            println(a as? C)
        }
    }
}

fun f1(a: A) {
    when(a) {
        is B if a is C -> {
            if (<warning descr="[USELESS_IS_CHECK]">a is C</warning>) {}
        }
        is B if <warning descr="[USELESS_IS_CHECK]">a is A</warning> -> {
            if (<warning descr="Condition 'a is C' is always false">a is C</warning>) {}
        }
        <warning descr="'when' branch is never reachable">is C</warning> -> {

        }
    }
}

fun main() {
    f(C())
}