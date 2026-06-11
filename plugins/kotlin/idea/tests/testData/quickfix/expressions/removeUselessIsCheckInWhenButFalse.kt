// "Remove redundant 'is' check" "true"
// K2_ERROR: Check for instance is always 'false'.

interface Base
interface Derived: Base

fun foo(bar: Base):Int {
    return when (bar) {
        is Derived -> 0
        <caret>is String -> 42
        else -> 1
    }
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveUselessIsCheckFixForWhen