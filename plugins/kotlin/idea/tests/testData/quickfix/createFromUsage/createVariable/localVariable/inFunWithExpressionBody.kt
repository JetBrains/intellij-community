// "Create local variable 'foo'" "true"
// ACTION: Convert to block body
// ACTION: Create local variable 'foo'
// ACTION: Create parameter 'foo'
// ACTION: Create property 'foo'
// ACTION: Rename reference

fun test(): Int = <caret>foo