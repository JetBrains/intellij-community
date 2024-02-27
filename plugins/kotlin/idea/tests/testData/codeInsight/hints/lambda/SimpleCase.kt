// MODE: return
val x = run {
    println("foo")
    1/*<# ^|[file.kt:28]run #>*/
}