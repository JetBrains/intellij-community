// AFTER-WARNING: Parameter 'args' is never used
// AFTER-WARNING: Parameter 'f' is never used
// AFTER-WARNING: Variable 'p' is never used
class Foo(f: () -> Unit)

fun main(args: String) {
    Foo(fun<caret>() {
        val p = 1
    })
}