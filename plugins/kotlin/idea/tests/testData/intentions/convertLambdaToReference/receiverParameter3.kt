// IS_APPLICABLE: false
// WITH_STDLIB
class A(s: String) {
    fun bar(s: String) {}
}

fun foo(f: (String) -> Unit) {}

fun test() {
    foo { s -> s.let(::A).bar(s) <caret>}
}