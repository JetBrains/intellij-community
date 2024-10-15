// FIR_IDENTICAL
// FIR_COMPARISON
class C {
    val `1_property` = 1
}

fun main() {
    C().<caret>
}

// ELEMENT: 1_property