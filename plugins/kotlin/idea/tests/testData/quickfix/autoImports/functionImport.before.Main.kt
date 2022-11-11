// "Import function 'someTestFun'" "true"
// ERROR: Unresolved reference: someTestFun
/* IGNORE_FIR */
package functionimporttest

fun functionImportTest() {
    <caret>someTestFun()
}
