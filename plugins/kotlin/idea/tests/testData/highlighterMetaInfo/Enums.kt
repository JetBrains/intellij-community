// FIR_IDENTICAL
package testing

enum class Test {
    FIRST,
    SECOND
}

enum class Type(val id: Int) {
    FIRST(1),
    SECOND(2)
}

fun testing(t1: Test, t2: Test): Test {
    if (t1 != t2) return Test.FIRST
    return testing(Test.FIRST, Test.SECOND)
}