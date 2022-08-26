// "Import class 'RootClass'" "true"
// ERROR: Unresolved reference: RootClass
package non.root.name

import RootClass

fun test() {
    RootClass()
}
/* IGNORE_FIR */