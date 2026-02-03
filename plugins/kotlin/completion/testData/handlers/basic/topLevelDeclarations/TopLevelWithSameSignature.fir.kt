// FIR_COMPARISON
package pack

fun xxx() {}

class A {
    fun xxx() {}

    fun test() {
        xx<caret>
    }
}

// ELEMENT: xxx
// TAIL_TEXT: "() (pack)"
// K1: only member function is suggested
// K2: both top-level function and member function are suggested, top-level function is rendered with fully-qualified name