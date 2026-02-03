// MODE: return
fun test() = run {
    val a = 1
    { a }/*<# ^|[ReturnFunType.kt:33]run #>*/
}