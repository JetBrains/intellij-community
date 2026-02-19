// ERROR: Variable 'p' must be initialized
// K2_ERROR: Variable 'p' must be initialized.
// PROBLEM: none

class Foo {
    val p: Any = p<caret>
}
