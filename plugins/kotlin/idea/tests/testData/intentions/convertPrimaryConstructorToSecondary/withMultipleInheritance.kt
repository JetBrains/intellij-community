interface A {
    val s: String
}

interface B {
    val x: Int
}

abstract class C(open val d: Double)

class D(<caret>val y: Int, final override val d: Double) :  A, C(d), B {
    final override val s = "$y -> $d"

    final override val x = y * y
}