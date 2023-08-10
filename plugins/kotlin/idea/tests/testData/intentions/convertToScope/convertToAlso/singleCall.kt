// WITH_STDLIB
// AFTER-WARNING: Variable 'c' is never used

class C {
    fun foo() {}
}

fun test() {
    val c = C()
    c.foo()<caret>
}