// PROBLEM: none
// WITH_STDLIB

import C.G

fun test() {
    <caret>B.G.let { it }
}

class B {
    object G
}

class C {
    object G
}
