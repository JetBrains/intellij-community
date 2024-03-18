class A {
    <caret>val prop: Int

    init {
        // comment 1
        prop = 42 // comment 2
        // comment 3
        foo()
    }
}

fun foo() {}
