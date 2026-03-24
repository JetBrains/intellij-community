// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
class Context

context(c: Context)
fun fooCtx() {}

fun example() {
    with(/*<# [jar://kotlin-stdlib-sources.jar!/commonMain/kotlin/util/Standard.kt:*]receiver| = #>*/Context()) label@ {
        fooCtx(/*<# [contextParametersWithLabeledLambda.kt:92]c| = |[contextParametersWithLabeledLambda.kt:164]this@label| «  #>*/)
    }
}