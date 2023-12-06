// AFTER-WARNING: Variable 'foo' is never used
fun <T> foo(x: T & Any) {
    val foo: <caret>() -> T & Any = { x }
}
