// FIR_COMPARISON
// RUN_HIGHLIGHTING_BEFORE

fun test1() {
    "".foo1
    bar
}

fun String.test2() {
    foo2
}

fun Int.test3() {
    foo3
}

val String.<caret>


// ABSENT: foo1
// ABSENT: foo2
// ABSENT: foo3
// ABSENT: bar
