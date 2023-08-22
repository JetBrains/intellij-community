// FIR_COMPARISON
// FIR_IDENTICAL
package ppp

val xxx: Int.() -> Unit
    get() = {}

fun Int.test() {
    this.xx<caret>
}

// ELEMENT: xxx