// IS_APPLICABLE: false
fun foo() {
    val x = bar<caret><_>("x")
}

fun <T> bar(t: T): Int = 1