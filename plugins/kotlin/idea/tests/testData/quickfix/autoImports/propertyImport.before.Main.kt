// "Import property 'someTestProp'" "true"
// ERROR: Unresolved reference: someTestProp
/* IGNORE_FIR */
package test

fun foo() {
    <caret>someTestProp
}
