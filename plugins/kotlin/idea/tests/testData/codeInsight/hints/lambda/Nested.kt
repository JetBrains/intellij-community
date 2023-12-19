// MODE: return
val x = run hello@{
    if (true) {
    }

    run { // Two hints here
        when (true) {
            true -> 1/*<# ^|[temp:///src/KotlinLambdasHintsProvider.kt:67]run #>*/
            false -> 0/*<# ^|[temp:///src/KotlinLambdasHintsProvider.kt:67]run #>*/
        }
    }/*<# ^|[temp:///src/KotlinLambdasHintsProvider.kt:34]hello #>*/
}