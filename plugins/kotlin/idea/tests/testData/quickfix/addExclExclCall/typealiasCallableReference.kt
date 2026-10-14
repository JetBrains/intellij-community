// "Add non-null asserted (NFoo!!) call" "false"
// K2_ERROR: UNSAFE_CALLABLE_REFERENCE
// K2_AFTER_ERROR: UNSAFE_CALLABLE_REFERENCE
class Foo {
    fun f() = 1
}

typealias NFoo = Foo?

fun test() {
    val f = NFoo::f<caret>
}
