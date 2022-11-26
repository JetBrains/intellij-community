// "Import extension function 'State.getValue'" "true"
// WITH_STDLIB
// ERROR: Type 'TypeVariable(R)' has no method 'getValue(Nothing?, KProperty<*>)' and thus it cannot serve as a delegate

package import

import base.State

fun test() {
    val y by <caret>run {
        State("Inside run")
    }
}

/* IGNORE_FIR */