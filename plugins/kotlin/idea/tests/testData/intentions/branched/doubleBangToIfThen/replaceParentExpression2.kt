// WITH_STDLIB
// AFTER-WARNING: Parameter 'args' is never used
fun foo(): String? {
    return "foo"
}

class A {
    val g = 44
    fun f(): Int {
        return 42
    }
}

fun main(args: Array<String>) {
    val a: A? = A()
    a<caret>!!.g
}
