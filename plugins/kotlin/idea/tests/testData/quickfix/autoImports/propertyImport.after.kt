// "Import property 'someTestProp'" "true"
// ERROR: Unresolved reference: someTestProp
/* IGNORE_FIR */
package test

import test.data.someTestProp

fun foo() {
    someTestProp
}
