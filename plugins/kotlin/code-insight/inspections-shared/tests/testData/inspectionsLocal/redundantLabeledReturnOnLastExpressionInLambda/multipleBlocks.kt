// WITH_STDLIB

fun foo() {
    listOf(1,2,3).find {
        if (it > 0) {
            <caret>return@find true
        } else {
            false
        }
    }
}