// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
class Context

context(c: Context)
fun fooCtx() {}

fun example() {
    with(/*<# [jar://kotlin-stdlib-sources.jar!/commonMain/kotlin/util/Standard.kt:*]receiver| = #>*/Context()) {
        fooCtx(/*<# [contextParametersWithThis.kt:92]c| = |[contextParametersWithThis.kt:157]this #>*/)
    }
}