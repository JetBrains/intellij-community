// "Import" "false"
// ERROR: Unresolved reference: ext
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: UNRESOLVED_REFERENCE
package p

open class A {
    fun Int.ext(other: Int) {}
}

object AObject : A()

fun usage() {
    10 <caret>ext 20
}
