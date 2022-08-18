// PROBLEM: none
// WITH_STDLIB

fun test() {
    if (true) println("hello") else<caret>;
    println("hi")
}