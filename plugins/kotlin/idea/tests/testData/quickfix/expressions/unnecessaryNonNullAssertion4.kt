// "Remove unnecessary non-null assertion (!!)" "true"
class Foo {
    val foo: String = ""
}

fun bar(i: Int) {}

fun test(foo: Foo?) {
    bar(
        foo
        !!.foo!!<caret>.length
    )
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveExclExclCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveExclExclCallFix