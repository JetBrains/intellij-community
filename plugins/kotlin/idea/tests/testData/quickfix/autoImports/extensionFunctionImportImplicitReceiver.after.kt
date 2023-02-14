// "Import extension function 'String.someFun'" "true"
// ERROR: Unresolved reference: someFun
package testingExtensionFunctionsImport

import testingExtensionFunctionsImport.data.someFun

fun String.some() {
    <caret>someFun()
}
/* IGNORE_FIR */