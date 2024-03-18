// MODE: return
val x = run {
    println(1)
    if (true) 1 else { 0 }/*<# ^|[temp:///src/KotlinLambdasHintsProvider.kt:28]run #>*/
}