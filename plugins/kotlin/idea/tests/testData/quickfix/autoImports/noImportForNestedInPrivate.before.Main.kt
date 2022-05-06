// "Import" "false"
// ACTION: Create local variable 'Nested'
// ACTION: Create object 'Nested'
// ACTION: Create parameter 'Nested'
// ACTION: Create property 'Nested'
// ACTION: Do not show return expression hints
// ACTION: Rename reference
// ERROR: Unresolved reference: Nested

fun test() {
    <caret>Nested
}
