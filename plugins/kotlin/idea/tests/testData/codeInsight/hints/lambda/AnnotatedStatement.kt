// MODE: return
@Target(AnnotationTarget.EXPRESSION)
annotation class Some

fun test() {
    run {
        val files: Any? = null
        @Some
        12/*<# ^|[KotlinLambdasHintsProvider.kt:97]run #>*/
    }

    run {
        val files: Any? = null
        @Some 12/*<# ^|[KotlinLambdasHintsProvider.kt:170]run #>*/
    }
}