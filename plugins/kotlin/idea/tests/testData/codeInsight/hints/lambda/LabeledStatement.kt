// MODE: return
fun test() {
    run {
        val files: Any? = null
        run@
        12/*<# ^|[LabeledStatement.kt:37]run #>*/
    }

    run {
        val files: Any? = null
        run@12/*<# ^|[LabeledStatement.kt:109]run #>*/
    }
}