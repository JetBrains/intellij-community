// FIR_IDENTICAL
// FIR_COMPARISON

class A {
    val `with space` = 1
}

fun test(a: A) {
    a.`with<caret>
}

// EXIST: {"lookupString":"with space", "typeText":"Int", "itemText":"with space"}
// NOTHING_ELSE