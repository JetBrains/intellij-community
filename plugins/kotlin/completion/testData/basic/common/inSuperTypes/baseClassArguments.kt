// IGNORE_K2
open class Base(value: Any) {
    class Nested
}

class A : Base(<caret>) {
    fun member() {}
    val prop: Int = 10
}

// EXIST: Nested
// ABSENT: member
// ABSENT: prop
// ABSENT: this
