// MODE: return
val x = run hello@{
    if (true) {
    }

    run { // Two hints here
        when (true) {
            true -> 1/*<# ^|[KotlinLambdasHintsProvider.kt:67]run #>*/
            false -> 0/*<# ^|[KotlinLambdasHintsProvider.kt:67]run #>*/
        }
    }/*<# ^|[KotlinLambdasHintsProvider.kt:34]hello #>*/
}