// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
class Context

fun build(initializer: Context.() -> Unit) {

}

context(c: Context)
fun fooCtx() {}

fun example() {
    b@ build {
        fooCtx(/*<# [contextParametersWithNestedLabeled.kt:141]c| = |[contextParametersWithNestedLabeled.kt:199]this| «  #>*/)
        buildString {
            fooCtx(/*<# [contextParametersWithNestedLabeled.kt:141]c| = |[contextParametersWithNestedLabeled.kt:199]this@b| «  #>*/)
        }
    }
}