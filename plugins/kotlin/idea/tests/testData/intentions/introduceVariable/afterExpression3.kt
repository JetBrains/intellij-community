// AFTER-WARNING: Variable 'bar' is never used
fun foo() = 0

fun bar(x: Int, y: Int) = x + y

fun test() {
    foo()
    bar(
        x = 1,
        y = 2
    )<caret>
}