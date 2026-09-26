// HIGHLIGHT: WARNING
// FIX: Remove redundant 'if' expression
fun foo(arg: Any?): Any? {
    return <caret>if (arg != null) arg else null
}