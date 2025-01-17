// "Import" "false"
// ERROR: Unresolved reference: ext
package p

open class A {
    fun Int.ext(other: Int) {}
}

object AObject : A()

fun usage() {
    10 <caret>ext 20
}
