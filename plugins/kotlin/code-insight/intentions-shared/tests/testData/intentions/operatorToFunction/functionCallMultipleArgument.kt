// AFTER-WARNING: Parameter 'i' is never used
// AFTER-WARNING: Parameter 'j' is never used
// AFTER-WARNING: Variable 'test' is never used
class bar() {
    operator fun invoke(i: Any?, j: Any?) : Boolean {
        return true
    }
}

fun foo(i: Any?, j: Any?) {
    val test = bar()<caret>(i, j)
}