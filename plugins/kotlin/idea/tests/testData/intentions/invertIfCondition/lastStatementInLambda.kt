// AFTER-WARNING: Variable 'v' is never used
fun foo() {
    val v: (Int) -> Unit = {
        <caret>if (it > 1) {
            bar()
        }
    }
}

fun bar(){}