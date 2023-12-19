// MODE: return
fun foo() {
    run {
        val length: Int? = null
        length ?: 0/*<# ^|[temp:///src/KotlinLambdasHintsProvider.kt:36]run #>*/
    }
}