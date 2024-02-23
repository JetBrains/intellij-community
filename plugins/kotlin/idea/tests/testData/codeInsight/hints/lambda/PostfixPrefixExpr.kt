// MODE: return
fun bar() {
    var test = 0
    run {
        test
        test++/*<# ^|[KotlinLambdasHintsProvider.kt:53]run #>*/
    }

    run {
        test
        ++test/*<# ^|[KotlinLambdasHintsProvider.kt:98]run #>*/
    }
}