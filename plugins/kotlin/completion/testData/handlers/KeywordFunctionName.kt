// FIR_COMPARISON
// FIR_IDENTICAL
package test

fun `fun`(){}

fun foo() {
    <caret>
}

// ELEMENT: fun
// TAIL_TEXT: "() (test)"