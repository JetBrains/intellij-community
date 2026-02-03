// MODE: return
fun foo() {
    run {
        val length: Int? = null
        length ?: 0/*<# ^|[Elvis.kt:36]run #>*/
    }
}