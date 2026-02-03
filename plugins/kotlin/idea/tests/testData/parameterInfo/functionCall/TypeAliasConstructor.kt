class A(val a: Int) {
    constructor(val b: String) : this(b.toInt())
}

typealias TA = A

fun usage() {
    val x = TA(<caret>)
}

