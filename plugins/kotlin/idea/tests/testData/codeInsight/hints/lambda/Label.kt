// MODE: return
val x = run foo@{
    println("foo")
    1/*<# ^|[file.kt:32]foo #>*/
}