import kotlin.String

fun foo() {
    <caret>
}

import kotlin.Int

// EXIST: if
// EXIST: return
// EXIST: val
// EXIST: var
// EXIST: when
