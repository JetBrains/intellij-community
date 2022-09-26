import importTrait.data.TestTrait

// "Import class 'TestTrait'" "true"
// ERROR: Unresolved reference: TestTrait

fun test() {
    val a: TestTrait<String, Int>? = null
}
/* IGNORE_FIR */
