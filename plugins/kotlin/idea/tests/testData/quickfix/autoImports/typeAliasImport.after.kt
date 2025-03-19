import dependency.TestAlias

// "Import type alias 'TestAlias'" "true"
// ERROR: Unresolved reference: TestAlias

fun test() {
    val a = <caret>TestAlias
}
