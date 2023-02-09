// FIR_IDENTICAL
// FIR_COMPARISON

class A {
    val `with space` = 1
}

fun test(a: A) {
    a.`with<caret>
}

// ELEMENT: "with space"