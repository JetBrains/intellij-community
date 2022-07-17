// "Change to 'return@foo'" "false"
// ACTION: Add braces to 'if' statement
// ACTION: Change return type of enclosing function 'test' to 'Unit?'
// ACTION: Do not show return expression hints
// DISABLE-ERRORS
inline fun foo(f: (Int) -> Int) {}

fun baz(): Int = 0

fun test() {
    foo { i ->
        if (i == 1) return null<caret>
        0
    }
}