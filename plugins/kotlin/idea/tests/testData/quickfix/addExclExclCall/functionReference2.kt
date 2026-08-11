// "Add non-null asserted (foo?.bar!!) call" "true"
// K2_ERROR: UNSAFE_CALLABLE_REFERENCE
class Foo {
    val bar = Bar()
}

class Bar {
    fun f() = 1
}

fun test(foo: Foo?) {
    val f = foo?.bar::f<caret>
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix