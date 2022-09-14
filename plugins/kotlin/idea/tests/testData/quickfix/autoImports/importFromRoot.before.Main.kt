// "Import class 'RootClass'" "true"
// ERROR: Unresolved reference: RootClass
package non.root.name

fun test() {
    RootClass<caret>()
}
/* IGNORE_FIR */