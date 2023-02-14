// WITH_STDLIB

typealias MyArray = Array<Int>

fun test(a: MyArray) {
    a.<caret>size >= 1
}