package testing

import some.Some

fun foo(): Some = Some()

fun testing() {
    foo()["str"]
}<caret>

// IGNORE_K2