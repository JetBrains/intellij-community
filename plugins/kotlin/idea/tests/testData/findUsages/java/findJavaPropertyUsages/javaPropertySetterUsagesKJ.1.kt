open class A {
    open var p: Int = 1
}

class AA : A() {
    override var p: Int = 1
}

class B : J() {
    override var p: Int = 1
}

fun test() {
    val t = A().p
    A().p = 1

    val t2 = AA().p
    AA().p = 1

    val t3 = J().p
    J().p = 1

    val t4 = B().p
    B().p = 1
}