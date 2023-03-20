// "Import extension function 'String.someFun'" "true"
// ERROR: Unresolved reference: someFun
package testingExtensionFunctionsImport

fun String.some() {
    <caret>someFun()
}
/* IGNORE_FIR */