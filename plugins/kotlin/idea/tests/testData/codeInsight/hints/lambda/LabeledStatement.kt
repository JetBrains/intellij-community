// MODE: return
fun test() {
    run {
        val files: Any? = null
        run@
        12/*<# ^|[file.kt:37]run #>*/
    }

    run {
        val files: Any? = null
        run@12/*<# ^|[file.kt:109]run #>*/
    }
}