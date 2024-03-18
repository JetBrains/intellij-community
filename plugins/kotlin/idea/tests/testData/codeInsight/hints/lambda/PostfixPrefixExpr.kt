// MODE: return
fun bar() {
    var test = 0
    run {
        test
        test++/*<# ^|[temp:///src/KotlinLambdasHintsProvider.kt:53]run #>*/
    }

    run {
        test
        ++test/*<# ^|[temp:///src/KotlinLambdasHintsProvider.kt:98]run #>*/
    }
}