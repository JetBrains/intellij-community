// WITH_STDLIB
// PROBLEM: none

class C {
    fun foo() {}
}

fun bar(f: () -> Function<Unit>) {}

fun test(c: C) {
    bar { c::foo }<caret>
}