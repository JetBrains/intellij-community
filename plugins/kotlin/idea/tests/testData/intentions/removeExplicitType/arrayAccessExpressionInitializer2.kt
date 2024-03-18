// IS_APPLICABLE: false
// ERROR: Unresolved reference: t
// IGNORE_K1
class A {
    operator fun <T> get(index: Int): T = t
}

fun test(a: A): <caret>String = a[0]