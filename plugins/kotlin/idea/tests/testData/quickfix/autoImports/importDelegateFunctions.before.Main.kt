// "Import extension functions 'Some.getValue', 'Some.setValue'" "true"
// WITH_STDLIB
// ERROR: Type 'Some' has no method 'getValue(Nothing?, KProperty<*>)' and thus it cannot serve as a delegate
// ERROR: Type 'Some' has no method 'setValue(Nothing?, KProperty<*>, [Error type: Error delegation type for Some("OK")])' and thus it cannot serve as a delegate for var (read-write property)

package kotlinpackage.one

import kotlinpackage.two.Some

fun test() {
    var x by <caret>Some("OK")
}

/* IGNORE_FIR */