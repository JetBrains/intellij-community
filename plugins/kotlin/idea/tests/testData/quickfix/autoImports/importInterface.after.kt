import importInterface.data.TestInterface

// "Import class 'TestInterface'" "true"
// ERROR: Unresolved reference: TestInterface

fun test() {
    val a = <caret>TestInterface
}
/* IGNORE_FIR */
