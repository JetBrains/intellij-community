interface MyInterface {
    class Nested
}

class A(base: MyInterface) : MyInterface by <caret> {
    fun member() {}
    val prop: Int = 10
}

// EXIST: base
// ABSENT: member
// ABSENT: prop
// ABSENT: this
// ABSENT: Nested
