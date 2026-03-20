// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
context(n: Int)
fun foo(): Int = n

fun bar(){
    context(/*<# [jar://kotlin-stdlib-sources.jar!/commonMain/kotlin/contextParameters/Context.kt:*]with| = #>*/0) {
        foo(/*<# [contextParametersInContext.kt:77]n| = |[contextParametersInContext.kt:131]context| «  #>*/)
    }
}