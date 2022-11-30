// WITH_STDLIB
// PROBLEM: none

class C {
    fun foo() {}
}

fun <T : Function<*>> bar(f: () -> T) {}

fun test(c: C) {
    bar { c::foo }<caret>
}