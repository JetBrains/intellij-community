// PROBLEM: none
// WITH_STDLIB

fun test() {
    42.<caret>also({ println(it) })
}
