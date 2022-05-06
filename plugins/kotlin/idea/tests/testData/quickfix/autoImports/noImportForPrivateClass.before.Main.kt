// "Import" "false"
// ACTION: Create local variable 'PrivateClass'
// ACTION: Create object 'PrivateClass'
// ACTION: Create parameter 'PrivateClass'
// ACTION: Create property 'PrivateClass'
// ACTION: Do not show return expression hints
// ACTION: Rename reference
// ERROR: Unresolved reference: PrivateClass

fun test() {
    <caret>PrivateClass
}