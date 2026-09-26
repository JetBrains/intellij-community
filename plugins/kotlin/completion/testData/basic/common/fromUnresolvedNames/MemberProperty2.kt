// FIR_COMPARISON
// RUN_HIGHLIGHTING_BEFORE

class A {
    val <caret>

    fun test1() {
        A().foo1
        "".foo2
        bar()
    }

    fun A.test2() {
        foo3
    }
}

// EXIST: foo3
// EXIST: bar
// ABSENT: foo1
// ABSENT: foo2
