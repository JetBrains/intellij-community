// FIX: Replace 'if' expression with safe cast expression
// HIGHLIGHT: WARNING
class My(val x: Int)

fun foo(arg: Any?): My? {
    return <caret>if (arg is My) arg else null
}