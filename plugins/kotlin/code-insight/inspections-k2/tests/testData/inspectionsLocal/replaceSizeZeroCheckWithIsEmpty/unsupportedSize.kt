// PROBLEM: none
// WITH_STDLIB

class List {
    val size = 0
}

fun test() {
    List().size<caret> == 0
}