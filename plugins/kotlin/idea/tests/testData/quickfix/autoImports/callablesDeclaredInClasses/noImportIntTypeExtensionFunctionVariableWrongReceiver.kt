// "Import" "false"
// ACTION: Convert to also
// ACTION: Convert to apply
// ACTION: Convert to run
// ACTION: Convert to with
// ACTION: Do not show return expression hints
// ACTION: Rename reference
// ERROR: Unresolved reference: ext

package p

open class A {
    fun Int.ext() {}
}

object AObject : A()

fun usage() {
    val foo = true
    // should not be importable, foo type is not Int
    foo.<caret>ext()
}
