fun topLevel() = 1

class A(val prop: Int, arg: Int) {
    val another = 1
    constructor(abc: Int): this(1, <caret>) {
        val local = 1
    }

    fun foo() = 1
}

// EXIST: abc, topLevel, prop, another, foo
// ABSENT: { itemText: "arg" }
// EXIST: { itemText: "arg =" }
// ABSENT: local
