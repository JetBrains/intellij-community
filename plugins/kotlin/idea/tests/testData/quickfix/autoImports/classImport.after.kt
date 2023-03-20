import TestData.TestSample

// "Import class 'TestSample'" "true"
// ERROR: Unresolved reference: TestSample

fun test() {
    val a = TestSample
}
/* IGNORE_FIR */