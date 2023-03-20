package accessor.call

import accessor.ContainsAccessMethod

fun foo(): String {
    return ContainsAccessMethod().<caret>getStr()
}