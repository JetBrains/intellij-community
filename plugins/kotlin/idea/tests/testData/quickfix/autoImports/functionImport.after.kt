// "Import function 'someTestFun'" "true"
// ERROR: Unresolved reference: someTestFun
package functionimporttest

import functionimporttest.data.someTestFun

fun functionImportTest() {
    someTestFun()
}
