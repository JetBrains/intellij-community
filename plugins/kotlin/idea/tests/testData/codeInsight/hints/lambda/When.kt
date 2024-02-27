// MODE: return
val x = run {
    when (true) {
        true -> 1/*<# ^|[KotlinLambdasHintsProvider.kt:28]run #>*/
        false -> 0/*<# ^|[KotlinLambdasHintsProvider.kt:28]run #>*/
    }
}