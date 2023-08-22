// SHOULD_FAIL_WITH: Callable reference transformation is not supported: ::foo
// AFTER-WARNING: Parameter 'f' is never used
// AFTER-WARNING: Parameter 'f' is never used
// AFTER-WARNING: Variable 'x' is never used
fun foo(f: (Int, <caret>Boolean) -> String) {

}

fun baz(f: (Int, Boolean) -> String) {
    val x = ::foo
}