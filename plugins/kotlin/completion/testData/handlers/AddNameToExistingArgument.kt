// FIR_COMPARISON
// FIR_IDENTICAL
fun foo(firstParam: Int, secondParam: Int) {

}

fun main() {
    foo(first<caret>1, 2)
}

// ELEMENT: firstParam =