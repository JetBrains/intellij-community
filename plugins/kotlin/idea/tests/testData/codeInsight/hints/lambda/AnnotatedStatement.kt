// MODE: return
@Target(AnnotationTarget.EXPRESSION)
annotation class Some

fun test() {
    run {
        val files: Any? = null
        @Some
        12/*<# ^|[AnnotatedStatement.kt:97]run #>*/
    }

    run {
        val files: Any? = null
        @Some 12/*<# ^|[AnnotatedStatement.kt:170]run #>*/
    }
}