// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
class Foo {
    fun test() {
        bar(/*<# [contextParametersReceiverFunction.kt:159]f| = |[contextParametersReceiverFunction.kt:75]this| «  #>*/)
    }
}

fun Foo.test() {
    bar(/*<# [contextParametersReceiverFunction.kt:159]f| = |[contextParametersReceiverFunction.kt:125]this| «  #>*/)
}

context(f: Foo)
fun bar() {}