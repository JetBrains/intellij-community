// ERROR: Variable 'p' must be initialized
// PROBLEM: none
// K2_ERROR: UNINITIALIZED_VARIABLE

class Foo {
    val p: Any = p<caret>
}
