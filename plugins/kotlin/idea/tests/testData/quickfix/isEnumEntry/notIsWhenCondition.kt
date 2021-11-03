// "Remove '!is'" "false"
// ACTION: Add braces to 'when' entry
// ACTION: Add braces to all 'when' entries
// ACTION: Add remaining branches
// ACTION: Convert to block body
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Import members from 'Foo'
// ACTION: Introduce import alias
// ACTION: Introduce local variable
// ERROR: 'is' over enum entry is not allowed, use comparison instead
// ERROR: Use of enum entry names as types is not allowed, use enum type instead
enum class Foo { A }

fun test(foo: Foo): Int = when (foo) {
    !is <caret>Foo.A -> 1
    else -> 2
}
