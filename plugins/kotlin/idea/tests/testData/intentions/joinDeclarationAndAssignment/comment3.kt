class A {
    // comment 1
    // comment 2
    <caret>val prop: Int // comment 3

    init {
        // comment 4
        // comment 5
        prop = 42 // comment 6
        // comment 7
        foo()
    }
}

fun foo() {}
