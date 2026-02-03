// IGNORE_K1
open class A {
    val a: Int = 1
}

class B : A() {
    val b: Int = 2
}

fun main(args: Array<String>) {
    var a = A()
    a as B
    a.b
    a = A()
    a.<caret>
}

// EXIST: a
// ABSENT: b