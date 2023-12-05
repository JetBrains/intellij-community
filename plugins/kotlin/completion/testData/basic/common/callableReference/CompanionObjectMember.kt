// FIR_COMPARISON
open class B {
    fun baz() {}
}
class A {
    companion object : B() {
        fun foo() {}
    }
}
fun test() {
    val f = A::<caret>
}

// EXIST: { itemText: "foo", attributes: "grayed" }
// EXIST: { itemText: "baz", attributes: "grayed" }
/* See KT-54316 */