// MODE: return
val x = run foo@{
    println("foo")
    1/*<# ^|[Label.kt:32]foo #>*/
}