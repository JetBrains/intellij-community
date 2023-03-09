// "Import class 'TestInterface'" "true"
// ERROR: Unresolved reference: TestInterface

fun test() {
    val a: <caret>TestInterface<String, Int>? = null
}
/* IGNORE_FIR */
