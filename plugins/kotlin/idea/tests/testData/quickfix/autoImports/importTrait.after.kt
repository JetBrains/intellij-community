import importTrait.data.TestTrait

// "Import" "true"
// ERROR: Unresolved reference: TestTrait

fun test() {
    val a = <caret>TestTrait
}
