// "Add non-null asserted (!!) call" "true"
class Foo {
    fun f() = 1
}

fun test(foo: Foo?) {
    val f = foo::f<caret>
}
// TODO: Enable when FIR reports UNSAFE_CALL for function reference on nullable (currently UNRESOLVED_REFERENCE)
/* IGNORE_FIR */

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix