// IS_APPLICABLE: false
fun foo() {
    val x = bar<_>("<caret>x")
}

fun <T> bar(t: T): Int = 1