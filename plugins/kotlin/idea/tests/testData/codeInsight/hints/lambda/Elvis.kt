// MODE: return
fun foo() {
    run {
        val length: Int? = null
        length ?: 0/*<# ^|[file.kt:36]run #>*/
    }
}