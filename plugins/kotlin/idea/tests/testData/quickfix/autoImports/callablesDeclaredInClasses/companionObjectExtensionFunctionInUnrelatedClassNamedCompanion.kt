// "Import" "true"
package p

open class A {
    companion object Foo {
        fun foo() {}
    }
}

open class B {
    fun A.Foo.baz() {}
}

object BObject : B()

fun usage() {
    A.<caret>baz()
}
