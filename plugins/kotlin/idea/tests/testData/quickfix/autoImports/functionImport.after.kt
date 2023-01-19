// "Import function 'someTestFun'" "true"
// ERROR: Unresolved reference: someTestFun
/* IGNORE_FIR */
package functionimporttest

import functionimporttest.data.someTestFun

fun functionImportTest() {
    someTestFun()
}
