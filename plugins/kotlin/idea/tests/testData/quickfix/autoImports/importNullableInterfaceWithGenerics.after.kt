import importInterface.data.TestInterface

// "Import class 'TestInterface'" "true"
// ERROR: Unresolved reference: TestInterface

fun test() {
    val a: TestInterface<String, Int>? = null
}
/* IGNORE_FIR */
