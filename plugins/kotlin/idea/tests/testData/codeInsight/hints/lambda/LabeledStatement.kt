// MODE: return
fun test() {
    run {
        val files: Any? = null
        run@
        12/*<# ^|[KotlinLambdasHintsProvider.kt:37]run #>*/
    }

    run {
        val files: Any? = null
        run@12/*<# ^|[KotlinLambdasHintsProvider.kt:109]run #>*/
    }
}