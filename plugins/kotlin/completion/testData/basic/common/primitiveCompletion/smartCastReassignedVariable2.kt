// IGNORE_K1
open class A {
    val a: Int = 1
}

class B : A() {
    val b: Int = 2
}

class C : A() {
    val c: Int = 3
}

fun main(args: Array<String>) {
    var a = A()
    a as B
    a.b
    a = C()
    a.<caret>
}

// EXIST: a
// EXIST: c
// ABSENT: b