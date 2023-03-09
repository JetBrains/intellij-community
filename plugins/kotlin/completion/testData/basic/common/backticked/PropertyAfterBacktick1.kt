// FIR_IDENTICAL
// FIR_COMPARISON

class A {
    val `with space` = 1
}

fun test(a: A) {
    a.`<caret>
}

// EXIST: {"lookupString":"with space", "typeText":"Int", "itemText":"with space"}
