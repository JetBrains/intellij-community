// "Import function 'importedFunA'" "true"
// ERROR: Unresolved reference: importedFunA
/* IGNORE_FIR */
import editor.completion.apx.importedFunA
import editor.completion.apx.importedFunA as funA
fun context() {
    fun funA() {}
    <caret>importedFunA()
}
