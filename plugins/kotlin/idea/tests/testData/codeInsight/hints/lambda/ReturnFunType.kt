// MODE: return
fun test() = run {
    val a = 1
    { a }/*<# ^|[file.kt:33]run #>*/
}