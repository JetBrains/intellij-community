// "Remove useless is check" "true"
interface Base
interface Derived: Base

fun foo(bar: Base):Int {
    return when (bar) {
        is Derived -> 0
        <caret>!is Base -> 42
        else -> 1
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveUselessIsCheckFixForWhen
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveUselessIsCheckFixForWhen