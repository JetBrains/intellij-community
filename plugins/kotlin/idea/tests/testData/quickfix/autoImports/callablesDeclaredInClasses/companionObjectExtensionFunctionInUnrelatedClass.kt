// "Import" "true"
package p

open class A {
    companion object
}

open class B {
    fun A.Companion.baz() {}
}

object BObject : B()

fun usage() {
    A.<caret>baz()
}
