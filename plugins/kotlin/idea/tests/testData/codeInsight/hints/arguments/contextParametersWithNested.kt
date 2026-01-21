// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
class Context

fun build(initializer: Context.() -> Unit) {

}

context(c: Context)
fun fooCtx() {}

fun example() {
    build {
        fooCtx(/*<# [contextParametersWithNested.kt:141]c| = |[contextParametersWithNested.kt:196]this #>*/)
        buildString {
            fooCtx(/*<# [contextParametersWithNested.kt:141]c| = |[contextParametersWithNested.kt:196]this@build #>*/)
        }
    }
}