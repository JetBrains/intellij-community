// IS_APPLICABLE: false
fun foo() {
    val x = bar<caret>(1)
}

fun bar(t: Int): Int = 1