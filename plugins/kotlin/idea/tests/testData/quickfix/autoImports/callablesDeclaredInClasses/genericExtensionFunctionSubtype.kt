// "Import extension function 'bar'" "true"
package p

open class A
open class B : A()

fun B.usage() {
    <caret>bar()
}

open class C {
    fun <T : A> T.bar() {}
}

object CObject : C()
