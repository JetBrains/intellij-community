// "Import" "false"
// ACTION: Create function 'privateFun'
// ACTION: Do not show return expression hints
// ACTION: Rename reference
// ERROR: Unresolved reference: privateFun

fun test() {
    <caret>privateFun()
}