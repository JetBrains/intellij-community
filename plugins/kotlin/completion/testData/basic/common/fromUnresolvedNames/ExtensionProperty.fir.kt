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


// EXIST: { lookupString: "foo1", itemText: "foo1" }
// EXIST: { lookupString: "foo2", itemText: "foo2" }
// ABSENT: foo3
// ABSENT: bar
