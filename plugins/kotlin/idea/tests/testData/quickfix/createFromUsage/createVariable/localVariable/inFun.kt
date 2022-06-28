// "Create local variable 'foo'" "true"
// ACTION: Create local variable 'foo'
// ACTION: Create parameter 'foo'
// ACTION: Create property 'foo'
// ACTION: Do not show return expression hints
// ACTION: Rename reference

fun test(): Int {
    return <caret>foo
}