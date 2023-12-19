// MODE: return
val x = run foo@{
    println("foo")
    1/*<# ^|[temp:///src/KotlinLambdasHintsProvider.kt:32]foo #>*/
}