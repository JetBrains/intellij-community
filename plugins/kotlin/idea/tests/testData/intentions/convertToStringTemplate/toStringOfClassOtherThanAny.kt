class B {
    override fun toString() = "B"
}

class C: B {
    fun toString(str: String) = "C: $str"
}

fun test(a: Double, b: B, c: C): String {
    return "a:" <caret>+ a.toString() + ", b:" + b.toString() + "_ c:" + c.toString("")
}
