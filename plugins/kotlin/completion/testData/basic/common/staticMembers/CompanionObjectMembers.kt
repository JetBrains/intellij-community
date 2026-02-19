open class B {
    fun baz() {}
}
class A {
    companion object : B() {
        fun foo() {}
    }
}
fun test() {
    val f = A.<caret>
}

// EXIST: { itemText: "foo", attributes: "bold" }
// EXIST: { itemText: "baz", attributes: "" }