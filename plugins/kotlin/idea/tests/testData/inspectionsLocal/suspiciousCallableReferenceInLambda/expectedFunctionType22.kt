// WITH_STDLIB
// PROBLEM: none

class C {
    fun foo(x1: Int, x2: Int, x3: Int, x4: Int, x5: Int, x6: Int, x7: Int, x8: Int, x9: Int, x10: Int, x11: Int, x12: Int, x13: Int, x14: Int, x15: Int, x16: Int, x17: Int, x18: Int, x19: Int, x20: Int, x21: Int, x22: Int) {}
}

fun bar(f: () -> Function22<Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Unit>) {}

fun test(c: C) {
    bar { c::foo }<caret>
}