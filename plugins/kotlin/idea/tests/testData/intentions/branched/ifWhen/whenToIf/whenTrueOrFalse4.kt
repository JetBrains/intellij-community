// PRIORITY: LOW
// AFTER-WARNING: Parameter 'i' is never used
fun foo(b: Boolean?) {
    <caret>when (b) {
        true -> print(1)
        false -> print(2)
        null -> print(3)
    }
}

fun print(i: Int) {}