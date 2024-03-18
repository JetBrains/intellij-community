// MODE: return
val x = run {
    when (true) {
        true -> 1/*<# ^|[temp:///src/KotlinLambdasHintsProvider.kt:28]run #>*/
        false -> 0/*<# ^|[temp:///src/KotlinLambdasHintsProvider.kt:28]run #>*/
    }
}