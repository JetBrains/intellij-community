// "Import class 'Some'" "true"
// WITH_STDLIB
// ERROR: Unresolved reference: Some

package kotlinpackage.one

import kotlinpackage.two.Some

fun test() {
    var x by <caret>Some("OK")
}

/* IGNORE_FIR */